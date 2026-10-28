package com.launchdarkly.sample.launchdarkly;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import com.launchdarkly.sdk.LDContext;
import com.launchdarkly.sdk.server.Components;
import com.launchdarkly.sdk.server.LDClient;
import com.launchdarkly.sdk.server.LDConfig;
import com.launchdarkly.sdk.server.interfaces.DataSourceStatusProvider;
import com.launchdarkly.sdk.server.interfaces.DataSourceStatusProvider.State;
import com.launchdarkly.sdk.server.integrations.TestData;
import com.launchdarkly.sample.HangingServer;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

class LaunchDarklyConfigTest {

	private static LaunchDarklyProperties properties(Duration startWait, URI relayProxyUri) {
		return new LaunchDarklyProperties("sdk-test-key", startWait, relayProxyUri, "test-app", "1.2.3");
	}

	@Test
	void stopsWaitingAtStartWaitWhenLaunchDarklyNeverAnswers() throws Exception {
		try (HangingServer launchDarkly = new HangingServer()) {
			long start = System.nanoTime();
			try (LDClient client = LaunchDarklyConfig.createClient(properties(Duration.ofSeconds(1), launchDarkly.uri()),
					new LDConfig.Builder().events(Components.noEvents()))) {
				long elapsedMs = (System.nanoTime() - start) / 1_000_000;

				assertThat(client.isInitialized()).isFalse();
				assertThat(elapsedMs).isBetween(900L, 2_000L);
				// The app can still evaluate flags: it gets the fallback value.
				assertThat(client.boolVariation("any-flag", LDContext.create("user-1"), true))
						.isTrue();
			}
		}
	}

	@Test
	void sendsApplicationMetadataWithSdkRequests() throws Exception {
		try (HangingServer launchDarkly = new HangingServer();
				LDClient client = LaunchDarklyConfig.createClient(properties(Duration.ofSeconds(1), launchDarkly.uri()),
						new LDConfig.Builder().events(Components.noEvents()))) {
			assertThat(launchDarkly.headerLines())
					.anyMatch(line -> line.equalsIgnoreCase("X-LaunchDarkly-Tags: application-id/test-app application-version/1.2.3"));
		}
	}

	@Test
	void initializesWhenFlagsLoad() throws Exception {
		TestData flags = TestData.dataSource();
		flags.update(flags.flag("demo-flag").booleanFlag().variationForAll(true));

		try (LDClient client = LaunchDarklyConfig.createClient(properties(Duration.ofSeconds(2), null),
				new LDConfig.Builder().dataSource(flags).events(Components.noEvents()))) {
			assertThat(client.isInitialized()).isTrue();
		}
	}

	@Test
	void defaultsStartWaitToTwoSecondsWhenUnset() {
		LaunchDarklyProperties bound = new Binder(new MapConfigurationPropertySource(Map.of("launchdarkly.sdk-key", "sdk-test-key")))
				.bind("launchdarkly", LaunchDarklyProperties.class)
				.get();

		assertThat(bound.startWait()).isEqualTo(Duration.ofSeconds(2));
	}

	@Test
	void treatsOffAsAnErrorOnlyWhenAnErrorCausedIt() {
		Instant now = Instant.now();
		var unauthorized = DataSourceStatusProvider.ErrorInfo.fromHttpError(401);
		var oldError = new DataSourceStatusProvider.ErrorInfo(DataSourceStatusProvider.ErrorKind.NETWORK_ERROR, 0, "timeout",
				now.minusSeconds(60));

		assertThat(LaunchDarklyConfig.stoppedByError(new DataSourceStatusProvider.Status(State.OFF, now, unauthorized)))
				.isTrue();
		assertThat(LaunchDarklyConfig.stoppedByError(new DataSourceStatusProvider.Status(State.OFF, now, null))).isFalse();
		assertThat(LaunchDarklyConfig.stoppedByError(new DataSourceStatusProvider.Status(State.OFF, now, oldError)))
				.isFalse();
	}

	@Test
	void failsFastWithoutAnSdkKey() {
		assertThatThrownBy(() -> LaunchDarklyConfig.createClient(new LaunchDarklyProperties("", Duration.ofSeconds(1), null, null, null),
				new LDConfig.Builder()))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("LAUNCHDARKLY_SDK_KEY");
	}

}
