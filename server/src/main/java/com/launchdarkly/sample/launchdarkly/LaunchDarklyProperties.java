package com.launchdarkly.sample.launchdarkly;

import java.net.URI;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * The {@code launchdarkly.*} settings from application.yml.
 *
 * @param sdkKey             the server-side SDK key for the environment (secret)
 * @param startWait          how long startup waits for LaunchDarkly before serving fallback values
 * @param relayProxyUri      optional Relay Proxy URL; when unset the SDK connects to LaunchDarkly directly
 * @param applicationId      identifies this app to LaunchDarkly
 * @param applicationVersion this app's version, shown with the application ID
 */
@ConfigurationProperties("launchdarkly")
public record LaunchDarklyProperties(String sdkKey, @DefaultValue("2s") Duration startWait, URI relayProxyUri,
		String applicationId, String applicationVersion) {
}
