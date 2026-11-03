package com.launchdarkly.sample.launchdarkly;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.launchdarkly.sdk.EvaluationDetail;
import com.launchdarkly.sdk.EvaluationReason;
import com.launchdarkly.sdk.LDContext;
import com.launchdarkly.sdk.LDValue;
import com.launchdarkly.sdk.server.LDClient;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The one way the server reads a flag. Code that evaluates flags injects this, never {@link LDClient}.
 *
 * <ul>
 * <li>Every method calls the SDK's typed {@code *VariationDetail} method and returns the detail: the
 * value plus the reason LaunchDarkly served it. Server-side SDKs always compute the reason, so there's
 * no setting to turn on.
 * <li>Methods are named {@code get…Flag} for what they return. A flag isn't "enabled"; it serves a
 * variation.
 * <li>Each {@code ERROR} reason is logged once per flag, so a missing flag or an outage doesn't flood
 * the log. The fallback passed in is what the caller gets in those cases.
 * </ul>
 *
 * Read the flag inside the decision it controls, and only when you use the value:
 *
 * <pre>{@code
 * // Fallback false: fail closed (new feature).
 * if (flags.getBoolFlag("new-checkout", context, false).getValue()) {
 *     return newCheckout(order);
 * }
 * return legacyCheckout(order);
 * }</pre>
 */
public class FlagService {

	private static final Logger log = LoggerFactory.getLogger(FlagService.class);

	private final LDClient client;
	private final Set<String> reported = ConcurrentHashMap.newKeySet();

	public FlagService(LDClient client) {
		this.client = client;
	}

	public EvaluationDetail<Boolean> getBoolFlag(String key, LDContext context, boolean fallback) {
		return check(key, client.boolVariationDetail(key, context, fallback));
	}

	public EvaluationDetail<String> getStringFlag(String key, LDContext context, String fallback) {
		return check(key, client.stringVariationDetail(key, context, fallback));
	}

	public EvaluationDetail<Integer> getIntFlag(String key, LDContext context, int fallback) {
		return check(key, client.intVariationDetail(key, context, fallback));
	}

	public EvaluationDetail<Double> getDoubleFlag(String key, LDContext context, double fallback) {
		return check(key, client.doubleVariationDetail(key, context, fallback));
	}

	public EvaluationDetail<LDValue> getJsonFlag(String key, LDContext context, LDValue fallback) {
		return check(key, client.jsonValueVariationDetail(key, context, fallback));
	}

	private <T> EvaluationDetail<T> check(String key, EvaluationDetail<T> detail) {
		EvaluationReason reason = detail.getReason();
		if (reason.getKind() != EvaluationReason.Kind.ERROR || !reported.add(key + reason.getErrorKind())) {
			return detail;
		}
		switch (reason.getErrorKind()) {
			case CLIENT_NOT_READY -> log.info("Flag {} served its fallback: LaunchDarkly isn't connected yet.", key);
			case WRONG_TYPE -> log.warn(
					"Flag {} served its fallback: WRONG_TYPE. Use the get…Flag method that matches the flag's type.", key);
			default -> log.warn("Flag {} served its fallback: {}. Check the flag key and the environment.", key,
					reason.getErrorKind());
		}
		return detail;
	}

}
