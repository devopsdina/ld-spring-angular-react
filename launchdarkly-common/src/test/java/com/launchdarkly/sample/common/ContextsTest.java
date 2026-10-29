package com.launchdarkly.sample.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.launchdarkly.sdk.LDContext;
import com.launchdarkly.sdk.LDValue;
import com.launchdarkly.sdk.json.JsonSerialization;

import org.junit.jupiter.api.Test;

// Checks the contexts against contracts/contexts.json, which the Angular and React tests check too.
class ContextsTest {

	private static final Product CAMERA = new ProductCatalog().findById(2).orElseThrow();

	private static LDValue contract(String name) throws IOException {
		return LDValue.parse(Files.readString(Path.of("..", "contracts", "contexts.json"))).get(name);
	}

	private static LDValue json(LDContext context) {
		return LDValue.parse(JsonSerialization.serialize(context));
	}

	@Test
	void buildsTheSameUserContextAsTheBrowser() throws IOException {
		LDContext user = Contexts.user("abc123@gmail.com", "abc123@gmail.com", "silver", "react");

		assertThat(json(user)).isEqualTo(contract("users").get("react"));
	}

	@Test
	void buildsTheSameProductContextAsTheBrowser() throws IOException {
		assertThat(json(Contexts.product(CAMERA))).isEqualTo(contract("product"));
	}

	@Test
	void addsTheBackendToTheServerSideContext() throws IOException {
		LDContext server = Contexts.server("abc123@gmail.com", "abc123@gmail.com", "silver", "react",
				Backend.quarkus("3.40.1.1"), CAMERA);

		assertThat(json(server)).isEqualTo(contract("server"));
	}

	@Test
	void usesOneSharedKeyForAnonymousVisitors() {
		LDContext anonymous = Contexts.user(null, null, null, null);

		assertThat(anonymous.getKey()).isEqualTo(Contexts.ANONYMOUS_KEY);
		assertThat(anonymous.isAnonymous()).isTrue();
	}

	@Test
	void masksEmailsInLogs() {
		String logged = Contexts.forLog(Contexts.user("abc123@gmail.com", "abc123@gmail.com", "silver", "react"));

		assertThat(logged).doesNotContain("abc123").contains("***@gmail.com").contains("silver");
	}

}
