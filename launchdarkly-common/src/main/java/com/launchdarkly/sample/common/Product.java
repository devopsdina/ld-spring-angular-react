package com.launchdarkly.sample.common;

import java.math.BigDecimal;

public record Product(int id, String name, String url, BigDecimal price, String type) {
}
