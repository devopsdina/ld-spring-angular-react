package com.launchdarkly.sample.common;

import java.math.BigDecimal;

/**
 * A product with its price after the {@code discount-pricing} flag is applied. Both backends return
 * this same JSON. It's a plain record with no Jackson annotations, because Spring Boot 4 uses
 * Jackson 3 and Quarkus 3 uses Jackson 2.
 *
 * @param discount       the fraction taken off, from the flag (0 = full price)
 * @param discountReason why the flag served that value, e.g. {@code FALLTHROUGH}, {@code RULE_MATCH}
 *                       or {@code ERROR(CLIENT_NOT_READY)}
 * @param backend        the backend that evaluated the flag and answered
 */
public record ProductOffer(int id, String name, String url, String type, BigDecimal originalPrice,
		BigDecimal price, double discount, String discountReason, Backend backend) {
}
