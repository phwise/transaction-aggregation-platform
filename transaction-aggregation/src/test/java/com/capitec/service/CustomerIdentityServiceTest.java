package com.capitec.service;

import com.capitec.base.exception.MissingCustomerIdException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CustomerIdentityServiceTest {

    private final CustomerIdentityService service = new CustomerIdentityService();

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void returnsTheJwtSubjectAsCustomerId() {
        authenticateWithSubject("alice-01");

        assertThat(service.getCurrentCustomerId()).isEqualTo("alice-01");
    }

    @Test
    void acceptsSubjectAtTheMaximumLength() {
        String subject = "a".repeat(50);
        authenticateWithSubject(subject);

        assertThat(service.getCurrentCustomerId()).isEqualTo(subject);
    }

    @Test
    void rejectsSubjectLongerThanFiftyCharacters() {
        authenticateWithSubject("a".repeat(51));

        assertThatThrownBy(service::getCurrentCustomerId).isInstanceOf(MissingCustomerIdException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "alice@example.com", "has space", "a/../b", "robert;drop"})
    void rejectsSubjectsOutsideTheAllowedFormat(String subject) {
        authenticateWithSubject(subject);

        assertThatThrownBy(service::getCurrentCustomerId).isInstanceOf(MissingCustomerIdException.class);
    }

    @Test
    void rejectsTokenWithoutSubject() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .claim("iss", "https://issuer.example")
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));

        assertThatThrownBy(service::getCurrentCustomerId).isInstanceOf(MissingCustomerIdException.class);
    }

    @Test
    void rejectsRequestWithoutAuthentication() {
        assertThatThrownBy(service::getCurrentCustomerId).isInstanceOf(MissingCustomerIdException.class);
    }

    @Test
    void rejectsNonJwtAuthentication() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("alice-01", "password"));

        assertThatThrownBy(service::getCurrentCustomerId).isInstanceOf(MissingCustomerIdException.class);
    }

    private static void authenticateWithSubject(String subject) {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject(subject)
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }
}