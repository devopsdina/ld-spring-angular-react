package com.launchdarkly.sample.quarkus;

import java.io.IOException;

import com.launchdarkly.sample.common.Backend;
import com.launchdarkly.sample.common.FlagBootstrap;
import com.launchdarkly.sample.common.LaunchDarklyClients;
import com.launchdarkly.sample.common.ProductCatalog;
import com.launchdarkly.sample.common.ProductService;
import com.launchdarkly.sdk.server.LDClient;

import io.quarkus.runtime.Quarkus;
import io.quarkus.runtime.Startup;
import jakarta.enterprise.inject.Disposes;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;

/**
 * Wires the shared LaunchDarkly code ({@code launchdarkly-common}) into Quarkus. The client setup,
 * contexts and evaluations live there, so Quarkus and Spring Boot behave the same way.
 *
 * <p>The SDK is initialized once, as a singleton: CDI calls the {@code @Singleton} producer once and
 * injects the same client everywhere. Quarkus creates beans lazily, on first use; {@code @Startup}
 * creates the client while the app starts instead, so startup waits for LaunchDarkly for at most
 * {@code launchdarkly.start-wait}, as it does in Spring Boot.
 */
public class LaunchDarklyProducer {

	@Produces
	@Singleton
	@Startup
	LDClient ldClient(LaunchDarklyConfig config) {
		return LaunchDarklyClients.create(config.toSettings());
	}

	// Flushes pending analytics events and closes the streaming connection on shutdown, and before
	// each dev-mode reload creates a new client.
	void close(@Disposes LDClient client) throws IOException {
		client.close();
	}

	@Produces
	@Singleton
	Backend backend() {
		// quarkus-core's jar manifest carries the Quarkus version.
		String version = Quarkus.class.getPackage().getImplementationVersion();
		return Backend.quarkus(version != null ? version : "unknown");
	}

	@Produces
	@Singleton
	ProductService productService(LDClient ldClient, Backend backend) {
		return new ProductService(ldClient, new ProductCatalog(), backend);
	}

	@Produces
	@Singleton
	FlagBootstrap flagBootstrap(LDClient ldClient) {
		return new FlagBootstrap(ldClient);
	}

}
