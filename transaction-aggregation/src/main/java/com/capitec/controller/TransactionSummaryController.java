
package com.capitec.controller;

import com.capitec.base.constant.UtcDateRange;
import com.capitec.service.CustomerIdentityService;
import com.capitec.service.TransactionAnalyticsService;
import com.capitec.service.TransactionAnalyticsService.CategoryTotals;
import com.capitec.service.TransactionAnalyticsService.CurrencyTotals;
import com.capitec.service.TransactionAnalyticsService.MonthTotals;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/transactions/summary")
@RequiredArgsConstructor
@Slf4j
@Tag(
        name = "Transaction summaries",
        description = "Totals for the authenticated customer. Money totals are per currency and count COMPLETED transactions only."
)
public class TransactionSummaryController {

    private static final String SEMANTICS =
            "Money totals include COMPLETED transactions only. "
                    + "FAILED, REVERSED and REFUNDED are excluded from money totals; "
                    + "PENDING is reported separately. "
                    + "Amounts are never converted between currencies. "
                    + "from and to are optional UTC calendar days, both inclusive.";

    private final TransactionAnalyticsService analytics;
    private final CustomerIdentityService customerIdentityService;

    @GetMapping
    @Operation(
            summary = "Totals by currency, with pending, failed, reversed and refunded counts",
            description = SEMANTICS
    )
    public SummaryResponseDTO summary(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {

        long startNanos = System.nanoTime();

        log.info("GET /summary started: from={}, to={}", from, to);

        try {
            String customerId = customerIdentityService.getCurrentCustomerId();

            log.debug("Customer identity resolved for summary request");

            UtcDateRange range = new UtcDateRange(from, to);

            log.debug("Calling currencyTotals: range={}", range);

            List<CurrencyTotals> totals =
                    analytics.currencyTotals(customerId, range);

            SummaryResponseDTO response = new SummaryResponseDTO(
                    totals.stream().map(CurrencyTotalsDTO::from).toList()
            );

            log.info(
                    "GET /summary completed: currencies={}, durationMs={}",
                    response.currencies().size(),
                    elapsedMillis(startNanos)
            );

            if (log.isDebugEnabled()) {
                response.currencies().forEach(currency ->
                        log.debug(
                                "Summary result: currency={}, completedCount={}, " +
                                        "completedCredits={}, completedDebits={}, completedNet={}, " +
                                        "pendingCount={}, failedCount={}, reversedCount={}, refundedCount={}",
                                currency.currency(),
                                currency.completedCount(),
                                currency.completedCredits(),
                                currency.completedDebits(),
                                currency.completedNet(),
                                currency.pendingCount(),
                                currency.failedCount(),
                                currency.reversedCount(),
                                currency.refundedCount()
                        )
                );
            }

            return response;

        } catch (RuntimeException ex) {
            log.error(
                    "GET /summary failed: from={}, to={}, durationMs={}",
                    from, to, elapsedMillis(startNanos), ex
            );
            throw ex;
        }
    }

    @GetMapping("/by-category")
    @Operation(
            summary = "Completed totals per category and currency",
            description = SEMANTICS
    )
    public CategorySummaryResponseDTO byCategory(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {

        long startNanos = System.nanoTime();

        log.info("GET /summary/by-category started: from={}, to={}", from, to);

        try {
            String customerId = customerIdentityService.getCurrentCustomerId();

            UtcDateRange range = new UtcDateRange(from, to);

            log.debug("Calling categoryTotals: range={}", range);

            List<CategoryTotals> totals =
                    analytics.categoryTotals(customerId, range);

            CategorySummaryResponseDTO response =
                    new CategorySummaryResponseDTO(
                            totals.stream().map(CategoryTotalsDTO::from).toList()
                    );

            log.info(
                    "GET /summary/by-category completed: categories={}, durationMs={}",
                    response.categories().size(),
                    elapsedMillis(startNanos)
            );

            if (log.isDebugEnabled()) {
                response.categories().forEach(category ->
                        log.debug(
                                "Category result: category={}, currency={}, completedCount={}, " +
                                        "credits={}, debits={}",
                                category.category(),
                                category.currency(),
                                category.completedCount(),
                                category.completedCredits(),
                                category.completedDebits()
                        )
                );
            }

            return response;

        } catch (RuntimeException ex) {
            log.error(
                    "GET /summary/by-category failed: from={}, to={}, durationMs={}",
                    from, to, elapsedMillis(startNanos), ex
            );
            throw ex;
        }
    }

    @GetMapping("/by-month")
    @Operation(
            summary = "Completed totals per UTC calendar month and currency",
            description = SEMANTICS
    )
    public MonthlySummaryResponseDTO byMonth(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {

        long startNanos = System.nanoTime();

        log.info("GET /summary/by-month started: from={}, to={}", from, to);

        try {
            String customerId = customerIdentityService.getCurrentCustomerId();

            UtcDateRange range = new UtcDateRange(from, to);

            log.debug("Calling monthTotals: range={}", range);

            List<MonthTotals> totals =
                    analytics.monthTotals(customerId, range);

            MonthlySummaryResponseDTO response =
                    new MonthlySummaryResponseDTO(
                            totals.stream().map(MonthTotalsDTO::from).toList()
                    );

            log.info(
                    "GET /summary/by-month completed: months={}, durationMs={}",
                    response.months().size(),
                    elapsedMillis(startNanos)
            );

            if (log.isDebugEnabled()) {
                response.months().forEach(month ->
                        log.debug(
                                "Monthly result: month={}, currency={}, completedCount={}, " +
                                        "credits={}, debits={}, net={}",
                                month.month(),
                                month.currency(),
                                month.completedCount(),
                                month.completedCredits(),
                                month.completedDebits(),
                                month.completedNet()
                        )
                );
            }

            return response;

        } catch (RuntimeException ex) {
            log.error(
                    "GET /summary/by-month failed: from={}, to={}, durationMs={}",
                    from, to, elapsedMillis(startNanos), ex
            );
            throw ex;
        }
    }

    private static long elapsedMillis(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }

    public record SummaryResponseDTO(List<CurrencyTotalsDTO> currencies) {
    }

    public record CurrencyTotalsDTO(
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
        static CurrencyTotalsDTO from(CurrencyTotals t) {
            return new CurrencyTotalsDTO(
                    t.currency(),
                    t.completedCredits(),
                    t.completedDebits(),
                    t.completedNet(),
                    t.completedCount(),
                    t.pendingCredits(),
                    t.pendingDebits(),
                    t.pendingCount(),
                    t.failedCount(),
                    t.reversedCount(),
                    t.refundedCount()
            );
        }
    }

    public record CategorySummaryResponseDTO(
            List<CategoryTotalsDTO> categories
    ) {
    }

    public record CategoryTotalsDTO(
            String category,
            String currency,
            long completedCount,
            BigDecimal completedCredits,
            BigDecimal completedDebits
    ) {
        static CategoryTotalsDTO from(CategoryTotals t) {
            return new CategoryTotalsDTO(
                    t.category().name(),
                    t.currency(),
                    t.completedCount(),
                    t.completedCredits(),
                    t.completedDebits()
            );
        }
    }

    public record MonthlySummaryResponseDTO(List<MonthTotalsDTO> months) {
    }

    public record MonthTotalsDTO(
            String month,
            String currency,
            long completedCount,
            BigDecimal completedCredits,
            BigDecimal completedDebits,
            BigDecimal completedNet
    ) {
        static MonthTotalsDTO from(MonthTotals t) {
            return new MonthTotalsDTO(
                    t.month(),
                    t.currency(),
                    t.completedCount(),
                    t.completedCredits(),
                    t.completedDebits(),
                    t.completedNet()
            );
        }
    }
}
