-- Business-rule triggers (Part 1 §6.3/§6.4).

-- [BR-6] Only legal return state transitions. Must equal ReturnStateMachine.ALLOWED_PAIRS (TransitionTableParityTest).
CREATE OR REPLACE FUNCTION rr_returns_valid_transition() RETURNS trigger AS $$
BEGIN
  IF (OLD.status::text || '>' || NEW.status::text) NOT IN (
      'REQUESTED>APPROVED','REQUESTED>CANCELLED',
      'APPROVED>IN_TRANSIT','APPROVED>RECEIVED','APPROVED>CANCELLED',
      'IN_TRANSIT>RECEIVED',
      'RECEIVED>INSPECTED',
      'INSPECTED>REJECTED','INSPECTED>PENDING_FINANCE','INSPECTED>REFUND_PENDING',
      'PENDING_FINANCE>REFUND_PENDING','PENDING_FINANCE>REJECTED',
      'REFUND_PENDING>REFUNDED','REFUND_PENDING>REFUND_FAILED',
      'REFUND_FAILED>REFUND_PENDING') THEN
    RAISE EXCEPTION 'illegal return transition % -> %', OLD.status, NEW.status
      USING ERRCODE = 'check_violation', CONSTRAINT = 'trg_returns_valid_transition';
  END IF;
  RETURN NEW;
END $$ LANGUAGE plpgsql;

CREATE TRIGGER trg_returns_valid_transition
  BEFORE UPDATE OF status ON returns
  FOR EACH ROW WHEN (OLD.status IS DISTINCT FROM NEW.status)
  EXECUTE FUNCTION rr_returns_valid_transition();

-- [BR-4] A return that requires finance review cannot reach REFUND_PENDING without an APPROVED review.
CREATE OR REPLACE FUNCTION rr_finance_approval_required() RETURNS trigger AS $$
BEGIN
  IF NEW.status = 'REFUND_PENDING' AND NEW.requires_finance_review AND NOT EXISTS (
      SELECT 1 FROM finance_reviews f WHERE f.return_id = NEW.id AND f.decision = 'APPROVED') THEN
    RAISE EXCEPTION 'finance approval required for return %', NEW.id
      USING ERRCODE = 'check_violation', CONSTRAINT = 'trg_finance_approval_required';
  END IF;
  RETURN NEW;
END $$ LANGUAGE plpgsql;

CREATE TRIGGER trg_finance_approval_required
  BEFORE UPDATE OF status ON returns
  FOR EACH ROW WHEN (NEW.status = 'REFUND_PENDING')
  EXECUTE FUNCTION rr_finance_approval_required();

-- [BR-9] Mirror of the eligibility engine (R2, R4, R7 + quantity + ownership) so seed data cannot cheat.
CREATE OR REPLACE FUNCTION rr_return_item_eligibility() RETURNS trigger AS $$
DECLARE
  v_qty smallint; v_order_status text; v_delivered timestamptz; v_order_customer bigint;
  v_returnable boolean; v_window smallint; v_req timestamptz; v_ret_customer bigint; v_ret_order bigint; v_oi_order bigint;
BEGIN
  SELECT oi.quantity, oi.order_id, o.status::text, o.delivered_at, o.customer_id, c.is_returnable, c.return_window_days
    INTO v_qty, v_oi_order, v_order_status, v_delivered, v_order_customer, v_returnable, v_window
    FROM order_items oi JOIN orders o ON o.id = oi.order_id
    JOIN products p ON p.id = oi.product_id JOIN categories c ON c.id = p.category_id
   WHERE oi.id = NEW.order_item_id;
  SELECT r.created_at, r.customer_id, r.order_id INTO v_req, v_ret_customer, v_ret_order FROM returns r WHERE r.id = NEW.return_id;

  IF NEW.unit_index > v_qty THEN
    RAISE EXCEPTION 'unit_index % exceeds quantity %', NEW.unit_index, v_qty USING ERRCODE = 'check_violation', CONSTRAINT = 'trg_return_item_eligibility';
  END IF;
  IF v_oi_order <> v_ret_order OR v_order_customer <> v_ret_customer THEN
    RAISE EXCEPTION 'order item does not belong to the return order/customer' USING ERRCODE = 'check_violation', CONSTRAINT = 'trg_return_item_eligibility';
  END IF;
  IF v_order_status <> 'DELIVERED' THEN
    RAISE EXCEPTION 'order not delivered' USING ERRCODE = 'check_violation', CONSTRAINT = 'trg_return_item_eligibility';
  END IF;
  IF NOT v_returnable THEN
    RAISE EXCEPTION 'category not returnable' USING ERRCODE = 'check_violation', CONSTRAINT = 'trg_return_item_eligibility';
  END IF;
  -- [A-02] calendar days in IST, day 30 inclusive
  IF (v_req AT TIME ZONE 'Asia/Kolkata')::date - (v_delivered AT TIME ZONE 'Asia/Kolkata')::date > v_window THEN
    RAISE EXCEPTION 'return window expired' USING ERRCODE = 'check_violation', CONSTRAINT = 'trg_return_item_eligibility';
  END IF;
  RETURN NEW;
END $$ LANGUAGE plpgsql;

CREATE TRIGGER trg_return_item_eligibility
  BEFORE INSERT ON return_items FOR EACH ROW EXECUTE FUNCTION rr_return_item_eligibility();

-- [BR-13] Sum of refund_items = refunds.amount_paise (checked at COMMIT).
CREATE OR REPLACE FUNCTION rr_refund_items_sum() RETURNS trigger AS $$
DECLARE v_refund uuid; v_amount bigint; v_sum bigint;
BEGIN
  v_refund := CASE WHEN TG_TABLE_NAME = 'refunds' THEN NEW.id ELSE NEW.refund_id END;
  SELECT amount_paise INTO v_amount FROM refunds WHERE id = v_refund;
  SELECT COALESCE(SUM(amount_paise), 0) INTO v_sum FROM refund_items WHERE refund_id = v_refund;
  IF v_amount IS DISTINCT FROM v_sum THEN
    RAISE EXCEPTION 'refund % amount % <> sum of items %', v_refund, v_amount, v_sum
      USING ERRCODE = 'check_violation', CONSTRAINT = 'trg_refund_items_sum';
  END IF;
  RETURN NULL;
END $$ LANGUAGE plpgsql;

CREATE CONSTRAINT TRIGGER trg_refund_items_sum_refunds AFTER INSERT OR UPDATE OF amount_paise ON refunds
  DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION rr_refund_items_sum();
CREATE CONSTRAINT TRIGGER trg_refund_items_sum_items AFTER INSERT ON refund_items
  DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION rr_refund_items_sum();

-- [BR-18] Double entry must balance, with at least two lines, per transaction (checked at COMMIT).
CREATE OR REPLACE FUNCTION rr_ledger_balanced() RETURNS trigger AS $$
DECLARE v_txn bigint; v_debit bigint; v_credit bigint; v_lines int;
BEGIN
  v_txn := CASE WHEN TG_TABLE_NAME = 'ledger_transactions' THEN NEW.id ELSE NEW.transaction_id END;
  SELECT COALESCE(SUM(amount_paise) FILTER (WHERE direction = 'DEBIT'), 0),
         COALESCE(SUM(amount_paise) FILTER (WHERE direction = 'CREDIT'), 0), COUNT(*)
    INTO v_debit, v_credit, v_lines FROM ledger_entries WHERE transaction_id = v_txn;
  IF v_lines < 2 OR v_debit <> v_credit THEN
    RAISE EXCEPTION 'ledger transaction % unbalanced (debit %, credit %, lines %)', v_txn, v_debit, v_credit, v_lines
      USING ERRCODE = 'check_violation', CONSTRAINT = 'trg_ledger_balanced';
  END IF;
  RETURN NULL;
END $$ LANGUAGE plpgsql;

CREATE CONSTRAINT TRIGGER trg_ledger_balanced_txn AFTER INSERT ON ledger_transactions
  DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION rr_ledger_balanced();
CREATE CONSTRAINT TRIGGER trg_ledger_balanced_entries AFTER INSERT ON ledger_entries
  DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION rr_ledger_balanced();

-- [BR-19][BR-20] Append-only tables: no UPDATE or DELETE for anyone.
CREATE OR REPLACE FUNCTION rr_immutable() RETURNS trigger AS $$
BEGIN
  RAISE EXCEPTION '% is append-only (% rejected)', TG_TABLE_NAME, TG_OP
    USING ERRCODE = 'insufficient_privilege', CONSTRAINT = 'trg_immutable';
END $$ LANGUAGE plpgsql;

CREATE TRIGGER trg_ledger_transactions_immutable BEFORE UPDATE OR DELETE ON ledger_transactions
  FOR EACH ROW EXECUTE FUNCTION rr_immutable();
CREATE TRIGGER trg_ledger_entries_immutable BEFORE UPDATE OR DELETE ON ledger_entries
  FOR EACH ROW EXECUTE FUNCTION rr_immutable();
CREATE TRIGGER trg_audit_log_immutable BEFORE UPDATE OR DELETE ON audit_log
  FOR EACH ROW EXECUTE FUNCTION rr_immutable();

-- Chart of accounts (Part 1 §7.2)
INSERT INTO ledger_accounts (code, name, normal_side) VALUES
  ('SALES_RETURNS', 'Sales returns (contra-revenue)', 'DEBIT'),
  ('RETURN_CONDITION_RECOVERY', 'Value retained for item condition', 'CREDIT'),
  ('GATEWAY_SETTLEMENT', 'Cash paid out via payment gateway', 'CREDIT'),
  ('STORE_CREDIT_LIABILITY', 'Store credit owed to customers', 'CREDIT');

