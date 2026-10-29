package com.launchdarkly.sample.common;

import java.util.Optional;

import com.launchdarkly.sdk.LDContext;
import com.launchdarkly.sdk.json.JsonSerialization;
import com.launchdarkly.sdk.server.FeatureFlagsState;
import com.launchdarkly.sdk.server.FlagsStateOption;
import com.launchdarkly.sdk.server.LDClient;

/**
 * Evaluates the browser's flags on the server, so the React SDK can start with them (its
 * {@code bootstrap} option) instead of waiting for LaunchDarkly. The first render then already has
 * the right {@code api-backend} value, so the page doesn't flicker or call the wrong backend first.
 *
 * <p>The server builds the context and returns it with the flags. The browser uses that context
 * unchanged, so the bootstrapped values are guaranteed to be for the context it identifies with. In
 * this demo the email and plan come from the URL so you can try targeting rules; a real app builds
 * the context from the logged-in session instead, so a user can't put someone else's email in the URL.
 */
public final class FlagBootstrap {

	private final LDClient ldClient;

	public FlagBootstrap(LDClient ldClient) {
		this.ldClient = ldClient;
	}

	/**
	 * {@code {"context": {...}, "flags": {...}}}, or empty if the SDK hasn't received flags yet, in
	 * which case the browser starts without a bootstrap.
	 */
	public Optional<String> forUser(String email, String plan, String framework) {
		LDContext context = Contexts.user(email, email, plan, framework);
		// CLIENT_SIDE_ONLY: only flags marked available to client-side SDKs. Server-only flags such as
		// discount-pricing never reach the browser. WITH_REASONS: variationDetail has reasons from the
		// first render, as it would with withReasons: true.
		FeatureFlagsState state = ldClient.allFlagsState(context, FlagsStateOption.CLIENT_SIDE_ONLY,
				FlagsStateOption.WITH_REASONS);
		if (!state.isValid()) {
			return Optional.empty();
		}
		// The SDK's own JSON serializer produces the format the JavaScript SDKs expect, without
		// depending on either framework's Jackson version.
		return Optional.of("{\"context\":" + JsonSerialization.serialize(context) + ",\"flags\":"
				+ JsonSerialization.serialize(state) + "}");
	}

}
