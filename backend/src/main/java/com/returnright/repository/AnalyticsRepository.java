package com.returnright.repository;

import com.returnright.model.dto.response.CategoryReturnRateResponse;
import com.returnright.model.dto.response.FlaggedCustomerResponse;
import com.returnright.model.dto.response.ReasonCountResponse;
import com.returnright.model.enums.ProductCategory;
import com.returnright.model.enums.ReturnReason;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Read-only dashboard queries, computed live (the data set is small). Cancelled returns are
 * ignored.
 */
@Repository
public class AnalyticsRepository {

  private static final int TOP_REASON_LIMIT = 5;

  private final JdbcTemplate jdbcTemplate;

  public AnalyticsRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public List<CategoryReturnRateResponse> returnRateByCategory() {
    String sql =
        """
        WITH sold AS (
            SELECT p.category, SUM(oi.quantity) AS units
            FROM order_item oi JOIN product p ON p.id = oi.product_id
            GROUP BY p.category),
        returned AS (
            SELECT p.category, SUM(ri.quantity) AS units
            FROM return_item ri
            JOIN return_request r ON r.id = ri.return_id
            JOIN order_item oi ON oi.id = ri.order_item_id
            JOIN product p ON p.id = oi.product_id
            WHERE r.status <> 'CANCELLED'
            GROUP BY p.category)
        SELECT s.category, s.units AS sold, COALESCE(r.units, 0) AS returned
        FROM sold s LEFT JOIN returned r ON r.category = s.category
        ORDER BY s.category
        """;
    return jdbcTemplate.query(
        sql,
        (rs, rowNum) -> {
          long sold = rs.getLong("sold");
          long returned = rs.getLong("returned");
          return new CategoryReturnRateResponse(
              ProductCategory.valueOf(rs.getString("category")),
              sold,
              returned,
              percent(returned, sold));
        });
  }

  public List<ReasonCountResponse> topReasons() {
    String sql =
        """
        SELECT ri.reason, COUNT(*) AS total
        FROM return_item ri JOIN return_request r ON r.id = ri.return_id
        WHERE r.status <> 'CANCELLED'
        GROUP BY ri.reason
        ORDER BY total DESC, ri.reason
        LIMIT ?
        """;
    return jdbcTemplate.query(
        sql,
        (rs, rowNum) ->
            new ReasonCountResponse(
                ReturnReason.valueOf(rs.getString("reason")), rs.getLong("total")),
        TOP_REASON_LIMIT);
  }

  /** Average hours from request to refund, or {@code null} when nothing has been refunded. */
  public Double averageCycleTimeHours() {
    String sql =
        """
        SELECT AVG(EXTRACT(EPOCH FROM (closed_at - created_at)) / 3600.0)
        FROM return_request WHERE status = 'REFUNDED'
        """;
    BigDecimal hours = jdbcTemplate.queryForObject(sql, BigDecimal.class);
    return hours == null ? null : hours.setScale(1, RoundingMode.HALF_UP).doubleValue();
  }

  public List<FlaggedCustomerResponse> flaggedCustomers() {
    String sql =
        """
        SELECT c.id, c.full_name, c.email, COUNT(*) AS flagged_returns, MAX(r.created_at) AS last_flagged_at
        FROM return_request r JOIN customer c ON c.id = r.customer_id
        WHERE r.flagged
        GROUP BY c.id, c.full_name, c.email
        ORDER BY last_flagged_at DESC
        """;
    return jdbcTemplate.query(
        sql,
        (rs, rowNum) ->
            new FlaggedCustomerResponse(
                rs.getObject("id", UUID.class),
                rs.getString("full_name"),
                rs.getString("email"),
                rs.getLong("flagged_returns")));
  }

  private static double percent(long part, long whole) {
    if (whole == 0) {
      return 0.0;
    }
    return BigDecimal.valueOf(part * 100.0 / whole).setScale(1, RoundingMode.HALF_UP).doubleValue();
  }
}
