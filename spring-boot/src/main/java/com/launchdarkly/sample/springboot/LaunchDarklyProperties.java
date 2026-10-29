package com.launchdarkly.sample.springboot;

import java.net.URI;
import java.time.Duration;

import com.launchdarkly.sample.common.LaunchDarklySettings;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * The {@code launchdarkly.*} settings from application.yml, read the Spring way. See
 * {@link LaunchDarklySettings} for what each one does.
 */
@ConfigurationProperties("launchdarkly")
public record LaunchDarklyProperties(String sdkKey, @DefaultValue("2s") Duration startWait, URI relayProxyUri,
		String applicationId, String applicationVersion) {

	public LaunchDarklySettings toSettings() {
		return new LaunchDarklySettings(sdkKey, startWait, relayProxyUri, applicationId, applicationVersion);
	}

}
