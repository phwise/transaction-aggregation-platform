package com.capitec.service;

import com.capitec.base.constant.UtcDateRange;
import com.capitec.domain.TransactionCategory;
import com.capitec.domain.TransactionDirection;
import com.capitec.base.db.repository.TransactionAnalyticsRepository;
import com.capitec.base.db.repository.TransactionAnalyticsRepository.GroupedTotalsRow;
import com.capitec.base.db.repository.TransactionAnalyticsRepository.StatusTotalsRow;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Analytics semantics:
 * <ul>
 *   <li>Money totals (credits, debits, net) include only COMPLETED transactions.</li>
 *   <li>PENDING transactions are reported separately as a count and amounts; they are not in money totals.</li>
 *   <li>FAILED, REVERSED and REFUNDED transactions are counted but never add to money totals.</li>
 *   <li>Totals are per currency. Amounts in different currencies are never added together.</li>
 *   <li>Date bounds are inclusive UTC calendar days; both are optional.</li>
 * </ul>
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
@Slf4j
public class TransactionAnalyticsService {

    public record CurrencyTotals(
            String currency,
            BigDecimal completedCredits,
            BigDecimal completedDebits,
            BigDecimal completedNet,
            long completedCount,
            BigDecimal pendingCredits,
            BigDecimal pendingDebits,
            long pendingCount,
            long failedCount,
            long reversedCount,
            long refundedCount
    ) {
    }

    public record CategoryTotals(
            TransactionCategory category,
            String currency,
            long completedCount,
            BigDecimal completedCredits,
            BigDecimal completedDebits
    ) {
    }

    public record MonthTotals(
            String month,
            String currency,
            long completedCount,
            BigDecimal completedCredits,
            BigDecimal completedDebits,
            BigDecimal completedNet
    ) {
    }

    private final TransactionAnalyticsRepository repository;


    /** Completed transactions only, grouped by category. Unset categories are reported as OTHER. */

    /**
     * Completed transactions only, grouped by category.
     * Unset categories are reported as OTHER.
     */
    public List<CategoryTotals> categoryTotals(String customerId, UtcDateRange range) {
        log.info("Starting categoryTotals: customerId={}, range={}", customerId, range);

        try {
            List<GroupedTotalsRow> rows =
                    repository.completedTotalsByCategory(customerId, range);

            log.debug("categoryTotals repository returned {} rows for customerId={}",
                    rows.size(), customerId);

            Map<GroupKey, Flow> grouped = fold(rows);

            List<CategoryTotals> result = grouped.entrySet().stream()
                    .map(e -> new CategoryTotals(
                            TransactionCategory.valueOf(e.getKey().group()),
                            e.getKey().currency(),
                            e.getValue().count,
                            e.getValue().credits,
                            e.getValue().debits))
                    .toList();

            log.info("Completed categoryTotals: customerId={}, repositoryRows={}, resultGroups={}",
                    customerId, rows.size(), result.size());

            if (log.isDebugEnabled()) {
                result.forEach(total -> log.debug(
                        "Category total: customerId={}, category={}, currency={}, " +
                                "completedCount={}, credits={}, debits={}",
                        customerId, total.category(), total.currency(),
                        total.completedCount(), total.completedCredits(),
                        total.completedDebits()));
            }

            return result;
        } catch (RuntimeException ex) {
            log.error("Failed categoryTotals: customerId={}, range={}",
                    customerId, range, ex);
            throw ex;
        }
    }


    /** Completed transactions only, grouped by UTC calendar month (YYYY-MM). */
    public List<MonthTotals> monthTotals(String customerId, UtcDateRange range) {
        return fold(repository.completedTotalsByMonth(customerId, range)).entrySet().stream()
                .map(e -> new MonthTotals(
                        e.getKey().group(),
                        e.getKey().currency(),
                        e.getValue().count,
                        e.getValue().credits,
                        e.getValue().debits,
                        e.getValue().credits.subtract(e.getValue().debits)))
                .toList();
    }

    private record GroupKey(String group, String currency) {
    }


    private static Map<GroupKey, Flow> fold(List<GroupedTotalsRow> rows) {
        log.debug("Starting fold: inputRows={}", rows.size());

        Map<GroupKey, Flow> flows = new LinkedHashMap<>();

        for (GroupedTotalsRow row : rows) {
            GroupKey key = new GroupKey(row.groupKey(), row.currency());

            log.trace("Folding row: group={}, currency={}, direction={}, count={}, total={}",
                    row.groupKey(), row.currency(), row.direction(),
                    row.count(), row.total());

            flows.computeIfAbsent(key, ignored -> new Flow()).add(row);
        }

        log.debug("Completed fold: inputRows={}, outputGroups={}",
                rows.size(), flows.size());

        return flows;
    }


    private static final class Flow {
        private long count;
        private BigDecimal credits = zero();
        private BigDecimal debits = zero();

        void add(GroupedTotalsRow row) {
            count += row.count();
            if (row.direction() == TransactionDirection.CREDIT) {
                credits = credits.add(row.total());
            } else {
                debits = debits.add(row.total());
            }
        }
    }


    public List<CurrencyTotals> currencyTotals(String customerId, UtcDateRange range) {
        log.info("Starting currencyTotals: customerId={}, range={}", customerId, range);

        try {
            List<StatusTotalsRow> rows =
                    repository.totalsByCurrencyStatusAndDirection(customerId, range);

            log.debug("currencyTotals repository returned {} rows for customerId={}",
                    rows.size(), customerId);

            Map<String, Accumulator> byCurrency = new TreeMap<>();

            for (StatusTotalsRow row : rows) {
                log.debug(
                        "Processing analytics row: currency={}, status={}, direction={}, count={}, total={}",
                        row.currency(), row.status(), row.direction(),
                        row.count(), row.total());

                byCurrency.computeIfAbsent(
                        row.currency(), currency -> new Accumulator()).add(row);
            }

            List<CurrencyTotals> result = byCurrency.entrySet().stream()
                    .map(entry -> entry.getValue().build(entry.getKey()))
                    .toList();

            log.info("Completed currencyTotals: customerId={}, repositoryRows={}, currencies={}",
                    customerId, rows.size(), result.size());

            if (log.isDebugEnabled()) {
                result.forEach(total -> log.debug(
                        "Currency summary: customerId={}, currency={}, " +
                                "completedCount={}, completedCredits={}, completedDebits={}, " +
                                "completedNet={}, pendingCount={}, pendingCredits={}, " +
                                "pendingDebits={}, failedCount={}, reversedCount={}, refundedCount={}",
                        customerId, total.currency(),
                        total.completedCount(), total.completedCredits(),
                        total.completedDebits(), total.completedNet(),
                        total.pendingCount(), total.pendingCredits(),
                        total.pendingDebits(), total.failedCount(),
                        total.reversedCount(), total.refundedCount()));
            }

            return result;
        } catch (RuntimeException ex) {
            log.error("Failed currencyTotals: customerId={}, range={}",
                    customerId, range, ex);
            throw ex;
        }
    }


    private static final class Accumulator {
        private BigDecimal completedCredits = zero();
        private BigDecimal completedDebits = zero();
        private long completedCount;
        private BigDecimal pendingCredits = zero();
        private BigDecimal pendingDebits = zero();
        private long pendingCount;
        private long failedCount;
        private long reversedCount;
        private long refundedCount;


        void add(StatusTotalsRow row) {
            boolean credit = row.direction() == TransactionDirection.CREDIT;

            log.trace(
                    "Accumulating currency totals: currency={}, status={}, direction={}, count={}, total={}",
                    row.currency(), row.status(), row.direction(),
                    row.count(), row.total());

            switch (row.status()) {
                case COMPLETED -> {
                    completedCount += row.count();
                    if (credit) {
                        completedCredits = completedCredits.add(row.total());
                    } else {
                        completedDebits = completedDebits.add(row.total());
                    }
                }
                case PENDING -> {
                    pendingCount += row.count();
                    if (credit) {
                        pendingCredits = pendingCredits.add(row.total());
                    } else {
                        pendingDebits = pendingDebits.add(row.total());
                    }
                }
                case FAILED -> failedCount += row.count();
                case REVERSED -> reversedCount += row.count();
                case REFUNDED -> refundedCount += row.count();
            }

            log.trace(
                    "Accumulator state: currency={}, completedCount={}, pendingCount={}, " +
                            "failedCount={}, reversedCount={}, refundedCount={}",
                    row.currency(), completedCount, pendingCount,
                    failedCount, reversedCount, refundedCount);
        }


        CurrencyTotals build(String currency) {
            return new CurrencyTotals(
                    currency,
                    completedCredits,
                    completedDebits,
                    completedCredits.subtract(completedDebits),
                    completedCount,
                    pendingCredits,
                    pendingDebits,
                    pendingCount,
                    failedCount,
                    reversedCount,
                    refundedCount
            );
        }

    }

    private static BigDecimal zero() {
        return BigDecimal.ZERO.setScale(2, RoundingMode.UNNECESSARY);
    }
}