package com.launchdarkly.sample.product;

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
class ProductControllerTest {

	private TestData flags;
	private LDClient client;
	private ProductController controller;

	@BeforeEach
	void setUp() {
		flags = TestData.dataSource();
		client = new LDClient("sdk-test-key", new LDConfig.Builder().dataSource(flags).events(Components.noEvents()).build());
		controller = new ProductController(client, new ProductCatalog());
	}

	@AfterEach
	void tearDown() throws Exception {
		client.close();
	}

	private ProductOffer get(int id, String userKey, String plan) {
		return controller.getProduct(id, userKey, userKey + "@example.com", plan, "angular").getBody();
	}

	private void discountFlag(LDValue... variations) {
		flags.update(flags.flag(ProductController.DISCOUNT_FLAG).variations(variations).fallthroughVariation(0));
	}

	@Test
	void appliesTheDiscountAndReportsTheReason() {
		discountFlag(LDValue.of(0.25));

		ProductOffer offer = get(1, "user-1", "gold");

		assertThat(offer.originalPrice()).isEqualByComparingTo("90.00");
		assertThat(offer.price()).isEqualByComparingTo("67.50");
		assertThat(offer.discount()).isEqualTo(0.25);
		assertThat(offer.discountReason()).isEqualTo("FALLTHROUGH");
	}

	@Test
	void targetsOnUserAndProductAttributesTogether() {
		// 50% off products over $100 for gold-plan users; everyone else pays full price.
		flags.update(flags.flag(ProductController.DISCOUNT_FLAG)
				.variations(LDValue.of(0), LDValue.of(0.5))
				.fallthroughVariation(0)
				.ifMatch(ContextKind.DEFAULT, "plan", LDValue.of("gold"))
				.andMatch(ContextKind.of("product"), "type", LDValue.of("electronics"))
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
	void returns404ForAnUnknownProduct() {
		assertThat(controller.getProduct(99, "user-1", null, null, null).getStatusCode().value()).isEqualTo(404);
	}

	@Test
	void usesOneSharedKeyForAnonymousVisitors() {
		assertThat(ProductController.userContext(null, null, null, null).getKey()).isEqualTo(ProductController.ANONYMOUS_KEY);
		assertThat(ProductController.userContext(null, null, null, null).isAnonymous()).isTrue();
	}

}
