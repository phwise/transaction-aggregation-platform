package com.capitec;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Testcontainers
class TransactionApiIntegrationTest {

    private static final String BASE = "/api/v1/transactions";
    private static final String ALICE = "alice-customer";
    private static final String BOB = "bob-customer";

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17");

    @Autowired
    private WebApplicationContext context;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void requestsWithoutAJwtAreRejected() throws Exception {
        mvc.perform(get(BASE)).andExpect(status().isUnauthorized());
    }

    @Test
    void validTokenWithoutASubjectIsUnauthorizedNotServerError() throws Exception {
        mvc.perform(get(BASE).with(jwt().jwt(token -> token.claims(claims -> claims.remove("sub")))))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer error=\"invalid_token\""));
    }

    @Test
    void repeatedIdempotencyKeyReplaysTheStoredResponse() throws Exception {
        String key = UUID.randomUUID().toString();

        MvcResult first = mvc.perform(aggregate(ALICE, key))
                .andExpect(status().isOk())
                .andExpect(header().string("Idempotent-Replayed", "false"))
                .andExpect(jsonPath("$.status").value("COMPLETE_SUCCESS"))
                .andReturn();

        mvc.perform(aggregate(ALICE, key))
                .andExpect(status().isOk())
                .andExpect(header().string("Idempotent-Replayed", "true"))
                .andExpect(result -> assertThat(result.getResponse().getContentAsString())
                        .isEqualTo(first.getResponse().getContentAsString()));
    }

    @Test
    void listReturnsOnlyTheAuthenticatedCustomersTransactions() throws Exception {
        mvc.perform(aggregate(ALICE, UUID.randomUUID().toString())).andExpect(status().isOk());
        mvc.perform(aggregate(BOB, UUID.randomUUID().toString())).andExpect(status().isOk());

        mvc.perform(get(BASE).with(asCustomer(ALICE)).param("pageSize", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(greaterThan(0)))
                .andExpect(jsonPath("$.content[*].customerId").value(everyItem(is(ALICE))));
    }

    @Test
    void customerIdHeaderCannotImpersonateAnotherCustomer() throws Exception {
        mvc.perform(aggregate(BOB, UUID.randomUUID().toString())).andExpect(status().isOk());

        mvc.perform(get(BASE).with(asCustomer(ALICE)).header("X-Customer-Id", BOB).param("pageSize", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].customerId").value(everyItem(is(ALICE))));
    }

    @Test
    void anotherCustomersTransactionIsNotFound() throws Exception {
        mvc.perform(aggregate(BOB, UUID.randomUUID().toString())).andExpect(status().isOk());

        MvcResult bobsList = mvc.perform(get(BASE).with(asCustomer(BOB)).param("pageSize", "1"))
                .andExpect(status().isOk())
                .andReturn();
        String bobsTransactionId = JsonPath.read(bobsList.getResponse().getContentAsString(), "$.content[0].id");

        mvc.perform(get(BASE + "/" + bobsTransactionId).with(asCustomer(ALICE)))
                .andExpect(status().isNotFound());
    }

    private RequestBuilder aggregate(String customerId, String idempotencyKey) {
        return post(BASE + "/aggregate")
                .with(asCustomer(customerId))
                .header("Idempotency-Key", idempotencyKey)
                .accept(MediaType.APPLICATION_JSON);
    }

    private static RequestPostProcessor asCustomer(String customerId) {
        return jwt().jwt(token -> token.subject(customerId));
    }
}
