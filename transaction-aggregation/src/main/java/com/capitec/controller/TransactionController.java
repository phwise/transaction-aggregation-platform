package com.capitec.controller;

import com.capitec.base.constant.TransactionSearchParams;
import com.capitec.base.dto.AggregationResponseDTO;
import com.capitec.base.dto.PageResponseDTO;
import com.capitec.base.dto.TransactionResponseDTO;
import com.capitec.base.constant.AggregationOutcome;
import com.capitec.service.IdempotencyService;
import com.capitec.base.constant.IdempotentResult;
import com.capitec.base.exception.InvalidRequestException;
import com.capitec.base.constant.StoredResponse;
import com.capitec.service.TransactionQueryService;
import com.capitec.service.AggregationService;
import com.capitec.service.CustomerIdentityService;
import com.capitec.base.constant.AggregationHttpStatus;
//import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.RequiredArgsConstructor;
//import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.ObjectMapper;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@RestController
@Validated
@RequestMapping("/api/v1/transactions")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Transactions", description = "Aggregation and retrieval of the authenticated customer's transactions")
public class TransactionController {

    private static final String AGGREGATE_OPERATION = "AGGREGATE_TRANSACTIONS";
    private static final String AGGREGATE_CANONICAL_REQUEST = "AGGREGATE_TRANSACTIONS;v1;no-body";

    private static final Map<String, String> SORTABLE_FIELDS = Map.of(
            "transactionDate", "transactionDate",
            "amount", "amount",
            "createdAt", "createdAt",
            "status", "status"
    );

    private final AggregationService aggregationService;
    private final TransactionQueryService queryService;
    private final CustomerIdentityService customerIdentityService;
    private final IdempotencyService idempotencyService;
    private final ObjectMapper objectMapper;


    @PostMapping(value = "/aggregate", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Aggregate transactions from all providers for the authenticated customer",
            description = "Send an Idempotency-Key header to make retries safe. A repeated key returns the original response "
                    + "with Idempotent-Replayed: true. Reusing a key for a different request returns 422. A key still in progress returns 409."
    )
    public ResponseEntity<String> aggregateTransactions(
            @RequestHeader(value = "Idempotency-Key", required = true)
            @Size(min = 1, max = 255)
            @Pattern(regexp = "[\\x21-\\x7E]+", message = "Idempotency-Key must be visible ASCII without spaces") final
            String idempotencyKey
    ) {
        log.atInfo().addKeyValue("idempotencyKey",idempotencyKey).log("Received aggregation request");

        String customerId = customerIdentityService.getCurrentCustomerId();
        log.atInfo().addKeyValue("idempotencyKey",idempotencyKey).log("Customer identity resolved");

        IdempotentResult result = idempotencyService.execute(
                customerId,
                AGGREGATE_OPERATION,
                idempotencyKey,
                AGGREGATE_CANONICAL_REQUEST,
                () ->{
                    log.atInfo().addKeyValue("idempotencyKey",idempotencyKey).log("Idempotency callback invoked");
                    return runAggregation(customerId,idempotencyKey);}
        );
        log.atInfo()
                .addKeyValue("idempotencyKey",idempotencyKey)
                .log("Idempotency result returned. Status={}, replayed={}",
                result.response().status(),
                result.replayed()
        );
        ResponseEntity.BodyBuilder response = ResponseEntity.status(result.response().status())
                .contentType(MediaType.APPLICATION_JSON);
        if (idempotencyKey != null) {
            response.header("Idempotent-Replayed", String.valueOf(result.replayed()));
        }
        return response.body(result.response().body());
    }

    @GetMapping
    @Operation(summary = "List the customer's transactions with optional filters, sorting and pagination",
            description = "Filters are combined with AND. Dates are UTC calendar days and both bounds are inclusive. "
                    + "amount bounds are inclusive and apply to the unsigned amount. q matches description or merchant, case-insensitively. "
                    + "sortBy accepts transactionDate, amount, createdAt or status."
    )
    public PageResponseDTO<TransactionResponseDTO> list(
            @RequestParam(defaultValue = "0") @PositiveOrZero int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize,
            @RequestParam(defaultValue = "transactionDate") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDirection,
            @ModelAttribute TransactionSearchParams filter
    ) {
        String customerId = customerIdentityService.getCurrentCustomerId();
        Pageable pageable = pageRequest(page, pageSize, sortBy, sortDirection);
        return PageResponseDTO.fromPage(
                queryService.search(customerId, filter.toFilter(), pageable).map(TransactionResponseDTO::fromEntity));
    }

    @GetMapping("/{transactionId}")
    @Operation(summary = "Get one of the customer's transactions; other customers' IDs return 404")
    public TransactionResponseDTO get(@PathVariable UUID transactionId) {
        String customerId = customerIdentityService.getCurrentCustomerId();
        return TransactionResponseDTO.fromEntity(queryService.get(customerId, transactionId));
    }

    private StoredResponse runAggregation(final String customerId,final String idempotencyKey) {
        log.atInfo()
                .addKeyValue("idempotencyKey",idempotencyKey)
                .log("Running transaction aggregation");
        AggregationOutcome outcome = aggregationService.aggregateTransactions(customerId,idempotencyKey);
        return new StoredResponse(
                AggregationHttpStatus.of(outcome.status()).value(),
                toJson(AggregationResponseDTO.fromOutcome(outcome))
        );
    }

    private String toJson(AggregationResponseDTO dto) {
        try {
            return objectMapper.writeValueAsString(dto);
        } catch (tools.jackson.core.JacksonException e) {
            throw new IllegalStateException(
                    "Could not serialize aggregation response",
                    e
            );
        }
    }

    private Pageable pageRequest(int page, int pageSize, String sortBy, String sortDirection) {
        String property = SORTABLE_FIELDS.get(sortBy);
        if (property == null) {
            throw new InvalidRequestException("sortBy must be one of: transactionDate, amount, createdAt, status");
        }
        Sort.Direction direction = Sort.Direction.fromOptionalString(sortDirection.toUpperCase(Locale.ROOT))
                .orElseThrow(() -> new InvalidRequestException("sortDirection must be ASC or DESC"));
        return PageRequest.of(page, pageSize, Sort.by(direction, property).and(Sort.by(Sort.Direction.DESC, "id")));
    }
}
