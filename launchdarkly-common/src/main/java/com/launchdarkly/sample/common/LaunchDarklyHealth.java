package com.launchdarkly.sample.common;

import java.util.LinkedHashMap;
import java.util.Map;

import com.launchdarkly.sdk.server.LDClient;
import com.launchdarkly.sdk.server.interfaces.DataSourceStatusProvider;

/**
 * The LaunchDarkly connection, for each backend's health endpoint. Backends always report UP: the
 * app keeps working on fallback or last known values while LaunchDarkly is unreachable, and
 * restarting it wouldn't fix the connection. Use {@code state} for dashboards and alerts instead.
 *
 * @param state       the data source state: INITIALIZING, VALID, INTERRUPTED or OFF
 * @param initialized whether the SDK has received flags at least once
 * @param lastError   the most recent connection error, e.g. {@code ERROR_RESPONSE(401)}, or null
 */
public record LaunchDarklyHealth(String state, boolean initialized, String lastError) {

	public static LaunchDarklyHealth of(LDClient client) {
		DataSourceStatusProvider.Status status = client.getDataSourceStatusProvider().getStatus();
		String lastError = status.getLastError() == null ? null : status.getLastError().toString();
		return new LaunchDarklyHealth(status.getState().name(), client.isInitialized(), lastError);
	}

	/** The details to show, leaving out {@code lastError} when there hasn't been one. */
	public Map<String, Object> details() {
		Map<String, Object> details = new LinkedHashMap<>();
		details.put("state", state);
		details.put("initialized", initialized);
		if (lastError != null) {
			details.put("lastError", lastError);
		}
		return details;
	}

}
