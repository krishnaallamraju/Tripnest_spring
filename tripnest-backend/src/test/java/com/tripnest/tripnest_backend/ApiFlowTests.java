package com.tripnest.tripnest_backend;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import static org.junit.jupiter.api.Assertions.*;

@ActiveProfiles("dev")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {"CORS_ORIGIN=https://tripnest.example, https://preview.example", "spring.datasource.url=jdbc:h2:mem:flows"})
class ApiFlowTests {
    @Value("${local.server.port}") int port;
    final HttpClient client = HttpClient.newHttpClient();
    final ObjectMapper json = new ObjectMapper();

    HttpResponse<String> request(String method, String path, String body, String token) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
            .timeout(Duration.ofSeconds(10)).header("Content-Type", "application/json");
        if (token != null) builder.header("Authorization", "Bearer " + token);
        return client.send(builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }

    JsonNode ok(String method, String path, String body, String token) throws Exception {
        var response = request(method, path, body, token);
        assertTrue(response.statusCode() >= 200 && response.statusCode() < 300, path + ": " + response.statusCode() + " " + response.body());
        return response.body().isBlank() ? null : json.readTree(response.body());
    }

    @Test void healthAndCors() throws Exception {
        assertEquals("TripNest API", ok("GET", "/", null, null).get("service").asText());
        assertEquals("UP", ok("GET", "/health", null, null).get("status").asText());
        for (String origin : new String[]{"https://tripnest.example", "https://preview.example"}) {
            var response = client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/auth/login"))
                .header("Origin", origin).header("Access-Control-Request-Method", "POST")
                .method("OPTIONS", HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(200, response.statusCode());
            assertEquals(origin, response.headers().firstValue("Access-Control-Allow-Origin").orElse(""));
        }
        assertEquals(401, request("GET", "/trips/my", null, null).statusCode());
        assertEquals(401, request("GET", "/api/notifications", null, "expired-or-invalid-token").statusCode());
    }

    @Test void registerLoginAndTripBudgetExpenseFlow() throws Exception {
        String registration = "{\"name\":\"Flow Test\",\"email\":\"flow@example.test\",\"password\":\"test-password-123\"}";
        assertEquals(201, request("POST", "/api/auth/register", registration, null).statusCode());
        assertEquals(409, request("POST", "/api/auth/register", registration, null).statusCode());
        var rejected = request("POST", "/api/auth/login", "{\"email\":\"flow@example.test\",\"password\":\"wrong\"}", null);
        assertEquals(401, rejected.statusCode());
        assertTrue(rejected.body().contains("Invalid email or password"));
        String token = ok("POST", "/api/auth/login", registration, null).get("token").asText();
        assertFalse(token.isBlank());
        assertEquals("flow@example.test", ok("GET", "/users/me", null, token).get("email").asText());
        ok("GET", "/users/me/preferences", null, token);
        ok("GET", "/users/me/favorites", null, token);
        ok("GET", "/api/notifications", null, token);
        int destination = ok("GET", "/destinations", null, token).get(0).get("id").asInt();
        String tripBody = "{\"title\":\"Integration trip\",\"destinationId\":" + destination + ",\"startDate\":\"2027-01-10\",\"endDate\":\"2027-01-12\",\"status\":\"PLANNED\"}";
        int trip = ok("POST", "/trips", tripBody, token).get("id").asInt();
        assertEquals(1, ok("GET", "/trips/my", null, token).size());
        ok("GET", "/trips/" + trip, null, token);
        ok("POST", "/trips/" + trip + "/itineraries", "{\"dayNumber\":1,\"title\":\"Arrival\"}", token);
        ok("POST", "/api/budgets/" + trip, "{\"amount\":1000,\"currency\":\"INR\"}", token);
        int expense = ok("POST", "/api/expenses/trip/" + trip, "{\"category\":\"Food\",\"amount\":100,\"date\":\"2027-01-10\"}", token).get("id").asInt();
        assertEquals(900, ok("GET", "/api/expenses/trip/" + trip + "/remaining-budget", null, token).asInt());
        assertEquals(1, ok("GET", "/api/expenses/trip/" + trip, null, token).size());
        assertEquals(200, request("DELETE", "/api/expenses/" + expense, null, token).statusCode());
        ok("POST", "/api/expenses/trip/" + trip, "{\"category\":\"Food\",\"amount\":50,\"date\":\"2027-01-10\"}", token);
        assertEquals(200, request("DELETE", "/trips/" + trip, null, token).statusCode());
        assertEquals(0, ok("GET", "/trips/my", null, token).size());
    }
}
