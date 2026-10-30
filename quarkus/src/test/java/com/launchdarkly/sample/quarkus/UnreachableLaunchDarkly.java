package com.launchdarkly.sample.quarkus;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;

import com.launchdarkly.sample.common.HangingServer;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;

/** Points the SDK at a server that never answers, so the app starts with LaunchDarkly unreachable. */
public class UnreachableLaunchDarkly implements QuarkusTestResourceLifecycleManager {

	private HangingServer launchDarkly;

	@Override
	public Map<String, String> start() {
		try {
			launchDarkly = new HangingServer();
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		return Map.of(
				"launchdarkly.sdk-key", "sdk-test-key",
				"launchdarkly.start-wait", "1s",
				"launchdarkly.relay-proxy-uri", launchDarkly.uri().toString());
	}

	@Override
	public void stop() {
		try {
			launchDarkly.close();
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

}
