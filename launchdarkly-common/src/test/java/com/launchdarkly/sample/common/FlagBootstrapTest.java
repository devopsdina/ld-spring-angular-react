package com.launchdarkly.sample.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import com.launchdarkly.sdk.LDValue;
import com.launchdarkly.sdk.server.Components;
import com.launchdarkly.sdk.server.LDClient;
import com.launchdarkly.sdk.server.LDConfig;
import com.launchdarkly.sdk.server.integrations.FileData;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

// TestData can't mark a flag as available to client-side SDKs, so these flags are loaded from a
// file in LaunchDarkly's own flag format instead.
class FlagBootstrapTest {

	private static final String FLAGS = """
			{"flags": {
			  "api-backend": {
			    "key": "api-backend", "version": 1, "on": true,
			    "variations": ["spring-boot", "quarkus"], "offVariation": 0, "fallthrough": {"variation": 0},
			    "rules": [{"id": "gold", "variation": 1,
			      "clauses": [{"contextKind": "user", "attribute": "plan", "op": "in", "values": ["gold"]}]}],
			    "clientSide": true, "clientSideAvailability": {"usingEnvironmentId": true, "usingMobileKey": false}
			  },
			  "discount-pricing": {
			    "key": "discount-pricing", "version": 1, "on": true,
			    "variations": [0, 0.25], "offVariation": 0, "fallthrough": {"variation": 1},
			    "clientSide": false, "clientSideAvailability": {"usingEnvironmentId": false, "usingMobileKey": false}
			  }
			}}
			""";

	@TempDir
	Path dir;

	private LDClient client() throws Exception {
		Path file = Files.writeString(dir.resolve("flags.json"), FLAGS);
		return new LDClient("sdk-test-key", new LDConfig.Builder()
				.dataSource(FileData.dataSource().filePaths(file))
				.events(Components.noEvents())
				.build());
	}

	@Test
	void returnsTheContextWithItsClientSideFlagsAndReasons() throws Exception {
		try (LDClient client = client()) {
			LDValue body = LDValue.parse(new FlagBootstrap(client).forUser("you@example.com", "gold", "react").orElseThrow());

			LDValue context = body.get("context");
			assertThat(context.get("kind").stringValue()).isEqualTo("user");
			assertThat(context.get("key").stringValue()).isEqualTo("you@example.com");
			assertThat(context.get("plan").stringValue()).isEqualTo("gold");
			assertThat(context.get("framework").stringValue()).isEqualTo("react");

			LDValue flags = body.get("flags");
			assertThat(flags.get("api-backend").stringValue()).isEqualTo("quarkus");
			assertThat(flags.get("$valid").booleanValue()).isTrue();
			assertThat(flags.get("$flagsState").get("api-backend").get("reason").get("kind").stringValue())
					.isEqualTo("RULE_MATCH");
		}
	}

	@Test
	void neverSendsServerOnlyFlagsToTheBrowser() throws Exception {
		try (LDClient client = client()) {
			LDValue flags = LDValue.parse(new FlagBootstrap(client).forUser("you@example.com", "silver", "react").orElseThrow())
					.get("flags");

			assertThat(flags.get("api-backend").stringValue()).isEqualTo("spring-boot");
			assertThat(flags.get(ProductService.DISCOUNT_FLAG).isNull()).isTrue();
			assertThat(flags.get("$flagsState").get(ProductService.DISCOUNT_FLAG).isNull()).isTrue();
		}
	}

	@Test
	void returnsNothingBeforeTheSdkHasFlags() throws Exception {
		try (HangingServer launchDarkly = new HangingServer();
				LDClient client = LaunchDarklyClients.create(
						new LaunchDarklySettings("sdk-test-key", Duration.ofMillis(100), launchDarkly.uri(), null, null),
						new LDConfig.Builder().events(Components.noEvents()))) {
			assertThat(new FlagBootstrap(client).forUser("you@example.com", "gold", "react")).isEmpty();
		}
	}

}
