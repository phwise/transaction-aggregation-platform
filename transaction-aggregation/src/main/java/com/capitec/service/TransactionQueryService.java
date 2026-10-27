package com.capitec.service;

import com.capitec.base.constant.TransactionFilter;
import com.capitec.base.exception.TransactionNotFoundException;
import com.capitec.base.db.entity.Transaction;
import com.capitec.base.db.repository.TransactionRepository;
import com.capitec.base.db.entity.TransactionSpecifications;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Customer-scoped read use cases. Every query takes the authenticated customer ID; no method
 * accepts a transaction without also filtering by that customer.
 */
@Service
@Transactional(readOnly = true)
public class TransactionQueryService {

    private final TransactionRepository repository;

    public TransactionQueryService(TransactionRepository repository) {
        this.repository = repository;
    }

    public Transaction get(String customerId, UUID transactionId) {
        return repository.findByIdAndCustomerId(transactionId, customerId)
                .orElseThrow(TransactionNotFoundException::new);
    }

    public Page<Transaction> search(String customerId, TransactionFilter filter, Pageable pageable) {
        return repository.findAll(TransactionSpecifications.matching(customerId, filter), pageable);
    }
}

