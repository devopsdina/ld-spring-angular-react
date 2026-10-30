package com.launchdarkly.sample.common;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * Formats the line each backend logs for a product request, e.g.
 *
 * <pre>
 * [quarkus] product 2 requested by react (plan=gold), routed by api-backend (RULE_MATCH) -> discount-pricing 0.25 (FALLTHROUGH)
 * </pre>
 *
 * The frontend, plan and routing come from the browser, so only values that match a known shape
 * are logged; anything else is logged as "unknown". That keeps a crafted value (say, one with a
 * newline) from writing fake log lines. The email is never logged.
 */
public final class RequestLog {

	public static final String ROUTING_HEADER = "X-Backend-Routing";

	private static final Set<String> FRAMEWORKS = Set.of("angular", "react");
	private static final Pattern PLAN = Pattern.compile("[A-Za-z0-9_-]{1,32}");
	private static final Pattern FLAG_ROUTING = Pattern.compile("flag:([A-Z_]{1,32})");

	private RequestLog() {
	}

	public static String line(Backend backend, int productId, ProductRequest request, double discount,
			String discountReason) {
		return "[%s] product %d requested by %s (plan=%s), %s -> discount-pricing %s (%s)".formatted(backend.id(),
				productId, frontend(request.framework()), plan(request.plan()), routing(request.routing()), discount,
				discountReason);
	}

	static String frontend(String framework) {
		return framework != null && FRAMEWORKS.contains(framework) ? framework : "unknown client";
	}

	static String plan(String plan) {
		return plan != null && PLAN.matcher(plan).matches() ? plan : "unknown";
	}

	static String routing(String routing) {
		if (routing == null) {
			return "no routing given";
		}
		if (routing.equals("url-override")) {
			return "routed by URL override";
		}
		var flag = FLAG_ROUTING.matcher(routing);
		return flag.matches() ? "routed by api-backend (" + flag.group(1) + ")" : "routing unknown";
	}

}
