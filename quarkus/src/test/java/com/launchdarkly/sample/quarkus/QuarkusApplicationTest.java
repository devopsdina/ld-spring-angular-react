package com.launchdarkly.sample.quarkus;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import com.launchdarkly.sample.common.ProductService;
import com.launchdarkly.sdk.LDValue;
import com.launchdarkly.sdk.ObjectBuilder;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

// Boots the whole app with LaunchDarkly unreachable: it must start anyway and serve fallback values.
@QuarkusTest
@QuarkusTestResource(UnreachableLaunchDarkly.class)
class QuarkusApplicationTest {

	@Inject
	LaunchDarklyConfig config;

	private final List<String> logged = new CopyOnWriteArrayList<>();
	private final Handler capture = new Handler() {
		@Override
		public void publish(LogRecord record) {
			logged.add(record.getMessage());
		}

		@Override
		public void flush() {
		}

		@Override
		public void close() {
		}
	};

	@BeforeEach
	void captureLogs() {
		Logger.getLogger(ProductService.class.getName()).addHandler(capture);
	}

	@AfterEach
	void stopCapturingLogs() {
		Logger.getLogger(ProductService.class.getName()).removeHandler(capture);
	}

	private String getCamera() {
		return given()
				.queryParam("userKey", "abc123@gmail.com")
				.queryParam("email", "abc123@gmail.com")
				.queryParam("plan", "silver")
				.queryParam("framework", "react")
				.header("X-Backend-Routing", "flag:RULE_MATCH")
				.when().get("/api/product/2")
				.then().statusCode(200)
				.extract().asString();
	}

	@Test
	void identifiesItselfWithTheVersionFromThePom() {
		assertThat(config.applicationId()).contains("ld-spring-angular-react-quarkus");
		assertThat(config.applicationVersion()).contains("1.0.0");
	}

	@Test
	void returnsTheSameProductJsonAsSpringBoot() throws IOException {
		LDValue response = LDValue.parse(getCamera());

		ObjectBuilder withoutBackend = LDValue.buildObject();
		for (String key : response.keys()) {
			if (!key.equals("backend")) {
				withoutBackend.put(key, response.get(key));
			}
		}
		LDValue contract = LDValue.parse(Files.readString(Path.of("..", "contracts", "product-offer.json"))).get("response");
		assertThat(withoutBackend.build()).isEqualTo(contract);
		assertThat(response.get("backend").get("id").stringValue()).isEqualTo("quarkus");
		assertThat(response.get("backend").get("name").stringValue()).isEqualTo("Quarkus");
		assertThat(response.get("backend").get("version").stringValue()).isEqualTo("3.40.1.1");
	}

	@Test
	void logsEachRequestWithTheFrontendAndRoutingButNotTheEmail() {
		getCamera();

		assertThat(logged).contains("[quarkus] product 2 requested by react (plan=silver), routed by api-backend (RULE_MATCH)"
				+ " -> discount-pricing 0.0 (ERROR(CLIENT_NOT_READY))");
		assertThat(logged).noneMatch(line -> line.contains("abc123"));
	}

	@Test
	void returns404ForAnUnknownProduct() {
		given().when().get("/api/product/99").then().statusCode(404);
	}

	@Test
	void returns503ForTheBootstrapUntilTheSdkHasFlags() {
		given().queryParam("email", "abc123@gmail.com").queryParam("plan", "silver")
				.when().get("/api/flags/bootstrap")
				.then().statusCode(503).header("Cache-Control", "no-store");
	}

	@Test
	void pointsBrowsersAtTheApiAndUis() {
		given().when().get("/").then().statusCode(200)
				.body("backend", equalTo("quarkus"))
				.body("ui.angular", equalTo("http://localhost:4200"));
	}

	@Test
	void reportsTheConnectionStateWithoutFailingHealth() {
		given().when().get("/q/health").then().statusCode(200)
				.body("status", equalTo("UP"))
				.body("checks.name", hasItem("launchdarkly"))
				.body("checks.find { it.name == 'launchdarkly' }.data.initialized", equalTo(false));
	}

}
