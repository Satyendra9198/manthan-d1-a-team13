package com.returnright.seed;

import com.returnright.audit.AuditService;
import com.returnright.auth.User;
import com.returnright.core.Actor;
import com.returnright.core.AppClock;
import com.returnright.core.RrProperties;
import com.returnright.core.enums.CancelReason;
import com.returnright.core.enums.Decision;
import com.returnright.core.enums.Grade;
import com.returnright.core.enums.OrderStatus;
import com.returnright.core.enums.PaymentMethod;
import com.returnright.core.enums.RefundMethod;
import com.returnright.core.enums.RejectReason;
import com.returnright.core.enums.ReturnReason;
import com.returnright.core.enums.ReturnStatus;
import com.returnright.core.enums.Role;
import com.returnright.finance.FinanceService;
import com.returnright.inspection.InspectionService;
import com.returnright.labels.LabelService;
import com.returnright.ledger.LedgerService;
import com.returnright.orders.Category;
import com.returnright.orders.Customer;
import com.returnright.orders.Order;
import com.returnright.orders.OrderItem;
import com.returnright.orders.Product;
import com.returnright.orders.Repositories.CategoryRepository;
import com.returnright.orders.Repositories.CustomerRepository;
import com.returnright.orders.Repositories.OrderItemRepository;
import com.returnright.orders.Repositories.OrderRepository;
import com.returnright.orders.Repositories.ProductRepository;
import com.returnright.returns.ReturnItem;
import com.returnright.returns.ReturnRequest;
import com.returnright.returns.ReturnService;
import com.returnright.returns.ReturnService.SubmitUnit;
import com.returnright.returns.ReturnService.UnitView;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import net.datafaker.Faker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClient;

/**
 * Deterministic synthetic data (Part 2 §16). Reference data, customers and orders are inserted directly; every return
 * is created and progressed THROUGH THE SERVICES (state machine, real gateway) with a pinned clock, so seed data can
 * never violate a business rule (F-13). Run: --spring.profiles.active=seed --seed.as-of=YYYY-MM-DD --seed.reset=true
 */
@Component
@Profile("seed")
public class Seeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(Seeder.class);
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final Actor SEED = Actor.system("seed");

    private final CategoryRepository categories;
    private final ProductRepository products;
    private final CustomerRepository customers;
    private final OrderRepository orders;
    private final OrderItemRepository orderItems;
    private final User.UserRepository users;
    private final ReturnService returnService;
    private final InspectionService inspection;
    private final FinanceService finance;
    private final LabelService labels;
    private final LedgerService ledger;
    private final AuditService audit;
    private final PasswordEncoder encoder;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final AppClock clock;
    private final RrProperties props;
    private final com.returnright.core.KvStore kv;

    @Value("${seed.random-seed}") long seed;
    @Value("${seed.as-of:}") String asOfArg;
    @Value("${seed.reset:false}") boolean reset;
    @Value("${seed.customers:500}") int customerCount;
    @Value("${seed.orders:2000}") int orderCount;
    @Value("${seed.return-rate:0.12}") double returnRate;
    @Value("${seed.staff-password}") String staffPassword;
    @Value("${GATEWAY_ADMIN_TOKEN:dev-admin-token}") String gatewayAdminToken;

    private Random rnd;
    private Instant anchor;
    private final Map<String, Product> productBySku = new HashMap<>();
    private final Map<String, Long> staff = new HashMap<>();
    private int orderSeq = 0;
    private final Map<String, Integer> stats = new LinkedHashMap<>();

    public Seeder(CategoryRepository categories, ProductRepository products, CustomerRepository customers,
                  OrderRepository orders, OrderItemRepository orderItems, User.UserRepository users,
                  ReturnService returnService, InspectionService inspection, FinanceService finance,
                  LabelService labels, LedgerService ledger, AuditService audit, PasswordEncoder encoder,
                  JdbcTemplate jdbc, TransactionTemplate tx, AppClock clock, RrProperties props,
                  com.returnright.core.KvStore kv) {
        this.categories = categories;
        this.products = products;
        this.customers = customers;
        this.orders = orders;
        this.orderItems = orderItems;
        this.users = users;
        this.returnService = returnService;
        this.inspection = inspection;
        this.finance = finance;
        this.labels = labels;
        this.ledger = ledger;
        this.audit = audit;
        this.encoder = encoder;
        this.jdbc = jdbc;
        this.tx = tx;
        this.clock = clock;
        this.props = props;
        this.kv = kv;
    }

    @Override
    public void run(ApplicationArguments args) {
        LocalDate asOf = asOfArg == null || asOfArg.isBlank() ? LocalDate.now(IST) : LocalDate.parse(asOfArg);
        anchor = asOf.atTime(LocalTime.NOON).atZone(IST).toInstant();
        rnd = new Random(seed);
        log.info("Seeding ReturnRight as of {} (anchor {}), seed {}", asOf, anchor, seed);

        Long existing = jdbc.queryForObject("SELECT count(*) FROM orders", Long.class);
        if (existing != null && existing > 0) {
            if (!reset) {
                throw new IllegalStateException("Database already has data. Re-run with --seed.reset=true to wipe it.");
            }
            truncate();
        }
        gatewayAdmin("/admin/reset-data");
        gatewayAdmin("/admin/faults/profile/healthy");

        clock.pin(anchor.minus(Duration.ofDays(200)));
        tx.executeWithoutResult(s -> referenceData());
        List<Customer> people = tx.execute(s -> customers());
        List<Order> bulk = tx.execute(s -> bulkOrders(people));
        Map<String, Order> golden = tx.execute(s -> goldenOrders());

        historicalReturns(bulk);
        goldenReturns(golden);
        clock.unpin();
        report(asOf);
    }

    // ------------------------------------------------------------------------------------------------
    private void truncate() {
        log.warn("--seed.reset: truncating all ReturnRight data");
        jdbc.execute("""
                TRUNCATE outbox_events, idempotency_keys, notifications, audit_log, ledger_entries, ledger_transactions,
                  refund_attempts, refund_items, refunds, finance_reviews, return_labels, return_items, returns,
                  order_items, orders, products, categories, customers, users RESTART IDENTITY CASCADE""");
        jdbc.execute("ALTER SEQUENCE rma_seq RESTART WITH 1000");
        // A reset is a fresh demo: clear rate limits, failed-login counters and lockouts too (revoked tokens stay
        // revoked). Otherwise re-seeding within the hour leaves golden orders locked by the 10/hour lookup limit.
        long cleared = kv.deleteByPrefix("rl:") + kv.deleteByPrefix("fail:") + kv.deleteByPrefix("lock:");
        log.warn("--seed.reset: cleared {} rate-limit/lockout keys", cleared);
    }

    private void gatewayAdmin(String path) {
        try {
            RestClient.create(props.gateway().url()).post().uri(path).header("X-Admin-Token", gatewayAdminToken)
                    .retrieve().toBodilessEntity();
        } catch (RuntimeException e) {
            log.warn("gateway admin {} failed ({}). Refunds will be reconciled later.", path, e.getMessage());
        }
    }

    private void referenceData() {
        Map<String, Category> cats = new HashMap<>();
        for (Catalogue.Cat c : Catalogue.CATEGORIES) {
            cats.put(c.code(), categories.save(new Category(c.id(), c.code(), c.name(), c.returnable(), c.windowDays())));
        }
        for (Catalogue.Item i : Catalogue.PRODUCTS) {
            Product p = products.save(new Product(i.sku(), i.name(), i.brand(), cats.get(i.category()), i.rupees() * 100));
            productBySku.put(i.sku(), p);
        }
        Instant now = clock.instant();
        String[][] team = {
                {"warehouse1", "Arjun Patil", "WAREHOUSE_AGENT"}, {"warehouse2", "Meera Iyer", "WAREHOUSE_AGENT"},
                {"finance1", "Kavita Rao", "FINANCE_REVIEWER"}, {"finance2", "Sandeep Joshi", "FINANCE_REVIEWER"},
                {"manager1", "Farhan Qureshi", "OPS_MANAGER"}, {"admin", "Platform Admin", "ADMIN"}};
        for (String[] t : team) {
            User u = users.save(new User(t[0], t[1], t[0] + "@returnright.dev", encoder.encode(staffPassword),
                    Role.valueOf(t[2]), now));
            staff.put(t[0], u.getId());
        }
    }

    private List<Customer> customers() {
        Faker faker = new Faker(Locale.of("en", "IND"), new Random(seed));
        List<Customer> out = new ArrayList<>();
        out.add(customer("edge.customer@example.com", "Asha Verma", 0));
        out.add(customer("flag.near@example.com", "Rohan Mehta", 1));
        out.add(customer("flag.cancelled@example.com", "Priya Nair", 2));
        out.add(customer("flag.already@example.com", "Vikram Singh", 3));
        out.add(customer("demo.customer@example.com", "Neha Kapoor", 4));
        for (int i = 1; i <= customerCount; i++) {
            String first = faker.name().firstName();
            String last = faker.name().lastName();
            String email = (first + "." + last).toLowerCase(Locale.ROOT).replaceAll("[^a-z.]", "") + "." + i + "@example.com";
            out.add(customer(email, first + " " + last, i + 10));
        }
        return out;
    }

    private Customer customer(String email, String name, int i) {
        String[] city = Catalogue.CITIES.get(Math.floorMod(i * 7 + 3, Catalogue.CITIES.size()));
        String phone = "+91 9" + String.format("%09d", Math.floorMod((long) i * 7_919_311L + 104_729L, 1_000_000_000L));
        String line1 = (10 + Math.floorMod(i * 37, 890)) + ", " + new String[] {"MG Road", "Station Road",
                "Ring Road", "Civil Lines", "Nehru Nagar", "Gandhi Path", "Lake View Colony", "Sector 4"}[Math.floorMod(i, 8)];
        String pin = city[2] + String.format("%03d", Math.floorMod(i * 13, 999) + 1);
        return customers.save(new Customer(email, name, phone, line1, city[0], city[1], pin,
                anchor.minus(Duration.ofDays(400 - Math.floorMod(i, 200)))));
    }

    private record Line(String sku, int qty, Long unitPricePaise) {}

    private Order order(Customer c, String number, OrderStatus status, PaymentMethod pm, String payRef,
                        Instant placed, Instant delivered, List<Line> lines) {
        long total = 0;
        for (Line l : lines) {
            long unit = l.unitPricePaise() != null ? l.unitPricePaise() : productBySku.get(l.sku()).getPricePaise();
            total += unit * l.qty();
        }
        Order o = orders.save(new Order(number, c, status, pm, payRef, placed, delivered, total));
        int n = 1;
        for (Line l : lines) {
            long unit = l.unitPricePaise() != null ? l.unitPricePaise() : productBySku.get(l.sku()).getPricePaise();
            OrderItem oi = orderItems.save(new OrderItem(o, n++, productBySku.get(l.sku()), l.qty(), unit));
            o.getItems().add(oi);
        }
        return o;
    }

    private List<Order> bulkOrders(List<Customer> people) {
        List<Customer> regular = people.subList(5, people.size());
        List<Catalogue.Item> catalogue = Catalogue.PRODUCTS;
        List<Order> out = new ArrayList<>();
        PaymentMethod[] pms = PaymentMethod.values();
        for (int i = 0; i < orderCount; i++) {
            Customer c = regular.get(Math.min(regular.size() - 1, (int) (regular.size() * Math.pow(rnd.nextDouble(), 1.6))));
            Instant placed = anchor.minus(Duration.ofMinutes(rnd.nextLong(2 * 24 * 60, 180L * 24 * 60)));
            double s = rnd.nextDouble();
            OrderStatus status;
            Instant delivered = null;
            if (s < 0.90) {
                delivered = placed.plus(Duration.ofHours(rnd.nextLong(48, 7 * 24)));
                status = delivered.isAfter(anchor) ? OrderStatus.SHIPPED : OrderStatus.DELIVERED;
                if (status == OrderStatus.SHIPPED) {
                    delivered = null;
                }
            } else if (s < 0.94) {
                status = OrderStatus.SHIPPED;
            } else if (s < 0.97) {
                status = OrderStatus.PLACED;
            } else {
                status = OrderStatus.CANCELLED;
            }
            int nLines = 1 + (rnd.nextDouble() < 0.55 ? 0 : rnd.nextInt(3));
            List<Line> lines = new ArrayList<>();
            List<Integer> picked = new ArrayList<>();
            while (lines.size() < nLines) {
                int idx = rnd.nextInt(catalogue.size());
                if (picked.contains(idx)) {
                    continue;
                }
                picked.add(idx);
                double q = rnd.nextDouble();
                int qty = q < 0.75 ? 1 : q < 0.93 ? 2 : 3;
                long price = catalogue.get(idx).rupees() * 100;
                Long unit = rnd.nextDouble() < 0.2 ? (price * 90 / 100) / 100 * 100 : null; // 10% off, whole rupees
                lines.add(new Line(catalogue.get(idx).sku(), qty, unit));
            }
            PaymentMethod pm = pms[rnd.nextDouble() < 0.4 ? 0 : rnd.nextDouble() < 0.67 ? 1 : rnd.nextBoolean() ? 2 : 3];
            String number = "RR-2026-%06d".formatted(++orderSeq);
            out.add(order(c, number, status, pm, "pay_%s_%06d".formatted(pm.name(), orderSeq), placed, delivered, lines));
        }
        return out;
    }

    /** Golden edge cases (Part 2 §16.3); all relative to the as-of anchor. */
    private Map<String, Order> goldenOrders() {
        Customer edge = customers.findByEmailIgnoreCase("edge.customer@example.com").orElseThrow();
        Customer near = customers.findByEmailIgnoreCase("flag.near@example.com").orElseThrow();
        Customer canc = customers.findByEmailIgnoreCase("flag.cancelled@example.com").orElseThrow();
        Customer already = customers.findByEmailIgnoreCase("flag.already@example.com").orElseThrow();
        Customer demo = customers.findByEmailIgnoreCase("demo.customer@example.com").orElseThrow();
        Map<String, Order> g = new LinkedHashMap<>();
        LocalDate today = anchor.atZone(IST).toLocalDate();
        java.util.function.BiFunction<Integer, LocalTime, Instant> deliveredAt =
                (daysAgo, t) -> today.minusDays(daysAgo).atTime(t).atZone(IST).toInstant();
        LocalTime noon = LocalTime.NOON;
        record G(String number, Customer c, int daysAgo, LocalTime at, OrderStatus st, String payRef, List<Line> lines) {}
        List<G> list = List.of(
                new G("RR-EDGE-001", edge, 30, noon, OrderStatus.DELIVERED, null, List.of(new Line("AP-JNS-SLM", 1, null))),
                new G("RR-EDGE-002", edge, 31, noon, OrderStatus.DELIVERED, null, List.of(new Line("AP-KRT-CTN", 1, null))),
                new G("RR-EDGE-003", edge, 10, noon, OrderStatus.DELIVERED, null, List.of(new Line("EL-EAR-PR", 1, null))),
                new G("RR-EDGE-004", edge, 11, noon, OrderStatus.DELIVERED, null, List.of(new Line("EL-SPK-BT", 1, null))),
                new G("RR-EDGE-005", edge, 30, LocalTime.of(0, 10), OrderStatus.DELIVERED, null, List.of(new Line("AP-SHR-LIN", 1, null))),
                new G("RR-EDGE-006", edge, 2, noon, OrderStatus.DELIVERED, null, List.of(new Line("IW-BRF-PK5", 1, null))),
                new G("RR-EDGE-007", edge, 1, noon, OrderStatus.DELIVERED, null, List.of(new Line("PR-DRY-1KG", 1, null))),
                new G("RR-EDGE-008", edge, 3, noon, OrderStatus.DELIVERED, null,
                        List.of(new Line("AP-JNS-SLM", 1, null), new Line("IW-VST-PK3", 1, null))),
                new G("RR-EDGE-009", edge, 5, noon, OrderStatus.DELIVERED, null, List.of(new Line("EL-LAP-14", 1, null))),
                new G("RR-EDGE-010", edge, 4, noon, OrderStatus.DELIVERED, null, List.of(new Line("FW-RUN-PRO", 1, null))),
                new G("RR-EDGE-011", edge, 4, noon, OrderStatus.DELIVERED, null,
                        List.of(new Line("AP-SAR-SLK", 1, 600_000L), new Line("AP-BLZ-WOL", 1, 600_000L))),
                new G("RR-EDGE-012", edge, 4, noon, OrderStatus.DELIVERED, null, List.of(new Line("FW-RUN-PRO", 1, 1_000_000L))),
                new G("RR-EDGE-013", edge, 8, noon, OrderStatus.DELIVERED, null, List.of(new Line("AP-TSH-PK3", 3, null))),
                new G("RR-EDGE-014", edge, -2, noon, OrderStatus.SHIPPED, null, List.of(new Line("HK-LMP-LED", 1, null))),
                new G("RR-EDGE-015", edge, 6, noon, OrderStatus.CANCELLED, null, List.of(new Line("HK-BTL-STL", 1, null))),
                new G("RR-EDGE-016", edge, 3, noon, OrderStatus.DELIVERED, "pay_CARD_EDGE016-DECLINE",
                        List.of(new Line("EL-EAR-PR", 1, null))),
                new G("RR-EDGE-017", edge, 0, LocalTime.of(9, 0), OrderStatus.DELIVERED, null, List.of(new Line("AP-HDY-FLC", 1, null))),
                new G("RR-FLAG-101", near, 25, noon, OrderStatus.DELIVERED, null, List.of(new Line("AP-TRK-JOG", 1, null))),
                new G("RR-FLAG-102", near, 20, noon, OrderStatus.DELIVERED, null, List.of(new Line("AP-DRS-MID", 1, null))),
                new G("RR-FLAG-103", near, 14, noon, OrderStatus.DELIVERED, null, List.of(new Line("FW-SND-LTH", 1, null))),
                new G("RR-FLAG-104", near, 5, noon, OrderStatus.DELIVERED, null, List.of(new Line("HK-BED-KNG", 1, null))),
                new G("RR-FLAG-201", canc, 22, noon, OrderStatus.DELIVERED, null, List.of(new Line("AP-HDY-FLC", 1, null))),
                new G("RR-FLAG-202", canc, 16, noon, OrderStatus.DELIVERED, null, List.of(new Line("FW-SNK-WHT", 1, null))),
                new G("RR-FLAG-203", canc, 9, noon, OrderStatus.DELIVERED, null, List.of(new Line("HK-SOF-CVR", 1, null))),
                new G("RR-FLAG-204", canc, 4, noon, OrderStatus.DELIVERED, null, List.of(new Line("AP-KRT-CTN", 1, null))),
                new G("RR-FLAG-301", already, 3, noon, OrderStatus.DELIVERED, null, List.of(new Line("FW-SLP-CMF", 1, 79_900L))),
                new G("RR-DEMO-101", demo, 8, noon, OrderStatus.DELIVERED, null, List.of(new Line("EL-TAB-11", 1, null))),
                new G("RR-DEMO-102", demo, 6, noon, OrderStatus.DELIVERED, null,
                        List.of(new Line("FW-BOT-CHL", 1, null), new Line("AP-JKT-DNM", 1, null))),
                new G("RR-DEMO-103", demo, 7, noon, OrderStatus.DELIVERED, "pay_UPI_DEMO103-DECLINE",
                        List.of(new Line("HK-AFR-4L", 1, null))),
                new G("RR-DEMO-104", demo, 6, noon, OrderStatus.DELIVERED, null, List.of(new Line("HK-VAC-RBT", 1, null))),
                new G("RR-DEMO-105", demo, 2, noon, OrderStatus.DELIVERED, null, List.of(new Line("AP-SHR-LIN", 2, null))));
        for (G x : list) {
            Instant delivered = x.st() == OrderStatus.DELIVERED ? deliveredAt.apply(x.daysAgo(), x.at()) : null;
            Instant placed = (delivered != null ? delivered : deliveredAt.apply(Math.max(x.daysAgo(), 0), noon))
                    .minus(Duration.ofDays(3));
            PaymentMethod pm = x.number().hashCode() % 2 == 0 ? PaymentMethod.UPI : PaymentMethod.CARD;
            String ref = x.payRef() != null ? x.payRef() : "pay_%s_%s".formatted(pm.name(), x.number().replace("-", ""));
            g.put(x.number(), order(x.c(), x.number(), x.st(), pm, ref, placed, delivered, x.lines()));
        }
        // [A-15] flag.already starts flagged (documented demo state)
        already.flag(anchor.minus(Duration.ofDays(2)), "Seeded: prior return abuse");
        audit.record(SEED, "customer", already.getId().toString(), "CUSTOMER_FLAGGED", "UNFLAGGED", "FLAGGED", null,
                null, Map.of("reason", "seeded demo state"));
        return g;
    }

    // ------------------------------------------------------------------------------------------------
    // Returns through the services
    // ------------------------------------------------------------------------------------------------
    private void historicalReturns(List<Order> bulk) {
        List<Order> delivered = new ArrayList<>(bulk.stream().filter(o -> o.getStatus() == OrderStatus.DELIVERED).toList());
        Collections.shuffle(delivered, rnd);
        int target = (int) Math.round(delivered.size() * returnRate);
        int created = 0;
        for (Order o : delivered) {
            if (created >= target) {
                break;
            }
            Instant t0 = o.getDeliveredAt().plus(Duration.ofHours(rnd.nextLong(2, 9 * 24)));
            if (t0.isAfter(anchor.minus(Duration.ofHours(2)))) {
                continue;
            }
            if (lifecycle(o.getOrderNumber(), o.getCustomer().getId(), t0, null, null) != null) {
                created++;
            }
        }
        stats.put("historical_returns", created);
    }

    /**
     * Creates a return at t0 and progresses it as far as time allows (each step only if its time is before the anchor).
     * stopAt forces a final state for golden/demo scenarios; grades optionally fixes inspection outcomes.
     */
    private UUID lifecycle(String orderNumber, long customerId, Instant t0, ReturnStatus stopAt, List<Grade> grades) {
        UUID submitted = null;
        try {
            clock.pin(t0);
            Order o = orders.findByOrderNumber(orderNumber).orElseThrow();
            List<UnitView> eligible = tx.execute(s -> returnService.units(o, t0).stream()
                    .filter(u -> u.eligibility().eligible()).toList());
            if (eligible == null || eligible.isEmpty()) {
                return null;
            }
            int n = Math.min(eligible.size(), grades != null ? grades.size() : 1 + (rnd.nextDouble() < 0.25 ? 1 : 0));
            List<SubmitUnit> units = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                UnitView u = eligible.get(i);
                units.add(new SubmitUnit(u.orderItem().getId(), u.unitIndex(), reason(), null));
            }
            RefundMethod method = rnd.nextDouble() < 0.15 ? RefundMethod.STORE_CREDIT : RefundMethod.ORIGINAL_PAYMENT;
            Actor customer = Actor.customer(customerId, "seed-customer");
            UUID id = returnService.submit(customer, customerId, orderNumber, method, units).getId();
            submitted = id;
            bump("submitted");

            boolean scripted = stopAt != null;
            if (!scripted && rnd.nextDouble() < 0.07) {
                if (step(t0.plus(Duration.ofHours(20)))) {
                    returnService.cancel(customer, id, version(id), CancelReason.CUSTOMER);
                    bump("cancelled");
                }
                return id;
            }
            if (stopAt == ReturnStatus.APPROVED) {
                return id;
            }
            Instant ship = t0.plus(Duration.ofHours(scripted ? 12 : rnd.nextLong(6, 30)));
            if (scripted || rnd.nextDouble() < 0.7) {
                if (!step(ship)) {
                    return id;
                }
                returnService.ship(customer, id, version(id));
            }
            if (stopAt == ReturnStatus.IN_TRANSIT) {
                return id;
            }
            Instant receive = ship.plus(Duration.ofHours(scripted ? 36 : rnd.nextLong(36, 96)));
            if (!step(receive)) {
                return id;
            }
            Actor agent = Actor.staff(staff.get(rnd.nextBoolean() ? "warehouse1" : "warehouse2"), Role.WAREHOUSE_AGENT,
                    "Warehouse");
            String token = labels.current(id).orElseThrow().getQrToken();
            inspection.scan(agent, token);
            if (stopAt == ReturnStatus.RECEIVED) {
                return id;
            }
            Instant inspect = receive.plus(Duration.ofHours(scripted ? 8 : rnd.nextLong(4, 30)));
            if (!step(inspect)) {
                return id;
            }
            List<ReturnItem> items = tx.execute(s -> new ArrayList<>(returnService.get(id).getItems()));
            for (int i = 0; i < items.size(); i++) {
                Grade g = grades != null ? grades.get(i) : grade();
                boolean reject = grades == null && rnd.nextDouble() < 0.08;
                inspection.inspectItem(agent, id, items.get(i).getId(), version(id),
                        reject ? Decision.REJECT : Decision.ACCEPT, reject ? null : g,
                        reject ? (rnd.nextBoolean() ? RejectReason.USED_OR_DAMAGED_BY_CUSTOMER : RejectReason.MISSING_PARTS)
                                : null, null);
            }
            inspection.complete(agent, id, version(id));
            ReturnStatus st = returnService.get(id).getStatus();
            if (st == ReturnStatus.PENDING_FINANCE && stopAt != ReturnStatus.PENDING_FINANCE) {
                if (step(inspect.plus(Duration.ofHours(rnd.nextLong(3, 26))))) {
                    Actor fin = Actor.staff(staff.get(rnd.nextBoolean() ? "finance1" : "finance2"), Role.FINANCE_REVIEWER,
                            "Finance");
                    if (rnd.nextDouble() < 0.9) {
                        finance.approve(fin, id, version(id), "Verified inspection photos");
                    } else {
                        finance.reject(fin, id, version(id), "Item condition inconsistent with claim");
                    }
                }
            }
            bump("final_" + returnService.get(id).getStatus().name());
            return id;
        } catch (RuntimeException e) {
            log.warn("seed return for {} stopped early: {}", orderNumber, e.getMessage());
            bump("stopped_early");
            return submitted;
        }
    }

    private boolean step(Instant at) {
        if (at.isAfter(anchor.minus(Duration.ofMinutes(30)))) {
            return false;
        }
        clock.pin(at);
        return true;
    }

    private int version(UUID id) {
        return returnService.get(id).getVersion();
    }

    private void goldenReturns(Map<String, Order> g) {
        LocalDate today = anchor.atZone(IST).toLocalDate();
        java.util.function.Function<Integer, Instant> daysAgo = d -> today.minusDays(d).atTime(11, 0).atZone(IST).toInstant();
        Customer edge = g.get("RR-EDGE-001").getCustomer();
        // RR-EDGE-013: one of three units already returned and refunded
        lifecycle("RR-EDGE-013", edge.getId(), daysAgo.apply(7), ReturnStatus.REFUNDED, List.of(Grade.A));
        // flag.near: exactly 3 requests in the window -> the next submit flags (A-14)
        long near = g.get("RR-FLAG-101").getCustomer().getId();
        lifecycle("RR-FLAG-101", near, daysAgo.apply(20), ReturnStatus.REFUNDED, List.of(Grade.A));
        lifecycle("RR-FLAG-102", near, daysAgo.apply(15), ReturnStatus.REFUNDED, List.of(Grade.B));
        lifecycle("RR-FLAG-103", near, daysAgo.apply(10), ReturnStatus.IN_TRANSIT, List.of(Grade.A));
        // flag.cancelled: 2 returns + 1 cancelled still count (A-14)
        long canc = g.get("RR-FLAG-201").getCustomer().getId();
        lifecycle("RR-FLAG-201", canc, daysAgo.apply(18), ReturnStatus.REFUNDED, List.of(Grade.A));
        lifecycle("RR-FLAG-202", canc, daysAgo.apply(12), ReturnStatus.REFUNDED, List.of(Grade.A));
        UUID c3 = lifecycle("RR-FLAG-203", canc, daysAgo.apply(6), ReturnStatus.APPROVED, List.of(Grade.A));
        if (c3 != null) {
            clock.pin(daysAgo.apply(5));
            returnService.cancel(Actor.customer(canc, "Priya Nair"), c3, version(c3), CancelReason.CUSTOMER);
        }
        // Demo queues for the persona walkthrough
        long demo = g.get("RR-DEMO-101").getCustomer().getId();
        lifecycle("RR-DEMO-101", demo, daysAgo.apply(7), ReturnStatus.PENDING_FINANCE, List.of(Grade.A)); // > Rs 10k
        lifecycle("RR-DEMO-102", demo, daysAgo.apply(5), ReturnStatus.RECEIVED, List.of(Grade.A, Grade.A)); // to inspect
        lifecycle("RR-DEMO-103", demo, daysAgo.apply(6), ReturnStatus.REFUNDED, List.of(Grade.A)); // gateway declines
        lifecycle("RR-DEMO-104", demo, daysAgo.apply(5), ReturnStatus.PENDING_FINANCE, List.of(Grade.C)); // 24,999 -> 12,500
        lifecycle("RR-DEMO-105", demo, daysAgo.apply(1), ReturnStatus.IN_TRANSIT, List.of(Grade.A, Grade.A));
    }

    private ReturnReason reason() {
        double r = rnd.nextDouble();
        if (r < 0.30) return ReturnReason.SIZE_FIT;
        if (r < 0.45) return ReturnReason.DAMAGED;
        if (r < 0.60) return ReturnReason.DEFECTIVE;
        if (r < 0.75) return ReturnReason.CHANGED_MIND;
        if (r < 0.85) return ReturnReason.NOT_AS_DESCRIBED;
        if (r < 0.93) return ReturnReason.WRONG_ITEM;
        if (r < 0.97) return ReturnReason.LATE_DELIVERY;
        return ReturnReason.OTHER;
    }

    private Grade grade() {
        double r = rnd.nextDouble();
        return r < 0.60 ? Grade.A : r < 0.85 ? Grade.B : Grade.C;
    }

    private void bump(String k) {
        stats.merge(k, 1, Integer::sum);
    }

    private void report(LocalDate asOf) {
        Map<String, Object> counts = jdbc.queryForMap("""
                SELECT (SELECT count(*) FROM customers) customers, (SELECT count(*) FROM products) products,
                       (SELECT count(*) FROM categories) categories, (SELECT count(*) FROM orders) orders,
                       (SELECT count(*) FROM returns) returns, (SELECT count(*) FROM refunds) refunds,
                       (SELECT count(*) FROM customers WHERE is_flagged) flagged,
                       (SELECT count(*) FROM ledger_transactions) ledger_txns""");
        log.info("SEED COMPLETE as of {}: {}", asOf, counts);
        log.info("Lifecycle stats: {}", stats);
        for (LedgerService.Check c : ledger.reconcile(15)) {
            log.info("Reconciliation {}: {} mismatches {}", c.check(), c.mismatches(), c.sampleIds());
        }
        for (String on : List.of("RR-EDGE-001", "RR-EDGE-002", "RR-EDGE-003", "RR-EDGE-004", "RR-EDGE-005",
                "RR-EDGE-006", "RR-EDGE-007", "RR-EDGE-008", "RR-EDGE-013", "RR-EDGE-014", "RR-EDGE-015", "RR-EDGE-017")) {
            tx.executeWithoutResult(s -> {
                Order o = orders.findByOrderNumber(on).orElseThrow();
                List<String> codes = returnService.units(o, anchor).stream()
                        .map(u -> u.unitIndex() + ":" + u.eligibility().reasonCode()).toList();
                log.info("  {} -> {}", on, codes);
            });
        }
    }
}
