package com.launchdarkly.sample.common;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;

import com.launchdarkly.sdk.EvaluationDetail;
import com.launchdarkly.sdk.EvaluationReason;
import com.launchdarkly.sdk.LDContext;
import com.launchdarkly.sdk.server.LDClient;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Prices a product with the {@code discount-pricing} flag. Both backends' product endpoints call this. */
public final class ProductService {

	public static final String DISCOUNT_FLAG = "discount-pricing";
	// Used if LaunchDarkly can't be reached or the flag is missing: full price.
	public static final double DISCOUNT_FALLBACK = 0;

	private static final Logger log = LoggerFactory.getLogger(ProductService.class);

	private final LDClient ldClient;
	private final ProductCatalog catalog;
	private final Backend backend;

	public ProductService(LDClient ldClient, ProductCatalog catalog, Backend backend) {
		this.ldClient = ldClient;
		this.catalog = catalog;
		this.backend = backend;
	}

	/** The product priced for this request, or empty if there's no product with that id. */
	public Optional<ProductOffer> offer(int id, ProductRequest request) {
		return catalog.findById(id).map(product -> offer(product, request));
	}

	private ProductOffer offer(Product product, ProductRequest request) {
		LDContext context = Contexts.server(request.userKey(), request.email(), request.plan(), request.framework(),
				backend, product);
		log.debug("[{}] Evaluating {} for {}", backend.id(), DISCOUNT_FLAG, Contexts.forLog(context));

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

		log.info(RequestLog.line(backend, product.id(), request, discount, reason.toString()));
		return new ProductOffer(product.id(), product.name(), product.url(), product.type(), product.price(), price,
				discount, reason.toString(), backend);
	}

}
