package com.capitec.base.db.repository;

import com.capitec.base.constant.UtcDateRange;
import com.capitec.domain.TransactionDirection;
import com.capitec.domain.TransactionStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

/**
 * Aggregates are computed by the database with GROUP BY, so the rows returned are bounded by
 * currency x status x direction (or category/month) rather than by the number of transactions.
 */
@Repository
public class TransactionAnalyticsRepository {

    public record StatusTotalsRow(
            String currency,
            TransactionStatus status,
            TransactionDirection direction,
            long count,
            BigDecimal total
    ) {
    }

    public record GroupedTotalsRow(
            String groupKey,
            String currency,
            TransactionDirection direction,
            long count,
            BigDecimal total
    ) {
    }

    private final JdbcTemplate jdbc;

    public TransactionAnalyticsRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<StatusTotalsRow> totalsByCurrencyStatusAndDirection(String customerId, UtcDateRange range) {
        List<Object> args = new ArrayList<>();
        args.add(customerId);
        String sql = """
            SELECT currency, status, direction, COUNT(*) AS row_count, SUM(amount) AS total
            FROM transactions
            WHERE customer_id = ?
            """ + dateClause(range, args) + "\n" + """
            GROUP BY currency, status, direction
            """;
        return jdbc.query(sql, (rs, rowNum) -> new StatusTotalsRow(
                rs.getString("currency"),
                TransactionStatus.valueOf(rs.getString("status")),
                TransactionDirection.valueOf(rs.getString("direction")),
                rs.getLong("row_count"),
                rs.getBigDecimal("total")
        ), args.toArray());
    }

    public List<GroupedTotalsRow> completedTotalsByCategory(String customerId, UtcDateRange range) {
        return completedTotalsGroupedBy("COALESCE(category, 'OTHER')", customerId, range);
    }

    public List<GroupedTotalsRow> completedTotalsByMonth(String customerId, UtcDateRange range) {
        return completedTotalsGroupedBy("to_char(transaction_date AT TIME ZONE 'UTC', 'YYYY-MM')", customerId, range);
    }

    private List<GroupedTotalsRow> completedTotalsGroupedBy(String groupExpression, String customerId, UtcDateRange range) {
        List<Object> args = new ArrayList<>();
        args.add(customerId);
        String sql = "SELECT " + groupExpression + " AS group_key, currency, direction, COUNT(*) AS row_count, SUM(amount) AS total\n"
                + "FROM transactions\n"
                + "WHERE customer_id = ? AND status = 'COMPLETED'" + dateClause(range, args) + "\n"
                + "GROUP BY group_key, currency, direction\n"
                + "ORDER BY group_key, currency";
        return jdbc.query(sql, (rs, rowNum) -> new GroupedTotalsRow(
                rs.getString("group_key"),
                rs.getString("currency"),
                TransactionDirection.valueOf(rs.getString("direction")),
                rs.getLong("row_count"),
                rs.getBigDecimal("total")
        ), args.toArray());
    }

    private static String dateClause(UtcDateRange range, List<Object> args) {
        StringBuilder clause = new StringBuilder();
        if (range.startInclusive() != null) {
            clause.append(" AND transaction_date >= ?");
            args.add(Timestamp.from(range.startInclusive()));
        }
        if (range.endExclusive() != null) {
            clause.append(" AND transaction_date < ?");
            args.add(Timestamp.from(range.endExclusive()));
        }
        return clause.toString();
    }
}
