package com.launchdarkly.sample.springboot;

import com.launchdarkly.sample.common.Backend;
import com.launchdarkly.sample.common.FlagBootstrap;
import com.launchdarkly.sample.common.LaunchDarklyClients;
import com.launchdarkly.sample.common.ProductCatalog;
import com.launchdarkly.sample.common.ProductService;
import com.launchdarkly.sdk.server.LDClient;

import org.springframework.boot.SpringBootVersion;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the shared LaunchDarkly code ({@code launchdarkly-common}) into Spring. The client setup,
 * contexts and evaluations live there, so Spring Boot and Quarkus behave the same way.
 *
 * <p>The SDK is initialized once, as a singleton: Spring creates the {@code @Bean} once and injects
 * the same instance everywhere. Spring creates it while the app starts, so startup waits for
 * LaunchDarkly for at most {@code launchdarkly.start-wait}.
 */
@Configuration
public class LaunchDarklyConfig {

	// close() flushes pending analytics events and closes the streaming connection on shutdown.
	@Bean(destroyMethod = "close")
	public LDClient ldClient(LaunchDarklyProperties properties) {
		return LaunchDarklyClients.create(properties.toSettings());
	}

	@Bean
	public Backend backend() {
		return Backend.springBoot(SpringBootVersion.getVersion());
	}

	@Bean
	public ProductService productService(LDClient ldClient, Backend backend) {
		return new ProductService(ldClient, new ProductCatalog(), backend);
	}

	@Bean
	public FlagBootstrap flagBootstrap(LDClient ldClient) {
		return new FlagBootstrap(ldClient);
	}

}
