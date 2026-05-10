package com.dreamhomes.haven.common.web;

import com.dreamhomes.haven.support.AbstractPostgresIT;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Public observability surface end-to-end:
 * <ul>
 *   <li>{@code /actuator/health} is reachable without a JWT and reports UP when DB +
 *       (embedded) Kafka are healthy. Only top-level status leaks for anonymous probes
 *       — component details are gated by {@code show-details: when-authorized}.</li>
 *   <li>{@code /actuator/prometheus} is auth-gated (operator-only by design — scraping
 *       isn't a public action).</li>
 *   <li>{@code /v3/api-docs} returns the OpenAPI 3 spec, no JWT required.</li>
 *   <li>Every response carries an {@code X-Request-ID} stamped by RequestIdFilter; an
 *       inbound id is honoured.</li>
 * </ul>
 */
@AutoConfigureMockMvc
class ObservabilityIT extends AbstractPostgresIT {

    @Autowired MockMvc mockMvc;

    @Test
    void healthEndpointIsPublicAndReportsUp() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                // Anonymous probe — no component details should leak (show-details=when-authorized).
                .andExpect(jsonPath("$.components").doesNotExist());
    }

    @Test
    void prometheusEndpointIsAuthGated() throws Exception {
        mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void openApiDocsArePublicAndDescribeOurEndpoints() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                // The spec includes our actually-implemented routes — sanity-check a few.
                .andExpect(jsonPath("$.paths['/api/auth/login']", is(notNullValue())))
                .andExpect(jsonPath("$.paths['/api/listings']", is(notNullValue())))
                .andExpect(jsonPath("$.paths['/api/admin/verifications']", is(notNullValue())));
    }

    @Test
    void everyResponseStampsXRequestIdHeader() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(header().exists("X-Request-ID"))
                .andExpect(header().string("X-Request-ID", org.hamcrest.Matchers.matchesPattern(
                        "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")));
    }

    @Test
    void inboundXRequestIdHeaderIsHonouredAndEchoedBack() throws Exception {
        mockMvc.perform(get("/actuator/health").header("X-Request-ID", "vista-bug-report-42"))
                .andExpect(header().string("X-Request-ID", "vista-bug-report-42"));
    }

    @Test
    void healthShapeIsContentTypeJson() throws Exception {
        // Spring Boot Actuator emits its vendor JSON MIME — that's still JSON shape.
        mockMvc.perform(get("/actuator/health"))
                .andExpect(content().contentTypeCompatibleWith("application/vnd.spring-boot.actuator.v3+json"));
    }

    @Test
    void scalarHtmlIsPubliclyReachableAndPointsAtTheOpenApiSpec() throws Exception {
        // Scalar renderer is a static HTML file served alongside Swagger UI. The page
        // pulls the spec from /v3/api-docs at runtime — no JVM-side rendering, so this
        // test just confirms the page is reachable without auth and references the spec.
        mockMvc.perform(get("/scalar.html"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/html"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/v3/api-docs")));
    }
}
