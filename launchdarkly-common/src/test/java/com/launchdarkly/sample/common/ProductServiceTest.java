package com.launchdarkly.sample.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import com.launchdarkly.sdk.ContextKind;
import com.launchdarkly.sdk.LDValue;
import com.launchdarkly.sdk.server.Components;
import com.launchdarkly.sdk.server.LDClient;
import com.launchdarkly.sdk.server.LDConfig;
import com.launchdarkly.sdk.server.integrations.TestData;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

// Runs the real SDK with TestData, the SDK's in-memory flag source, so targeting rules are
// evaluated exactly as they would be with flags from LaunchDarkly.
class ProductServiceTest {

	private TestData flags;
	private LDClient client;

	@BeforeEach
	void setUp() {
		flags = TestData.dataSource();
		client = new LDClient("sdk-test-key", new LDConfig.Builder().dataSource(flags).events(Components.noEvents()).build());
	}

	@AfterEach
	void tearDown() throws Exception {
		client.close();
	}

	private ProductOffer get(Backend backend, int id, String userKey, String plan) {
		return new ProductService(client, new ProductCatalog(), backend)
				.offer(id, new ProductRequest(userKey, userKey + "@example.com", plan, "angular", "flag:FALLTHROUGH"))
				.orElseThrow();
	}

	private ProductOffer get(int id, String userKey, String plan) {
		return get(Backend.springBoot("4.1.2"), id, userKey, plan);
	}

	private void discountFlag(LDValue... variations) {
		flags.update(flags.flag(ProductService.DISCOUNT_FLAG).variations(variations).fallthroughVariation(0));
	}

	@Test
	void appliesTheDiscountAndReportsTheReasonAndBackend() {
		discountFlag(LDValue.of(0.25));

		ProductOffer offer = get(1, "user-1", "gold");

		assertThat(offer.originalPrice()).isEqualByComparingTo("90.00");
		assertThat(offer.price()).isEqualByComparingTo("67.50");
		assertThat(offer.discount()).isEqualTo(0.25);
		assertThat(offer.discountReason()).isEqualTo("FALLTHROUGH");
		assertThat(offer.backend()).isEqualTo(Backend.springBoot("4.1.2"));
	}

	@Test
	void targetsOnUserAndProductAttributesTogether() {
		// 50% off electronics for gold-plan users; everyone else pays full price.
		flags.update(flags.flag(ProductService.DISCOUNT_FLAG)
				.variations(LDValue.of(0), LDValue.of(0.5))
				.fallthroughVariation(0)
				.ifMatch(ContextKind.DEFAULT, "plan", LDValue.of("gold"))
				.andMatch(Contexts.PRODUCT, "type", LDValue.of("electronics"))
				.thenReturn(1));

		ProductOffer goldCamera = get(2, "user-1", "gold");
		ProductOffer silverCamera = get(2, "user-2", "silver");
		ProductOffer goldChair = get(1, "user-1", "gold");

		assertThat(goldCamera.price()).isEqualByComparingTo("300.00");
		assertThat(goldCamera.discountReason()).startsWith("RULE_MATCH");
		assertThat(silverCamera.price()).isEqualByComparingTo("600.00");
		assertThat(goldChair.price()).isEqualByComparingTo("90.00");
	}

	@Test
	void targetsOnTheBackendThatEvaluates() {
		// 50% off only when Quarkus evaluates the flag.
		flags.update(flags.flag(ProductService.DISCOUNT_FLAG)
				.variations(LDValue.of(0), LDValue.of(0.5))
				.fallthroughVariation(0)
				.ifMatch(ContextKind.DEFAULT, "backend", LDValue.of("quarkus"))
				.thenReturn(1));

		assertThat(get(Backend.quarkus("3.40.1.1"), 2, "user-1", "silver").price()).isEqualByComparingTo("300.00");
		assertThat(get(Backend.springBoot("4.1.2"), 2, "user-1", "silver").price()).isEqualByComparingTo("600.00");
	}

	@Test
	void servesFullPriceWhenTheFlagIsMissing() {
		ProductOffer offer = get(3, "user-1", "gold");

		assertThat(offer.price()).isEqualByComparingTo("130.00");
		assertThat(offer.discount()).isZero();
		assertThat(offer.discountReason()).isEqualTo("ERROR(FLAG_NOT_FOUND)");
	}

	@Test
	void servesFullPriceWhenTheFlagServesAnOutOfRangeDiscount() {
		discountFlag(LDValue.of(2));

		ProductOffer offer = get(4, "user-1", "gold");

		assertThat(offer.price()).isEqualByComparingTo(new BigDecimal("6.00"));
	}

	@Test
	void findsNoOfferForAnUnknownProduct() {
		assertThat(new ProductService(client, new ProductCatalog(), Backend.quarkus("3.40.1.1"))
				.offer(99, new ProductRequest("user-1", null, null, null, null))).isEmpty();
	}

}
