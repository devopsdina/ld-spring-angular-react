package com.launchdarkly.sample.springboot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.launchdarkly.sample.common.HangingServer;
import com.launchdarkly.sdk.LDValue;
import com.launchdarkly.sdk.ObjectBuilder;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

// Boots the whole app with LaunchDarkly unreachable: it must start anyway and serve fallback values.
@SpringBootTest
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class SampleApplicationTest {

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

	private String getCamera() throws Exception {
		return mvc.perform(get("/api/product/2")
				.param("userKey", "abc123@gmail.com")
				.param("email", "abc123@gmail.com")
				.param("plan", "silver")
				.param("framework", "react")
				.header("X-Backend-Routing", "flag:FALLTHROUGH"))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
	}

	@Test
	void identifiesItselfWithTheVersionFromThePom() {
		assertThat(properties.applicationId()).isEqualTo("ld-spring-angular-react-spring-boot");
		assertThat(properties.applicationVersion()).isEqualTo("1.0.0");
	}

	@Test
	void returnsTheSameProductJsonAsQuarkus() throws Exception {
		LDValue response = LDValue.parse(getCamera());

		ObjectBuilder withoutBackend = LDValue.buildObject();
		for (String key : response.keys()) {
			if (!key.equals("backend")) {
				withoutBackend.put(key, response.get(key));
			}
		}
		LDValue contract = LDValue.parse(Files.readString(Path.of("..", "contracts", "product-offer.json"))).get("response");
		assertThat(withoutBackend.build()).isEqualTo(contract);
		assertThat(response.get("backend").get("id").stringValue()).isEqualTo("spring-boot");
		assertThat(response.get("backend").get("name").stringValue()).isEqualTo("Spring Boot");
		assertThat(response.get("backend").get("version").stringValue()).startsWith("4.");
	}

	@Test
	void logsEachRequestWithTheFrontendAndRoutingButNotTheEmail(CapturedOutput output) throws Exception {
		getCamera();

		assertThat(output.getOut())
				.contains("[spring-boot] product 2 requested by react (plan=silver), routed by api-backend (FALLTHROUGH)"
						+ " -> discount-pricing 0.0 (ERROR(CLIENT_NOT_READY))")
				.doesNotContain("abc123");
	}

	@Test
	void returns404ForAnUnknownProduct() throws Exception {
		mvc.perform(get("/api/product/99")).andExpect(status().isNotFound());
	}

	@Test
	void returns503ForTheBootstrapUntilTheSdkHasFlags() throws Exception {
		mvc.perform(get("/api/flags/bootstrap").param("email", "abc123@gmail.com").param("plan", "silver"))
				.andExpect(status().isServiceUnavailable())
				.andExpect(header().string("Cache-Control", "no-store"));
	}

	@Test
	void pointsBrowsersAtTheApiAndUis() throws Exception {
		mvc.perform(get("/"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.backend").value("spring-boot"))
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
