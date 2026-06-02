package com.printscheduler.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for {@link SimulationController}.
 *
 * <p>Starts the full Spring Boot application on a random port and tests every
 * REST endpoint via the standard Java {@link HttpClient} (no Spring-specific
 * HTTP client dependency needed). Covers success paths, state-machine guard
 * checks (409), validation rejections (400), and the full lifecycle.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("SimulationController – Integration")
class SimulationControllerIntegrationTest {

    @LocalServerPort
    int port;

    @Autowired
    ObjectMapper objectMapper;

    private final HttpClient http = HttpClient.newHttpClient();

    /** Valid minimal config payload. */
    private static final String VALID_CONFIG = """
        {
          "numUsers": 3,
          "numPrinters": 2,
          "queueCapacity": 10,
          "jobIntervalMs": 1000,
          "algorithm": "HYBRID",
          "colorJobRatio": 0.5,
          "smallJobPercentage": 0.3,
          "simulationSpeed": 1.0
        }
        """;

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private HttpResponse<String> get(String path) throws Exception {
        return http.send(
            HttpRequest.newBuilder(URI.create(url(path))).GET().build(),
            HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(String path, String jsonBody) throws Exception {
        HttpRequest.Builder req = HttpRequest.newBuilder(URI.create(url(path)))
            .header("Content-Type", "application/json");
        if (jsonBody != null) {
            req.POST(HttpRequest.BodyPublishers.ofString(jsonBody));
        } else {
            req.POST(HttpRequest.BodyPublishers.noBody());
        }
        return http.send(req.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> put(String path, String jsonBody) throws Exception {
        return http.send(
            HttpRequest.newBuilder(URI.create(url(path)))
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build(),
            HttpResponse.BodyHandlers.ofString());
    }

    private JsonNode tree(String body) throws Exception {
        return objectMapper.readTree(body);
    }

    @BeforeEach
    void reset() throws Exception {
        post("/api/simulation/reset", null);
    }

    // ── GET /api/health ───────────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/health returns 200 with status UP")
    void healthEndpoint() throws Exception {
        HttpResponse<String> resp = get("/api/health");
        assertThat(resp.statusCode()).isEqualTo(200);
        assertThat(tree(resp.body()).at("/data/status").asText()).isEqualTo("UP");
    }

    // ── POST /start ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("POST /start")
    class Start {

        @Test
        @DisplayName("202 when config is valid and sim is stopped")
        void startSuccess() throws Exception {
            HttpResponse<String> resp = post("/api/simulation/start", VALID_CONFIG);
            assertThat(resp.statusCode()).isEqualTo(202);
            JsonNode body = tree(resp.body());
            assertThat(body.at("/success").asBoolean()).isTrue();
            assertThat(body.at("/data/status").asText()).isEqualTo("RUNNING");
            assertThat(body.at("/data/algorithm").asText()).isEqualTo("HYBRID");
        }

        @Test
        @DisplayName("409 when simulation is already running")
        void startWhenAlreadyRunning() throws Exception {
            startSim();
            HttpResponse<String> resp = post("/api/simulation/start", VALID_CONFIG);
            assertThat(resp.statusCode()).isEqualTo(409);
            assertThat(tree(resp.body()).at("/error/code").asText())
                .isEqualTo("SIMULATION_ALREADY_RUNNING");
        }

        @Test
        @DisplayName("400 when numUsers exceeds maximum")
        void startValidationNumUsers() throws Exception {
            String bad = VALID_CONFIG.replace("\"numUsers\": 3", "\"numUsers\": 25");
            HttpResponse<String> resp = post("/api/simulation/start", bad);
            assertThat(resp.statusCode()).isEqualTo(400);
            assertThat(tree(resp.body()).at("/error/code").asText()).isEqualTo("VALIDATION_FAILED");
        }

        @Test
        @DisplayName("400 when algorithm is invalid")
        void startValidationAlgorithm() throws Exception {
            String bad = VALID_CONFIG.replace("\"HYBRID\"", "\"ROUND_ROBIN\"");
            HttpResponse<String> resp = post("/api/simulation/start", bad);
            assertThat(resp.statusCode()).isEqualTo(400);
        }

        @Test
        @DisplayName("400 when body is malformed JSON")
        void startMalformedBody() throws Exception {
            HttpResponse<String> resp = post("/api/simulation/start", "not-json");
            assertThat(resp.statusCode()).isEqualTo(400);
            assertThat(tree(resp.body()).at("/error/code").asText()).isEqualTo("MALFORMED_REQUEST");
        }

        @Test
        @DisplayName("400 when simulationSpeed is out of range")
        void startValidationSpeed() throws Exception {
            String bad = VALID_CONFIG.replace("\"simulationSpeed\": 1.0", "\"simulationSpeed\": 99.0");
            assertThat(post("/api/simulation/start", bad).statusCode()).isEqualTo(400);
        }

        @Test
        @DisplayName("400 when queueCapacity is below minimum")
        void startValidationQueueCapacity() throws Exception {
            String bad = VALID_CONFIG.replace("\"queueCapacity\": 10", "\"queueCapacity\": 1");
            assertThat(post("/api/simulation/start", bad).statusCode()).isEqualTo(400);
        }

        @Test
        @DisplayName("400 when numPrinters exceeds maximum")
        void startValidationNumPrinters() throws Exception {
            String bad = VALID_CONFIG.replace("\"numPrinters\": 2", "\"numPrinters\": 25");
            assertThat(post("/api/simulation/start", bad).statusCode()).isEqualTo(400);
        }
    }

    // ── POST /pause ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("POST /pause")
    class Pause {

        @Test
        @DisplayName("200 when simulation is running")
        void pauseSuccess() throws Exception {
            startSim();
            HttpResponse<String> resp = post("/api/simulation/pause", null);
            assertThat(resp.statusCode()).isEqualTo(200);
            assertThat(tree(resp.body()).at("/data/status").asText()).isEqualTo("PAUSED");
        }

        @Test
        @DisplayName("409 when simulation is not running")
        void pauseWhenStopped() throws Exception {
            HttpResponse<String> resp = post("/api/simulation/pause", null);
            assertThat(resp.statusCode()).isEqualTo(409);
            assertThat(tree(resp.body()).at("/error/code").asText())
                .isEqualTo("SIMULATION_NOT_RUNNING");
        }
    }

    // ── POST /resume ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("POST /resume")
    class Resume {

        @Test
        @DisplayName("200 when simulation is paused")
        void resumeSuccess() throws Exception {
            startSim();
            post("/api/simulation/pause", null);
            HttpResponse<String> resp = post("/api/simulation/resume", null);
            assertThat(resp.statusCode()).isEqualTo(200);
            assertThat(tree(resp.body()).at("/data/status").asText()).isEqualTo("RUNNING");
        }

        @Test
        @DisplayName("409 when simulation is not paused")
        void resumeWhenNotPaused() throws Exception {
            HttpResponse<String> resp = post("/api/simulation/resume", null);
            assertThat(resp.statusCode()).isEqualTo(409);
            assertThat(tree(resp.body()).at("/error/code").asText())
                .isEqualTo("SIMULATION_NOT_PAUSED");
        }
    }

    // ── POST /stop ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("POST /stop")
    class Stop {

        @Test
        @DisplayName("200 when running → stops")
        void stopSuccess() throws Exception {
            startSim();
            HttpResponse<String> resp = post("/api/simulation/stop", null);
            assertThat(resp.statusCode()).isEqualTo(200);
            assertThat(tree(resp.body()).at("/data/status").asText()).isEqualTo("STOPPED");
        }

        @Test
        @DisplayName("200 even when already stopped (idempotent)")
        void stopWhenAlreadyStopped() throws Exception {
            assertThat(post("/api/simulation/stop", null).statusCode()).isEqualTo(200);
        }
    }

    // ── POST /reset ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("POST /reset")
    class ResetTests {

        @Test
        @DisplayName("200 always, even from STOPPED state")
        void resetIdempotent() throws Exception {
            HttpResponse<String> resp = post("/api/simulation/reset", null);
            assertThat(resp.statusCode()).isEqualTo(200);
            assertThat(tree(resp.body()).at("/data/status").asText()).isEqualTo("STOPPED");
        }

        @Test
        @DisplayName("200 and STOPPED after reset from running")
        void resetFromRunning() throws Exception {
            startSim();
            HttpResponse<String> resp = post("/api/simulation/reset", null);
            assertThat(resp.statusCode()).isEqualTo(200);
            assertThat(tree(resp.body()).at("/data/status").asText()).isEqualTo("STOPPED");
        }
    }

    // ── PUT /configure ────────────────────────────────────────────────────

    @Nested
    @DisplayName("PUT /configure")
    class Configure {

        @Test
        @DisplayName("200 with algorithm change in appliedChanges")
        void configureAlgorithm() throws Exception {
            startSim();
            HttpResponse<String> resp = put("/api/simulation/configure", "{\"algorithm\": \"SJF\"}");
            assertThat(resp.statusCode()).isEqualTo(200);
            assertThat(tree(resp.body()).at("/data/appliedChanges/algorithm").asText())
                .isEqualTo("SJF");
        }

        @Test
        @DisplayName("200 with simulationSpeed change")
        void configureSpeed() throws Exception {
            startSim();
            HttpResponse<String> resp = put("/api/simulation/configure", "{\"simulationSpeed\": 2.5}");
            assertThat(resp.statusCode()).isEqualTo(200);
            assertThat(tree(resp.body()).at("/data/appliedChanges/simulationSpeed").asDouble())
                .isEqualTo(2.5);
        }

        @Test
        @DisplayName("200 with jobIntervalMs change")
        void configureJobInterval() throws Exception {
            startSim();
            HttpResponse<String> resp = put("/api/simulation/configure", "{\"jobIntervalMs\": 500}");
            assertThat(resp.statusCode()).isEqualTo(200);
            assertThat(tree(resp.body()).at("/data/appliedChanges/jobIntervalMs").asLong())
                .isEqualTo(500);
        }

        @Test
        @DisplayName("400 when algorithm value is invalid")
        void configureInvalidAlgorithm() throws Exception {
            startSim();
            assertThat(put("/api/simulation/configure", "{\"algorithm\": \"WEIRD\"}").statusCode())
                .isEqualTo(400);
        }

        @Test
        @DisplayName("400 when simulationSpeed is out of range")
        void configureInvalidSpeed() throws Exception {
            startSim();
            assertThat(put("/api/simulation/configure", "{\"simulationSpeed\": 99.0}").statusCode())
                .isEqualTo(400);
        }

        @Test
        @DisplayName("200 with empty body (no-op)")
        void configureEmptyBody() throws Exception {
            startSim();
            HttpResponse<String> resp = put("/api/simulation/configure", "{}");
            assertThat(resp.statusCode()).isEqualTo(200);
            assertThat(tree(resp.body()).at("/data/appliedChanges").isObject()).isTrue();
        }
    }

    // ── GET /state ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /state")
    class State {

        @Test
        @DisplayName("200 with STOPPED status when not running")
        void stateWhenStopped() throws Exception {
            HttpResponse<String> resp = get("/api/simulation/state");
            assertThat(resp.statusCode()).isEqualTo(200);
            assertThat(tree(resp.body()).at("/data/status").asText()).isEqualTo("STOPPED");
        }

        @Test
        @DisplayName("RUNNING after start")
        void stateWhenRunning() throws Exception {
            startSim();
            JsonNode body = tree(get("/api/simulation/state").body());
            assertThat(body.at("/data/status").asText()).isEqualTo("RUNNING");
            assertThat(body.at("/data/currentScheduler").asText()).isEqualTo("HYBRID");
        }

        @Test
        @DisplayName("PAUSED after pause")
        void stateWhenPaused() throws Exception {
            startSim();
            post("/api/simulation/pause", null);
            assertThat(tree(get("/api/simulation/state").body())
                .at("/data/status").asText()).isEqualTo("PAUSED");
        }

        @Test
        @DisplayName("response envelope has success=true and positive timestamp")
        void responseEnvelope() throws Exception {
            JsonNode root = tree(get("/api/simulation/state").body());
            assertThat(root.at("/success").asBoolean()).isTrue();
            assertThat(root.at("/timestamp").asLong()).isPositive();
        }
    }

    // ── GET /metrics ──────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /metrics returns 200")
    void metricsEndpoint() throws Exception {
        HttpResponse<String> resp = get("/api/simulation/metrics");
        assertThat(resp.statusCode()).isEqualTo(200);
        assertThat(tree(resp.body()).at("/success").asBoolean()).isTrue();
    }

    // ── GET /export ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /export")
    class Export {

        @Test
        @DisplayName("CSV export returns 200 with text/csv Content-Type")
        void exportCsv() throws Exception {
            HttpResponse<String> resp = get("/api/simulation/export?format=csv&type=jobs");
            assertThat(resp.statusCode()).isEqualTo(200);
            assertThat(resp.headers().firstValue("Content-Type").orElse(""))
                .startsWith("text/csv");
        }

        @Test
        @DisplayName("JSON export returns 200 with records array")
        void exportJson() throws Exception {
            HttpResponse<String> resp = get("/api/simulation/export?format=json&type=jobs");
            assertThat(resp.statusCode()).isEqualTo(200);
            assertThat(tree(resp.body()).at("/data/records").isArray()).isTrue();
        }

        @Test
        @DisplayName("400 when format is invalid")
        void exportInvalidFormat() throws Exception {
            HttpResponse<String> resp = get("/api/simulation/export?format=xml&type=jobs");
            assertThat(resp.statusCode()).isEqualTo(400);
            assertThat(tree(resp.body()).at("/error/code").asText()).isEqualTo("INVALID_FORMAT");
        }

        @Test
        @DisplayName("400 when type is invalid")
        void exportInvalidType() throws Exception {
            HttpResponse<String> resp = get("/api/simulation/export?format=json&type=logs");
            assertThat(resp.statusCode()).isEqualTo(400);
            assertThat(tree(resp.body()).at("/error/code").asText()).isEqualTo("INVALID_TYPE");
        }

        @Test
        @DisplayName("JSON metrics export is accepted")
        void exportMetricsJson() throws Exception {
            assertThat(get("/api/simulation/export?format=json&type=metrics").statusCode())
                .isEqualTo(200);
        }

        @Test
        @DisplayName("CSV metrics export is accepted")
        void exportMetricsCsv() throws Exception {
            assertThat(get("/api/simulation/export?format=csv&type=metrics").statusCode())
                .isEqualTo(200);
        }
    }

    // ── Full lifecycle ─────────────────────────────────────────────────────

    @Test
    @DisplayName("full lifecycle: start → pause → resume → stop → reset")
    void fullLifecycle() throws Exception {
        assertThat(post("/api/simulation/start", VALID_CONFIG).statusCode()).isEqualTo(202);
        assertThat(stateStatus()).isEqualTo("RUNNING");

        post("/api/simulation/pause", null);
        assertThat(stateStatus()).isEqualTo("PAUSED");

        post("/api/simulation/resume", null);
        assertThat(stateStatus()).isEqualTo("RUNNING");

        post("/api/simulation/stop", null);
        assertThat(stateStatus()).isEqualTo("STOPPED");

        assertThat(post("/api/simulation/reset", null).statusCode()).isEqualTo(200);
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private void startSim() throws Exception {
        post("/api/simulation/start", VALID_CONFIG);
    }

    private String stateStatus() throws Exception {
        return tree(get("/api/simulation/state").body()).at("/data/status").asText();
    }
}
