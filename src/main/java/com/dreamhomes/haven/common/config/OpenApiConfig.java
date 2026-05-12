package com.dreamhomes.haven.common.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.tags.Tag;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.ProblemDetail;

import java.util.List;
import java.util.Map;

@Configuration
public class OpenApiConfig {

    private static final String BEARER_SCHEME = "bearerAuth";

    @Value("${haven.errors.type-base:https://github.com/DreamHom/haven/blob/main/docs/errors/}")
    private String errorTypeBase;

    @Bean
    OpenAPI dreamhomesOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("DreamHomes Haven API")
                        .version("v1")
                        .description("""
                                The secure backend powering DreamHomes — the platform that lets
                                Nigerian property owners, agents, and applicants transact with
                                trust they couldn't get on a WhatsApp group or a wall flyer.

                                **Highlights**

                                - Stateless JWT auth with token revocation via `tokenVersion`
                                - 4-track verification (owner identity, applicant identity, agent
                                  credential, property documents) with admin moderation
                                - Transactional outbox over Kafka so notifications never go missing
                                - Public discovery + Cache-Control headers, paginated browse,
                                  rate-limited auth, request-id correlation across the stack

                                See [`docs/users/`](https://github.com/DreamHom/haven/tree/main/docs/users)
                                for the personas these endpoints serve.
                                """)
                        .contact(new Contact()
                                .name("DreamHomes Haven team")
                                .url("https://github.com/DreamHom/haven"))
                        .license(new License()
                                .name("MIT")
                                .url("https://github.com/DreamHom/haven/blob/main/LICENSE")))
                .servers(List.of(
                        new Server().url("http://localhost:8080/api").description("Local dev")
                ))
                .tags(List.of(
                        new Tag().name("Auth").description("Register, log in, and identify the current user."),
                        new Tag().name("Users").description("Public profiles, agent badges, and the user-facing view of the trust signals admins manage."),
                        new Tag().name("Properties").description("The physical asset — what the listing is *of*. Owned by a single user; one property may back many listings over time."),
                        new Tag().name("Listings").description("The thing applicants browse, save, comment on, request inspections for, and submit offers against."),
                        new Tag().name("Listing photos").description("Owner / assigned-agent uploads that drive the visual hero of a listing."),
                        new Tag().name("Inspections").description("Slots an owner / agent opens, and inspection requests an applicant claims against them."),
                        new Tag().name("Offers").description("Applicant-submitted bids on a listing, including counter-offer chains and accept/decline state machine."),
                        new Tag().name("Comments").description("Public Q&A on listings — anyone authenticated can post, owner / agent / commenter can soft-delete."),
                        new Tag().name("Reviews").description("Post-deal reviews (rating + text) on the user the deal closed with. Gates on a CLOSED listing + ACCEPTED offer."),
                        new Tag().name("Saves").description("Applicant-side bookmarks on listings, surfaced as a personal collection and aggregated as engagement signal on the listing."),
                        new Tag().name("Agent assignments").description("Owner ↔ agent handshake (REQUESTED → ACCEPTED / DECLINED / REVOKED) so an agent can act on a listing on behalf of an owner."),
                        new Tag().name("Verifications").description("Identity / credential / property document submissions that admins approve or reject; approvals stamp badges on the user / property."),
                        new Tag().name("Notifications").description("Per-user inbox: inspection requests, offers received, verification decisions, and other event-driven nudges."),
                        new Tag().name("Admin").description("Moderation surface: user suspension, listing takedown, verification decisions, audit log."),
                        new Tag().name("Observability").description("Health, info, metrics — actuator endpoints exposed to platform tooling.")
                ))
                .components(new Components()
                        .addSecuritySchemes(BEARER_SCHEME, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("JWT issued by `POST /auth/login`. Send as `Authorization: Bearer <token>`."))
             
                        .addResponses("Unauthenticated", problemResponse(
                                "Missing, expired, or revoked JWT.",
                                "Unauthenticated",
                                "{\"type\":\"" + errorTypeBase + "unauthenticated\"," +
                                        "\"title\":\"Unauthorized\",\"status\":401," +
                                        "\"detail\":\"unauthenticated\"," +
                                        "\"instance\":\"/api/<request-path>\"}"))
                        .addResponses("Forbidden", problemResponse(
                                "Caller authenticated but lacks the required role, ownership, or assignment.",
                                "Forbidden",
                                "{\"type\":\"" + errorTypeBase + "forbidden\"," +
                                        "\"title\":\"Forbidden\",\"status\":403," +
                                        "\"detail\":\"forbidden\"," +
                                        "\"instance\":\"/api/<request-path>\"}"))
                        .addResponses("NotFound", problemResponse(
                                "Target resource does not exist (or is no longer visible to the caller).",
                                "NotFound",
                                "{\"type\":\"" + errorTypeBase + "not-found\"," +
                                        "\"title\":\"Not Found\",\"status\":404," +
                                        "\"detail\":\"User 999999 was not found\"," +
                                        "\"instance\":\"/api/users/999999/profile\"}"))
                        .addResponses("Conflict", problemResponse(
                                "Request collides with current state — illegal state transition, " +
                                        "duplicate row, or optimistic-lock contention.",
                                "Conflict",
                                "{\"type\":\"" + errorTypeBase + "conflict\"," +
                                        "\"title\":\"Conflict\",\"status\":409," +
                                        "\"detail\":\"resource state prevents this operation\"," +
                                        "\"instance\":\"/api/<request-path>\"}"))
                        .addResponses("ValidationFailed", problemResponse(
                                "Request body or query parameter failed jakarta.validation constraints.",
                                "ValidationFailed",
                                "{\"type\":\"" + errorTypeBase + "validation-failed\"," +
                                        "\"title\":\"Bad Request\",\"status\":400," +
                                        "\"detail\":\"validation failed\"," +
                                        "\"instance\":\"/api/<request-path>\"," +
                                        "\"errors\":[{\"field\":\"email\",\"message\":\"must be a well-formed email\"}]}"))
                        .addResponses("RateLimited", problemResponse(
                                "Per-IP token bucket exhausted on a rate-limited path " +
                                        "(currently `POST /auth/register` and `POST /auth/login`).",
                                "RateLimited",
                                "{\"type\":\"" + errorTypeBase + "rate-limited\"," +
                                        "\"title\":\"Too Many Requests\",\"status\":429," +
                                        "\"detail\":\"rate limit exceeded\"," +
                                        "\"instance\":\"/api/auth/login\"}")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
    }

    private static ApiResponse problemResponse(String description,
                                               String exampleName, String exampleJson) {
        return new ApiResponse()
                .description(description)
                .content(new Content().addMediaType("application/json",
                        new MediaType()
                                .schema(new Schema<ProblemDetail>().$ref(
                                        "#/components/schemas/ProblemDetail"))
                                .addExamples(exampleName, new Example().value(exampleJson))));
    }


    @Bean
    OpenApiCustomizer stripApiPrefix() {
        return openApi -> {
            if (openApi.getPaths() == null) return;
            Paths newPaths = new Paths();
            openApi.getPaths().forEach((path, item) -> {
                String stripped = path.startsWith("/api/") ? path.substring(4)
                        : "/api".equals(path) ? "/" : path;
                newPaths.addPathItem(stripped, item);
            });
            openApi.setPaths(newPaths);
        };
    }


    @Bean
    OperationCustomizer reTagActuatorOperations() {
        return (operation, handlerMethod) -> {
            List<String> tags = operation.getTags();
            if (tags == null) return operation;
            if (!tags.removeIf("actuator"::equalsIgnoreCase)) return operation;
            if (!tags.contains("Observability")) tags.add("Observability");
            operation.setTags(tags);
            return operation;
        };
    }

    @Bean
    OpenApiCustomizer enrichActuatorOperations() {
        Map<String, ActuatorDoc> docs = Map.of(
                "/actuator", new ActuatorDoc(
                        "Discover available actuator endpoints",
                        """
                        HAL+JSON discovery doc listing every actuator endpoint exposed by \
                        this app — health, info, prometheus, etc. Useful for tooling that \
                        introspects the management surface before deciding what to call.
                        """),
                "/actuator/health", new ActuatorDoc(
                        "Liveness + readiness probe",
                        """
                        Returns `{ "status": "UP" }` when the app is running and downstream \
                        dependencies (Postgres, Kafka) are reachable. Anonymous in this \
                        deployment — exposed for load balancers, container orchestrators, \
                        and uptime monitors. A non-200 response means rolling traffic away \
                        from this instance is appropriate.

                        For per-component drill-down (e.g. just the database health), use \
                        `GET /actuator/health/{component}`.
                        """),
                "/actuator/health/**", new ActuatorDoc(
                        "Per-component health drill-down",
                        """
                        Returns the health status of a single component, e.g. \
                        `GET /actuator/health/db` for the datasource only. Useful when the \
                        aggregate `/actuator/health` is DOWN and you want to know which \
                        dependency is the cause without parsing the full breakdown.
                        """),
                "/actuator/info", new ActuatorDoc(
                        "Build + environment metadata",
                        """
                        Static info bundle (build version, git commit, active Spring profile). \
                        Useful for confirming which version is deployed where, especially \
                        during incident triage. Auth-gated to avoid leaking build metadata \
                        publicly.
                        """),
                "/actuator/prometheus", new ActuatorDoc(
                        "Prometheus metrics scrape",
                        """
                        Exposes Micrometer metrics in Prometheus' text exposition format. \
                        Includes:

                        - `haven.outbox.unpublished` — depth of the transactional outbox \
                          waiting to ship to Kafka. **Alert when non-zero for more than \
                          a tick or two** — that signals the relay is failing or backed up.
                        - Standard JVM metrics (heap, GC, threads).
                        - Spring HTTP server metrics (request latency, error rate per \
                          endpoint via `http.server.requests`).
                        - Hikari connection pool metrics.

                        Auth-gated. Configure your Prometheus scraper with a service-account \
                        token so polling doesn't expose the endpoint publicly.
                        """)
        );

        return openApi -> {
            if (openApi.getPaths() == null) return;
            docs.forEach((path, doc) -> {
                PathItem item = openApi.getPaths().get(path);
                if (item == null) return;
                Operation op = item.getGet();
                if (op == null) return;
                op.setSummary(doc.summary());
                op.setDescription(doc.description());
            });
        };
    }

    @Bean
    OpenApiCustomizer dropEmptyActuatorTopLevelTag() {
        return openApi -> {
            if (openApi.getTags() == null) return;
            openApi.getTags().removeIf(t -> "Actuator".equalsIgnoreCase(t.getName()));
        };
    }

    private record ActuatorDoc(String summary, String description) {}
}
