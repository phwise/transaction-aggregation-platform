package com.capitec.service;

import com.capitec.base.exception.MissingCustomerIdException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class CustomerIdentityService {

    private static final String CUSTOMER_ID_HEADER = "X-Customer-Id";

    private static final Pattern CUSTOMER_ID =
            Pattern.compile("[A-Za-z0-9_-]{1,50}");

    private final HttpServletRequest request;

    public String getCurrentCustomerId() {

        String customerId = request.getHeader(CUSTOMER_ID_HEADER);

        if (customerId != null
                && CUSTOMER_ID.matcher(customerId).matches()) {
            return customerId;
        }

        throw new MissingCustomerIdException();
    }
}
