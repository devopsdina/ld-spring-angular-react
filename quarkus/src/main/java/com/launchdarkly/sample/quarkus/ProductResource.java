package com.launchdarkly.sample.quarkus;

import com.launchdarkly.sample.common.FlagBootstrap;
import com.launchdarkly.sample.common.ProductRequest;
import com.launchdarkly.sample.common.ProductService;
import com.launchdarkly.sample.common.RequestLog;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/api")
@Produces(MediaType.APPLICATION_JSON)
public class ProductResource {

	private final ProductService products;
	private final FlagBootstrap bootstrap;

	public ProductResource(ProductService products, FlagBootstrap bootstrap) {
		this.products = products;
		this.bootstrap = bootstrap;
	}

	@GET
	@Path("/product/{id}")
	public Response getProduct(@PathParam("id") int id,
			@QueryParam("userKey") String userKey,
			@QueryParam("email") String email,
			@QueryParam("plan") String plan,
			@QueryParam("framework") String framework,
			@HeaderParam(RequestLog.ROUTING_HEADER) String routing) {
		return products.offer(id, new ProductRequest(userKey, email, plan, framework, routing))
				.map(offer -> Response.ok(offer).build())
				.orElseGet(() -> Response.status(Response.Status.NOT_FOUND).build());
	}

	/** The browser's context and flags, for the React SDK's bootstrap option. 503 until the SDK has flags. */
	@GET
	@Path("/flags/bootstrap")
	public Response getBootstrap(@QueryParam("email") String email,
			@QueryParam("plan") String plan,
			@QueryParam("framework") String framework) {
		// Flags are per user, so no cache may keep them.
		return bootstrap.forUser(email, plan, framework)
				.map(json -> Response.ok(json))
				.orElseGet(() -> Response.status(Response.Status.SERVICE_UNAVAILABLE))
				.header("Cache-Control", "no-store")
				.build();
	}

}
