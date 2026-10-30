package com.launchdarkly.sample.springboot;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

// The client setup itself is tested in launchdarkly-common; this checks Spring reads the settings.
class LaunchDarklyPropertiesTest {

	private static LaunchDarklyProperties bind(Map<String, String> values) {
		return new Binder(new MapConfigurationPropertySource(values)).bind("launchdarkly", LaunchDarklyProperties.class).get();
	}

	@Test
	void defaultsStartWaitToTwoSecondsWhenUnset() {
		assertThat(bind(Map.of("launchdarkly.sdk-key", "sdk-test-key")).startWait()).isEqualTo(Duration.ofSeconds(2));
	}

	@Test
	void passesEverySettingToTheSharedClientSetup() {
		var settings = bind(Map.of(
				"launchdarkly.sdk-key", "sdk-test-key",
				"launchdarkly.start-wait", "3s",
				"launchdarkly.relay-proxy-uri", "http://relay:8030",
				"launchdarkly.application-id", "app",
				"launchdarkly.application-version", "1.2.3")).toSettings();

		assertThat(settings.sdkKey()).isEqualTo("sdk-test-key");
		assertThat(settings.startWait()).isEqualTo(Duration.ofSeconds(3));
		assertThat(settings.relayProxyUri()).hasToString("http://relay:8030");
		assertThat(settings.applicationId()).isEqualTo("app");
		assertThat(settings.applicationVersion()).isEqualTo("1.2.3");
	}

}
