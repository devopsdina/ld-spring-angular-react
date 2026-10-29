package com.launchdarkly.sample.common;

import java.net.URI;
import java.time.Duration;

/**
 * The {@code launchdarkly.*} settings. Each backend reads them its own way (Spring's
 * {@code @ConfigurationProperties}, Quarkus's {@code @ConfigMapping}) and passes them here.
 *
 * @param sdkKey             the server-side SDK key for the environment (secret)
 * @param startWait          how long startup waits for LaunchDarkly before serving fallback values
 * @param relayProxyUri      optional Relay Proxy URL; when null the SDK connects to LaunchDarkly directly
 * @param applicationId      identifies this app to LaunchDarkly
 * @param applicationVersion this app's version, shown with the application ID
 */
public record LaunchDarklySettings(String sdkKey, Duration startWait, URI relayProxyUri, String applicationId,
		String applicationVersion) {

	/** LaunchDarkly recommends 1–5s for server-side SDKs. The SDK's own default is 5s. */
	public static final Duration DEFAULT_START_WAIT = Duration.ofSeconds(2);

	public LaunchDarklySettings {
		if (startWait == null) {
			startWait = DEFAULT_START_WAIT;
		}
	}

}
