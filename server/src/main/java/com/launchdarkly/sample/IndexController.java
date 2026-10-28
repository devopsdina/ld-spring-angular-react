package com.launchdarkly.sample;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** The server is API-only; this tells anyone who opens it in a browser where to go. */
@RestController
public class IndexController {

	@GetMapping(path = "/", produces = "application/json")
	public Map<String, Object> index() {
		return Map.of(
				"products", "/api/product/{id} (id 1-5)",
				"health", "/actuator/health",
				"ui", Map.of("angular", "http://localhost:4200", "react", "http://localhost:3000"));
	}

}
