package com.launchdarkly.sample.quarkus;

import java.util.Map;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

/** The server is API-only; this tells anyone who opens it in a browser where to go. */
@Path("/")
public class IndexResource {

	@GET
	@Produces(MediaType.APPLICATION_JSON)
	public Map<String, Object> index() {
		return Map.of(
				"backend", "quarkus",
				"products", "/api/product/{id} (id 1-5)",
				"bootstrap", "/api/flags/bootstrap?email=&plan=&framework=",
				"health", "/q/health",
				"ui", Map.of("angular", "http://localhost:4200", "react", "http://localhost:3000"));
	}

}
