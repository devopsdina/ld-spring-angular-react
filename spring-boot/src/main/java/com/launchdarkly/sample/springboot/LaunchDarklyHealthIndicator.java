package com.launchdarkly.sample.springboot;

import com.launchdarkly.sample.common.LaunchDarklyHealth;
import com.launchdarkly.sdk.server.LDClient;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Shows the LaunchDarkly connection at /actuator/health. It always reports UP; see
 * {@link LaunchDarklyHealth} for why.
 */
@Component("launchdarkly")
public class LaunchDarklyHealthIndicator implements HealthIndicator {

	private final LDClient ldClient;

	public LaunchDarklyHealthIndicator(LDClient ldClient) {
		this.ldClient = ldClient;
	}

	@Override
	public Health health() {
		return Health.up().withDetails(LaunchDarklyHealth.of(ldClient).details()).build();
	}

}
