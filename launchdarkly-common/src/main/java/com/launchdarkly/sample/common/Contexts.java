package com.launchdarkly.sample.common;

import java.util.regex.Pattern;

import com.launchdarkly.sdk.ContextKind;
import com.launchdarkly.sdk.LDContext;
import com.launchdarkly.sdk.json.JsonSerialization;

/**
 * Builds every evaluation context, for both backends, so a targeting rule matches the same way in
 * Spring Boot and Quarkus. The browser builds the same {@code user} and {@code product} contexts;
 * {@code contracts/contexts.json} holds the expected JSON, and the Java, Angular and React tests all
 * check against it.
 */
public final class Contexts {

	/**
	 * The key every visitor without a userKey shares, so anonymous requests don't each create a new
	 * context in LaunchDarkly.
	 */
	public static final String ANONYMOUS_KEY = "anonymous";

	public static final ContextKind PRODUCT = ContextKind.of("product");

	private static final Pattern EMAIL_NAME = Pattern.compile("[^\"@\\s]+@");

	private Contexts() {
	}

	/** The user, as the browser sends it. The bootstrap endpoint returns this to the browser. */
	public static LDContext user(String userKey, String email, String plan, String framework) {
		return userBuilder(userKey, email, plan, framework).build();
	}

	/**
	 * The user plus the {@code backend} attribute (which backend is evaluating), with the product:
	 * the context server-side flags are evaluated for. A multi-context, so targeting rules can use
	 * user attributes, product attributes, or both, e.g. "50% off products over $100 for gold-plan
	 * users".
	 */
	public static LDContext server(String userKey, String email, String plan, String framework, Backend backend,
			Product product) {
		LDContext user = userBuilder(userKey, email, plan, framework).set("backend", backend.id()).build();
		return LDContext.createMulti(user, product(product));
	}

	public static LDContext product(Product product) {
		return LDContext.builder(PRODUCT, String.valueOf(product.id()))
				.name(product.name())
				.set("type", product.type())
				.set("price", product.price().doubleValue())
				.build();
	}

	/** The context as JSON with email addresses masked (abc123@gmail.com → ***@gmail.com), for logs. */
	public static String forLog(LDContext context) {
		return EMAIL_NAME.matcher(JsonSerialization.serialize(context)).replaceAll("***@");
	}

	private static com.launchdarkly.sdk.ContextBuilder userBuilder(String userKey, String email, String plan,
			String framework) {
		if (userKey == null || userKey.isBlank()) {
			return LDContext.builder(ANONYMOUS_KEY).anonymous(true);
		}
		var user = LDContext.builder(userKey);
		if (email != null) {
			user.set("email", email);
		}
		if (plan != null) {
			user.set("plan", plan);
		}
		if (framework != null) {
			user.set("framework", framework);
		}
		return user;
	}

}
