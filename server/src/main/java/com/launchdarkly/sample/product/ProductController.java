package com.launchdarkly.sample.product;

import java.math.BigDecimal;
import java.math.RoundingMode;

import com.launchdarkly.sdk.ContextKind;
import com.launchdarkly.sdk.EvaluationDetail;
import com.launchdarkly.sdk.EvaluationReason;
import com.launchdarkly.sdk.LDContext;
import com.launchdarkly.sdk.server.LDClient;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api", produces = "application/json")
public class ProductController {

	static final String DISCOUNT_FLAG = "discount-pricing";
	// Used if LaunchDarkly can't be reached or the flag is missing: full price.
	static final double DISCOUNT_FALLBACK = 0;
	// The key every visitor without a userKey shares, so anonymous requests don't each create a new
	// context in LaunchDarkly.
	static final String ANONYMOUS_KEY = "anonymous";

	private static final Logger log = LoggerFactory.getLogger(ProductController.class);

	private final LDClient ldClient;
	private final ProductCatalog catalog;

	public ProductController(LDClient ldClient, ProductCatalog catalog) {
		this.ldClient = ldClient;
		this.catalog = catalog;
	}

	@GetMapping("/product/{id}")
	public ResponseEntity<ProductOffer> getProduct(@PathVariable int id,
			@RequestParam(required = false) String userKey,
			@RequestParam(required = false) String email,
			@RequestParam(required = false) String plan,
			@RequestParam(required = false) String framework) {
		return catalog.findById(id)
				.map(product -> offer(product, userContext(userKey, email, plan, framework)))
				.map(ResponseEntity::ok)
				.orElseGet(() -> ResponseEntity.notFound().build());
	}

	private ProductOffer offer(Product product, LDContext user) {
		// A multi-context: targeting rules can use user attributes, product attributes, or both,
		// e.g. "50% off products over $100 for gold-plan users".
		LDContext context = LDContext.createMulti(user, productContext(product));

		// The Detail variant returns the reason along with the value.
		EvaluationDetail<Double> detail = ldClient.doubleVariationDetail(DISCOUNT_FLAG, context, DISCOUNT_FALLBACK);
		EvaluationReason reason = detail.getReason();
		if (reason.getKind() == EvaluationReason.Kind.ERROR) {
			log.warn("Flag {} served the fallback value: {}", DISCOUNT_FLAG, reason.getErrorKind());
		}

		double discount = detail.getValue();
		if (discount < 0 || discount > 1) {
			log.warn("Flag {} served {}, which isn't between 0 and 1. Using full price.", DISCOUNT_FLAG, discount);
			discount = DISCOUNT_FALLBACK;
		}
		BigDecimal discountAmount = product.price().multiply(BigDecimal.valueOf(discount));
		BigDecimal price = product.price().subtract(discountAmount).setScale(2, RoundingMode.HALF_UP);

		return new ProductOffer(product.id(), product.name(), product.url(), product.type(), product.price(), price,
				discount, reason.toString());
	}

	static LDContext userContext(String userKey, String email, String plan, String framework) {
		if (userKey == null || userKey.isBlank()) {
			return LDContext.builder(ANONYMOUS_KEY).kind("user").anonymous(true).build();
		}
		var user = LDContext.builder(userKey).kind("user");
		if (email != null) {
			user.set("email", email);
		}
		if (plan != null) {
			user.set("plan", plan);
		}
		if (framework != null) {
			user.set("framework", framework);
		}
		return user.build();
	}

	static LDContext productContext(Product product) {
		return LDContext.builder(ContextKind.of("product"), String.valueOf(product.id()))
				.name(product.name())
				.set("type", product.type())
				.set("price", product.price().doubleValue())
				.build();
	}

}
