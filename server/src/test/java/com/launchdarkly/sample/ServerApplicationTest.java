package com.launchdarkly.sample;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;

import com.launchdarkly.sample.launchdarkly.LaunchDarklyProperties;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

// Boots the whole app with LaunchDarkly unreachable: it must start anyway and serve fallback values.
@SpringBootTest
@AutoConfigureMockMvc
class ServerApplicationTest {

	private static final HangingServer launchDarkly;

	static {
		try {
			launchDarkly = new HangingServer();
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}

	@DynamicPropertySource
	static void launchDarklyProperties(DynamicPropertyRegistry registry) {
		registry.add("launchdarkly.sdk-key", () -> "sdk-test-key");
		registry.add("launchdarkly.start-wait", () -> "1s");
		registry.add("launchdarkly.relay-proxy-uri", () -> launchDarkly.uri().toString());
	}

	@AfterAll
	static void stopLaunchDarkly() throws IOException {
		launchDarkly.close();
	}

	@Autowired
	private MockMvc mvc;

	@Autowired
	private LaunchDarklyProperties properties;

	@Test
	void identifiesItselfWithTheVersionFromThePom() {
		assertThat(properties.applicationId()).isEqualTo("ld-spring-angular-react-server");
		assertThat(properties.applicationVersion()).isEqualTo("1.0.0");
	}

	@Test
	void servesFullPriceWhileLaunchDarklyIsUnreachable() throws Exception {
		mvc.perform(get("/api/product/2").param("userKey", "user-1").param("plan", "gold"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("Camera"))
				.andExpect(jsonPath("$.price").value(600.00))
				.andExpect(jsonPath("$.discountReason").value("ERROR(CLIENT_NOT_READY)"));
	}

	@Test
	void pointsBrowsersAtTheApiAndUis() throws Exception {
		mvc.perform(get("/"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.products").value("/api/product/{id} (id 1-5)"))
				.andExpect(jsonPath("$.ui.angular").value("http://localhost:4200"));
	}

	@Test
	void reportsTheConnectionStateWithoutFailingHealth() throws Exception {
		mvc.perform(get("/actuator/health"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("UP"))
				.andExpect(jsonPath("$.components.launchdarkly.details.initialized").value(false));
	}

}
