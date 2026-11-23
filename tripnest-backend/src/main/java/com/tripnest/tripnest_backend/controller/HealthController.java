package com.tripnest.tripnest_backend.controller;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {
    @GetMapping("/")
    public Map<String, String> index() {
        return Map.of("service", "TripNest API", "status", "UP", "health", "/health",
                "message", "This is the backend API. Open the frontend website to sign in and use TripNest.");
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "UP");
    }
}
