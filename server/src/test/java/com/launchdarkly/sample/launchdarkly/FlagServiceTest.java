package com.launchdarkly.sample.launchdarkly;

import static org.assertj.core.api.Assertions.assertThat;

import com.launchdarkly.sdk.EvaluationDetail;
import com.launchdarkly.sdk.EvaluationReason;
import com.launchdarkly.sdk.LDContext;
import com.launchdarkly.sdk.LDValue;
import com.launchdarkly.sdk.server.Components;
import com.launchdarkly.sdk.server.LDClient;
import com.launchdarkly.sdk.server.LDConfig;
import com.launchdarkly.sdk.server.integrations.TestData;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FlagServiceTest {

	private static final LDContext USER = LDContext.create("user-1");

	private TestData td;
	private LDClient client;
	private FlagService flags;

	@BeforeEach
	void setUp() {
		td = TestData.dataSource();
		client = new LDClient("sdk-test-key", new LDConfig.Builder().dataSource(td).events(Components.noEvents()).build());
		flags = new FlagService(client);
	}

	@AfterEach
	void tearDown() throws Exception {
		client.close();
	}

	@Test
	void returnsTheValueAndTheReason() {
		td.update(td.flag("new-checkout").variationForAll(true));

		EvaluationDetail<Boolean> detail = flags.getBoolFlag("new-checkout", USER, false);

		assertThat(detail.getValue()).isTrue();
		assertThat(detail.getReason().getKind()).isEqualTo(EvaluationReason.Kind.FALLTHROUGH);
	}

	@Test
	void servesTheFallbackForAMissingFlag() {
		EvaluationDetail<Boolean> detail = flags.getBoolFlag("not-created-yet", USER, false);

		assertThat(detail.getValue()).isFalse();
		assertThat(detail.getReason().getErrorKind()).isEqualTo(EvaluationReason.ErrorKind.FLAG_NOT_FOUND);
	}

	@Test
	void servesTheFallbackWhenTheTypeDoesNotMatch() {
		td.update(td.flag("checkout-theme").variations(LDValue.of("modern")).fallthroughVariation(0));

		EvaluationDetail<Integer> detail = flags.getIntFlag("checkout-theme", USER, 10);

		assertThat(detail.getValue()).isEqualTo(10);
		assertThat(detail.getReason().getErrorKind()).isEqualTo(EvaluationReason.ErrorKind.WRONG_TYPE);
	}

	@Test
	void readsEachType() {
		td.update(td.flag("theme").variations(LDValue.of("modern")).fallthroughVariation(0));
		td.update(td.flag("max-items").variations(LDValue.of(25)).fallthroughVariation(0));
		td.update(td.flag("discount").variations(LDValue.of(0.25)).fallthroughVariation(0));
		td.update(td.flag("limits").variations(LDValue.parse("{\"rps\":100}")).fallthroughVariation(0));

		assertThat(flags.getStringFlag("theme", USER, "classic").getValue()).isEqualTo("modern");
		assertThat(flags.getIntFlag("max-items", USER, 10).getValue()).isEqualTo(25);
		assertThat(flags.getDoubleFlag("discount", USER, 0).getValue()).isEqualTo(0.25);
		assertThat(flags.getJsonFlag("limits", USER, LDValue.ofNull()).getValue().get("rps").intValue()).isEqualTo(100);
	}

}
