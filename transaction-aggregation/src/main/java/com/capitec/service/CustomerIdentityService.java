package com.capitec.service;

import com.capitec.base.exception.MissingCustomerIdException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;

import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class CustomerIdentityService {

    private static final Pattern CUSTOMER_ID =
            Pattern.compile("[A-Za-z0-9_-]{1,50}");

    public String getCurrentCustomerId() {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication instanceof JwtAuthenticationToken jwtAuthentication) {
            String subject = jwtAuthentication.getToken().getSubject();
            if (subject != null && CUSTOMER_ID.matcher(subject).matches()) {
                return subject;
            }
        }

        throw new MissingCustomerIdException();
    }
}
