package com.launchdarkly.sample.product;

import java.math.BigDecimal;

public record Product(int id, String name, String url, BigDecimal price, String type) {
}
