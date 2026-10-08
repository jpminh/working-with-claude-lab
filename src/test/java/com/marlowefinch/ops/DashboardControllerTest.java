package com.marlowefinch.ops;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** The JSON API through MockMvc, plus one real HTTP call to observe the rendered 400. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("demo")
class DashboardControllerTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private TestRestTemplate http;

    private static final String FROM_FORMAT = "from must be an ISO date (YYYY-MM-DD)";
    private static final String TO_FORMAT = "to must be an ISO date (YYYY-MM-DD)";
    private static final String ORDER = "from must be on or before to";
    private static final String SPAN = "the range may span at most 366 days";
    private static final String LIMIT = "limit must be an integer between 1 and 500";

    /** Every endpoint that takes from/to (TODO-232 AC-4). */
    private static final String[] ENDPOINTS = {
        "/api/kpis", "/api/deliveries/on-time", "/api/deliveries/late", "/api/tickets/by-category"
    };

    @Test
    void healthReportsUpAndTheFixedToday() throws Exception {
        mvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.today").value("2026-09-21"));
    }

    @Test
    void kpisDefaultToTheLast30DaysEndingToday() throws Exception {
        mvc.perform(get("/api/kpis"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.from").value("2026-08-22"))
                .andExpect(jsonPath("$.to").value("2026-09-21"))
                .andExpect(jsonPath("$.onTimeRate").value(0.937))
                .andExpect(jsonPath("$.openTickets").value(114))
                .andExpect(jsonPath("$.revenue").value(360095.5))
                .andExpect(jsonPath("$.orders").value(624));
    }

    @Test
    void kpisAcceptAnExplicitRange() throws Exception {
        mvc.perform(get("/api/kpis").param("from", "2026-07-01").param("to", "2026-07-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.from").value("2026-07-01"))
                .andExpect(jsonPath("$.to").value("2026-07-31"))
                .andExpect(jsonPath("$.orders").value(679))
                .andExpect(jsonPath("$.revenue").value(480209.5));
    }

    @Test
    void onTimeReturnsOneRowPerCarrier() throws Exception {
        mvc.perform(get("/api/deliveries/on-time"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(4)))
                .andExpect(jsonPath("$[1].carrier").value("Kessler Logistics"))
                .andExpect(jsonPath("$[1].delivered").value(265))
                .andExpect(jsonPath("$[1].onTime").value(238))
                .andExpect(jsonPath("$[1].rate").value(0.8981));
    }

    @Test
    void lateReturnsOrderCarrierDatesAndDaysLateAndRespectsTheLimit() throws Exception {
        mvc.perform(get("/api/deliveries/late").param("from", "2026-09-14").param("to", "2026-09-21").param("limit", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].orderRef").isString())
                .andExpect(jsonPath("$[0].carrier").isString())
                .andExpect(jsonPath("$[0].promisedDate").isString())
                .andExpect(jsonPath("$[0].deliveredDate").isString())
                .andExpect(jsonPath("$[0].daysLate").isNumber());
    }

    /** Was "returns an empty list": a reversed range now fails AC-2 instead of looking like a quiet week. */
    @Test
    void lateWithFromAfterToIsRejectedWithA400() throws Exception {
        mvc.perform(get("/api/deliveries/late").param("from", "2026-09-21").param("to", "2026-09-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors", hasSize(1)))
                .andExpect(jsonPath("$.errors[0]").value(ORDER));
    }

    @Test
    void ticketsByCategoryReturnsOpenAndTotalPerCategory() throws Exception {
        mvc.perform(get("/api/tickets/by-category"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(5)))
                .andExpect(jsonPath("$[0].category").value("Delivery delay"))
                .andExpect(jsonPath("$[0].open").value(41))
                .andExpect(jsonPath("$[0].total").value(90));
    }

    @Test
    void vendorsIncludeDaysUntilContractEndAndTheNoticeWindowFlag() throws Exception {
        mvc.perform(get("/api/vendors"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(8)))
                .andExpect(jsonPath("$[0].name").value("Volta Parts GmbH"))
                .andExpect(jsonPath("$[0].contractEnd").value("2026-10-15"))
                .andExpect(jsonPath("$[0].noticeDays").value(30))
                .andExpect(jsonPath("$[0].daysUntilContractEnd").value(24))
                .andExpect(jsonPath("$[0].inNoticeWindow").value(true))
                .andExpect(jsonPath("$[7].inNoticeWindow").value(false));
    }

    /**
     * TODO-232: query parameters are validated, so a malformed date is a 400 with an errors
     * list rather than a 500. (A real HTTP call is kept here so the rendered error response,
     * not just the MockMvc result, is observed.)
     */
    @Test
    void malformedFromReturnsA400WithAnErrorsBody() {
        ResponseEntity<String> response = http.getForEntity("/api/kpis?from=next-tuesday", String.class);
        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getHeaders().getContentType().toString()).startsWith("application/json");
        assertThat(response.getBody()).isEqualTo("{\"errors\":[\"" + FROM_FORMAT + "\"]}");
    }

    // --- TODO-232 AC-1: ISO dates ---

    @Test
    void malformedToIsRejectedWithA400() throws Exception {
        mvc.perform(get("/api/kpis").param("to", "21/09/2026"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors", hasSize(1)))
                .andExpect(jsonPath("$.errors[0]").value(TO_FORMAT));
    }

    @Test
    void anImpossibleCalendarDateIsRejectedWithA400() throws Exception {
        mvc.perform(get("/api/kpis").param("from", "2026-02-30"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0]").value(FROM_FORMAT));
    }

    @Test
    void malformedFromAndToAreBothReported() throws Exception {
        mvc.perform(get("/api/kpis").param("from", "next-tuesday").param("to", "someday"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors", hasSize(2)))
                .andExpect(jsonPath("$.errors[0]").value(FROM_FORMAT))
                .andExpect(jsonPath("$.errors[1]").value(TO_FORMAT));
    }

    // --- TODO-232 AC-2: order and span ---

    @Test
    void fromAfterToIsRejectedWithA400() throws Exception {
        mvc.perform(get("/api/kpis").param("from", "2026-09-02").param("to", "2026-09-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors", hasSize(1)))
                .andExpect(jsonPath("$.errors[0]").value(ORDER));
    }

    @Test
    void fromEqualToToIsAccepted() throws Exception {
        mvc.perform(get("/api/kpis").param("from", "2026-09-01").param("to", "2026-09-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.from").value("2026-09-01"))
                .andExpect(jsonPath("$.to").value("2026-09-01"));
    }

    @Test
    void fromAfterTheDefaultToIsRejected() throws Exception {
        // to defaults to today (2026-09-21)
        mvc.perform(get("/api/kpis").param("from", "2026-09-22"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0]").value(ORDER));
    }

    @Test
    void a367DayRangeIsRejectedWithA400() throws Exception {
        mvc.perform(get("/api/kpis").param("from", "2025-09-19").param("to", "2026-09-21"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors", hasSize(1)))
                .andExpect(jsonPath("$.errors[0]").value(SPAN));
    }

    @Test
    void a366DayRangeIsAccepted() throws Exception {
        mvc.perform(get("/api/kpis").param("from", "2025-09-20").param("to", "2026-09-21"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.from").value("2025-09-20"))
                .andExpect(jsonPath("$.to").value("2026-09-21"));
    }

    @Test
    void theSpanLimitAlsoAppliesAgainstTheDefaultTo() throws Exception {
        mvc.perform(get("/api/kpis").param("from", "2025-09-19"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0]").value(SPAN));
    }

    // --- TODO-232 AC-3: limit ---

    @Test
    void limitZeroIsRejectedWithA400() throws Exception {
        mvc.perform(get("/api/deliveries/late").param("limit", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors", hasSize(1)))
                .andExpect(jsonPath("$.errors[0]").value(LIMIT));
    }

    @Test
    void negativeLimitIsRejectedWithA400() throws Exception {
        mvc.perform(get("/api/deliveries/late").param("limit", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0]").value(LIMIT));
    }

    @Test
    void limit501IsRejectedWithA400() throws Exception {
        mvc.perform(get("/api/deliveries/late").param("limit", "501"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors", hasSize(1)))
                .andExpect(jsonPath("$.errors[0]").value(LIMIT));
    }

    @Test
    void nonNumericLimitIsRejectedWithA400() throws Exception {
        for (String bad : new String[] {"lots", "2.5", "99999999999"}) {
            mvc.perform(get("/api/deliveries/late").param("limit", bad))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors", hasSize(1)))
                    .andExpect(jsonPath("$.errors[0]").value(LIMIT));
        }
    }

    @Test
    void limitsOneAnd500AreAccepted() throws Exception {
        mvc.perform(get("/api/deliveries/late").param("from", "2026-01-01").param("limit", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
        mvc.perform(get("/api/deliveries/late").param("from", "2026-01-01").param("limit", "500"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(lessThanOrEqualTo(500))))
                .andExpect(jsonPath("$", hasSize(greaterThan(20))));
    }

    @Test
    void lateDefaultsToALimitOf20AndTheLast30Days() throws Exception {
        String explicit = mvc.perform(get("/api/deliveries/late")
                        .param("from", "2026-08-22").param("to", "2026-09-21").param("limit", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(20)))
                .andReturn().getResponse().getContentAsString();
        String defaulted = mvc.perform(get("/api/deliveries/late"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(defaulted).isEqualTo(explicit);
    }

    // --- TODO-232 AC-4: several errors, every endpoint ---

    @Test
    void severalProblemsProduceSeveralErrorsInOrder() throws Exception {
        mvc.perform(get("/api/deliveries/late").param("from", "next-tuesday").param("limit", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors", hasSize(2)))
                .andExpect(jsonPath("$.errors[0]").value(FROM_FORMAT))
                .andExpect(jsonPath("$.errors[1]").value(LIMIT));
    }

    @Test
    void badFromBadToAndBadLimitProduceThreeErrors() throws Exception {
        mvc.perform(get("/api/deliveries/late").param("from", "x").param("to", "y").param("limit", "501"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors", hasSize(3)))
                .andExpect(jsonPath("$.errors[0]").value(FROM_FORMAT))
                .andExpect(jsonPath("$.errors[1]").value(TO_FORMAT))
                .andExpect(jsonPath("$.errors[2]").value(LIMIT));
    }

    @Test
    void reversedRangeAndBadLimitProduceTwoErrors() throws Exception {
        mvc.perform(get("/api/deliveries/late")
                        .param("from", "2026-09-21").param("to", "2026-09-01").param("limit", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors", hasSize(2)))
                .andExpect(jsonPath("$.errors[0]").value(ORDER))
                .andExpect(jsonPath("$.errors[1]").value(LIMIT));
    }

    @Test
    void malformedFromIsRejectedOnEveryEndpoint() throws Exception {
        for (String path : ENDPOINTS) {
            mvc.perform(get(path).param("from", "next-tuesday"))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.errors", hasSize(1)))
                    .andExpect(jsonPath("$.errors[0]").value(FROM_FORMAT));
        }
    }

    @Test
    void malformedToReversedAndOverlongRangesAreRejectedOnEveryEndpoint() throws Exception {
        for (String path : ENDPOINTS) {
            mvc.perform(get(path).param("to", "tomorrow"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0]").value(TO_FORMAT));
            mvc.perform(get(path).param("from", "2026-09-21").param("to", "2026-09-01"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0]").value(ORDER));
            mvc.perform(get(path).param("from", "2025-09-19").param("to", "2026-09-21"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0]").value(SPAN));
        }
    }

    @Test
    void aValidRequestStillReturns200OnEveryEndpoint() throws Exception {
        for (String path : ENDPOINTS) {
            mvc.perform(get(path).param("from", "2026-07-01").param("to", "2026-07-31"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.errors").doesNotExist());
        }
    }

    @Test
    void defaultsStillApplyWhenParametersAreMissingOrBlank() throws Exception {
        for (String path : ENDPOINTS) {
            mvc.perform(get(path)).andExpect(status().isOk());
        }
        // only one bound given: the other one defaults
        mvc.perform(get("/api/kpis").param("from", "2026-09-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.from").value("2026-09-01"))
                .andExpect(jsonPath("$.to").value("2026-09-21"));
        mvc.perform(get("/api/kpis").param("to", "2026-09-10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.from").value("2026-08-22"))
                .andExpect(jsonPath("$.to").value("2026-09-10"));
        // blank counts as missing
        mvc.perform(get("/api/kpis").param("from", "").param("to", ""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.from").value("2026-08-22"))
                .andExpect(jsonPath("$.to").value("2026-09-21"));
    }
}
