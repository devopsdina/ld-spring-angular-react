package com.launchdarkly.sample.launchdarkly;

import com.launchdarkly.sdk.server.Components;
import com.launchdarkly.sdk.server.LDClient;
import com.launchdarkly.sdk.server.LDConfig;
import com.launchdarkly.sdk.server.interfaces.DataSourceStatusProvider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

/**
 * Creates the one LaunchDarkly client for the app, with the PS preflight checklist rules applied:
 *
 * <ol>
 * <li>The SDK is initialized once, as a singleton. Spring creates a {@code @Bean} once and injects
 * the same instance everywhere; don't call {@code new LDClient(...)} anywhere else.
 * https://launchdarkly-labs.github.io/ps-flag-book/sdk/preflight-checklist/init-and-config.html#sdk-is-initialized-once-as-a-singleton-early-in-the-applications-lifecycle
 * <li>Startup never blocks for more than {@code launchdarkly.start-wait} (default 2s; LaunchDarkly
 * recommends 1–5s for server-side SDKs). If LaunchDarkly can't be reached in time, the app starts
 * anyway, serves the fallback values passed in code, and the SDK keeps connecting in the background.
 * https://launchdarkly-labs.github.io/ps-flag-book/sdk/preflight-checklist/init-and-config.html#application-does-not-block-indefinitely-for-initialization
 * </ol>
 *
 * Server-side SDKs have no option to turn evaluation reasons on: every evaluation computes one.
 * Call the {@code *VariationDetail} methods to get it, as {@code ProductController} does.
 */
@Configuration
public class LaunchDarklyConfig {

	private static final Logger log = LoggerFactory.getLogger(LaunchDarklyConfig.class);

	// close() flushes pending analytics events and closes the streaming connection on shutdown.
	@Bean(destroyMethod = "close")
	public LDClient ldClient(LaunchDarklyProperties properties) {
		return createClient(properties, new LDConfig.Builder());
	}

	/** Builds and starts the client. Tests call this directly with their own data source. */
	static LDClient createClient(LaunchDarklyProperties properties, LDConfig.Builder builder) {
		if (!StringUtils.hasText(properties.sdkKey())) {
			throw new IllegalStateException(
					"Set LAUNCHDARKLY_SDK_KEY to your environment's server-side SDK key (launchdarkly.sdk-key).");
		}
		builder.startWait(properties.startWait());
		// Application metadata: LaunchDarkly shows which app and version each SDK connection came from.
		if (StringUtils.hasText(properties.applicationId())) {
			builder.applicationInfo(Components.applicationInfo()
					.applicationId(properties.applicationId())
					.applicationVersion(properties.applicationVersion()));
		}
		if (properties.relayProxyUri() != null) {
			builder.serviceEndpoints(Components.serviceEndpoints().relayProxy(properties.relayProxyUri()));
		}

		log.info("Connecting to LaunchDarkly, waiting at most {}s.", properties.startWait().toMillis() / 1000.0);
		// Blocks for at most startWait, then returns whether or not it connected.
		LDClient client = new LDClient(properties.sdkKey(), builder.build());

		// Logs every connection change for as long as the app runs. Registered after the constructor
		// returns, so the isInitialized() check below logs the first connection.
		client.getDataSourceStatusProvider().addStatusListener(status -> {
			switch (status.getState()) {
				case VALID -> log.info("LaunchDarkly connected. Flags are up to date.");
				case INTERRUPTED -> log.warn(
						"LaunchDarkly connection interrupted, retrying. Serving last known flag values. Error: {}",
						status.getLastError());
				case OFF -> {
					if (stoppedByError(status)) {
						log.error("LaunchDarkly data source stopped and will not retry (often an invalid SDK key). Error: {}",
								status.getLastError());
					} else {
						log.info("LaunchDarkly client closed.");
					}
				}
				default -> log.info("LaunchDarkly data source state: {}", status.getState());
			}
		});

		if (client.isInitialized()) {
			log.info("LaunchDarkly initialized within {}s.", properties.startWait().toMillis() / 1000.0);
		} else {
			log.warn("LaunchDarkly did not initialize within {}s. Serving fallback values until it connects.",
					properties.startWait().toMillis() / 1000.0);
		}
		return client;
	}

	// The data source also goes OFF when the app shuts down and closes the client. Only an error
	// raised as it went OFF means it stopped on its own.
	static boolean stoppedByError(DataSourceStatusProvider.Status status) {
		DataSourceStatusProvider.ErrorInfo error = status.getLastError();
		return error != null && !error.getTime().isBefore(status.getStateSince().minusSeconds(1));
	}

}
