package com.launchdarkly.sample.quarkus;

import java.net.URI;
import java.time.Duration;
import java.util.Optional;

import com.launchdarkly.sample.common.LaunchDarklySettings;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

/**
 * The {@code launchdarkly.*} settings from application.properties, read the Quarkus way. See
 * {@link LaunchDarklySettings} for what each one does.
 */
@ConfigMapping(prefix = "launchdarkly")
public interface LaunchDarklyConfig {

	// Optional so a missing key reaches LaunchDarklyClients, whose error says how to set it.
	Optional<String> sdkKey();

	@WithDefault("2s")
	Duration startWait();

	Optional<URI> relayProxyUri();

	Optional<String> applicationId();

	Optional<String> applicationVersion();

	default LaunchDarklySettings toSettings() {
		return new LaunchDarklySettings(sdkKey().orElse(null), startWait(), relayProxyUri().orElse(null),
				applicationId().orElse(null), applicationVersion().orElse(null));
	}

}
