package com.launchdarkly.sample.product;

import java.math.BigDecimal;

/**
 * A product with its price after the {@code discount-pricing} flag is applied.
 *
 * @param discount       the fraction taken off, from the flag (0 = full price)
 * @param discountReason why the flag served that value, e.g. {@code FALLTHROUGH}, {@code RULE_MATCH}
 *                       or {@code ERROR(CLIENT_NOT_READY)}
 */
public record ProductOffer(int id, String name, String url, String type, BigDecimal originalPrice,
		BigDecimal price, double discount, String discountReason) {
}
