package com.launchdarkly.sample.quarkus;

import com.launchdarkly.sample.common.LaunchDarklyHealth;
import com.launchdarkly.sdk.server.LDClient;

import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.HealthCheckResponseBuilder;
import org.eclipse.microprofile.health.Readiness;

import jakarta.enterprise.context.ApplicationScoped;

/**
 * Shows the LaunchDarkly connection at /q/health. It always reports UP; see
 * {@link LaunchDarklyHealth} for why.
 */
@Readiness
@ApplicationScoped
public class LaunchDarklyHealthCheck implements HealthCheck {

	private final LDClient ldClient;

	public LaunchDarklyHealthCheck(LDClient ldClient) {
		this.ldClient = ldClient;
	}

	@Override
	public HealthCheckResponse call() {
		HealthCheckResponseBuilder health = HealthCheckResponse.named("launchdarkly").up();
		LaunchDarklyHealth.of(ldClient).details().forEach((name, value) -> {
			if (value instanceof Boolean flag) {
				health.withData(name, flag);
			} else {
				health.withData(name, String.valueOf(value));
			}
		});
		return health.build();
	}

}
