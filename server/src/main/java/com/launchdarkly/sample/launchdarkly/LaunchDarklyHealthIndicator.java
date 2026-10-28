package com.launchdarkly.sample.launchdarkly;

import com.launchdarkly.sdk.server.LDClient;
import com.launchdarkly.sdk.server.interfaces.DataSourceStatusProvider;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Shows the LaunchDarkly connection at /actuator/health. It always reports UP: the app keeps working
 * on fallback or last known values while LaunchDarkly is unreachable, and restarting the app
 * wouldn't fix the connection. Use the {@code state} detail for dashboards and alerts instead.
 */
@Component("launchdarkly")
public class LaunchDarklyHealthIndicator implements HealthIndicator {

	private final LDClient ldClient;

	public LaunchDarklyHealthIndicator(LDClient ldClient) {
		this.ldClient = ldClient;
	}

	@Override
	public Health health() {
		DataSourceStatusProvider.Status status = ldClient.getDataSourceStatusProvider().getStatus();
		Health.Builder health = Health.up()
				.withDetail("state", status.getState())
				.withDetail("initialized", ldClient.isInitialized());
		if (status.getLastError() != null) {
			health.withDetail("lastError", status.getLastError().toString());
		}
		return health.build();
	}

}
