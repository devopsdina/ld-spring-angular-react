package com.launchdarkly.sample.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RequestLogTest {

	private static String line(String framework, String plan, String routing) {
		return RequestLog.line(Backend.quarkus("3.40.1.1"), 2,
				new ProductRequest("abc123@gmail.com", "abc123@gmail.com", plan, framework, routing), 0.25, "FALLTHROUGH");
	}

	@Test
	void showsTheBackendFrontendRoutingAndResult() {
		assertThat(line("react", "gold", "flag:RULE_MATCH")).isEqualTo(
				"[quarkus] product 2 requested by react (plan=gold), routed by api-backend (RULE_MATCH) -> discount-pricing 0.25 (FALLTHROUGH)");
		assertThat(line("angular", "silver", "url-override")).contains("requested by angular", "routed by URL override");
	}

	@Test
	void neverLogsTheEmail() {
		assertThat(line("react", "gold", "flag:FALLTHROUGH")).doesNotContain("abc123");
	}

	@Test
	void logsUnrecognizedBrowserValuesAsUnknown() {
		String forged = line("react\n[spring-boot] fake line", "gold\r\nfake", "flag:x\nfake");

		assertThat(forged).doesNotContain("\n", "\r", "fake")
				.contains("unknown client", "plan=unknown", "routing unknown");
		assertThat(line(null, null, null)).contains("unknown client", "plan=unknown", "no routing given");
	}

}
