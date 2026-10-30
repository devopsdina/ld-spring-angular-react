package com.launchdarkly.sample.common;

/**
 * What the browser sends with a product request. Every field comes from the browser, so treat it
 * as untrusted: {@link RequestLog} only logs values it recognizes.
 *
 * @param userKey   the user's context key (the email in this demo), or null for an anonymous visitor
 * @param framework {@code angular} or {@code react}
 * @param routing   the {@code X-Backend-Routing} header: how the UI chose this backend, e.g.
 *                  {@code flag:RULE_MATCH} or {@code url-override}
 */
public record ProductRequest(String userKey, String email, String plan, String framework, String routing) {
}
