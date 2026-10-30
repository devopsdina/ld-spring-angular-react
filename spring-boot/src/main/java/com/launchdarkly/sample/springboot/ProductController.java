package com.launchdarkly.sample.springboot;

import com.launchdarkly.sample.common.FlagBootstrap;
import com.launchdarkly.sample.common.ProductOffer;
import com.launchdarkly.sample.common.ProductRequest;
import com.launchdarkly.sample.common.ProductService;
import com.launchdarkly.sample.common.RequestLog;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class ProductController {

	private final ProductService products;
	private final FlagBootstrap bootstrap;

	public ProductController(ProductService products, FlagBootstrap bootstrap) {
		this.products = products;
		this.bootstrap = bootstrap;
	}

	@GetMapping(path = "/product/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<ProductOffer> getProduct(@PathVariable int id,
			@RequestParam(required = false) String userKey,
			@RequestParam(required = false) String email,
			@RequestParam(required = false) String plan,
			@RequestParam(required = false) String framework,
			@RequestHeader(name = RequestLog.ROUTING_HEADER, required = false) String routing) {
		return products.offer(id, new ProductRequest(userKey, email, plan, framework, routing))
				.map(ResponseEntity::ok)
				.orElseGet(() -> ResponseEntity.notFound().build());
	}

	/** The browser's context and flags, for the React SDK's bootstrap option. 503 until the SDK has flags. */
	@GetMapping(path = "/flags/bootstrap", produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<String> getBootstrap(@RequestParam(required = false) String email,
			@RequestParam(required = false) String plan,
			@RequestParam(required = false) String framework) {
		// Flags are per user, so no cache may keep them.
		return bootstrap.forUser(email, plan, framework)
				.map(json -> ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(json))
				.orElseGet(() -> ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).cacheControl(CacheControl.noStore()).build());
	}

}
