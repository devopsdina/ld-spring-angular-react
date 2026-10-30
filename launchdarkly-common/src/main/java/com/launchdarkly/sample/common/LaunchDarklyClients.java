package com.launchdarkly.sample.common;

import com.launchdarkly.sdk.server.Components;
import com.launchdarkly.sdk.server.LDClient;
import com.launchdarkly.sdk.server.LDConfig;
import com.launchdarkly.sdk.server.interfaces.DataSourceStatusProvider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Creates the LaunchDarkly client with the PS preflight checklist rules applied:
 *
 * <ol>
 * <li>The SDK is initialized once, as a singleton. This class doesn't keep the client: each
 * framework's container does (a Spring {@code @Bean}, a Quarkus {@code @Singleton} producer), and
 * closes it on shutdown. A static instance here would leak a connection on every Quarkus dev-mode
 * reload. Don't call {@code new LDClient(...)} anywhere else.
 * https://launchdarkly-labs.github.io/ps-flag-book/sdk/preflight-checklist/init-and-config.html#sdk-is-initialized-once-as-a-singleton-early-in-the-applications-lifecycle
 * <li>Startup never blocks for more than {@code startWait} (default 2s; LaunchDarkly recommends 1–5s
 * for server-side SDKs). If LaunchDarkly can't be reached in time, the app starts anyway, serves
 * the fallback values passed in code, and the SDK keeps connecting in the background.
 * https://launchdarkly-labs.github.io/ps-flag-book/sdk/preflight-checklist/init-and-config.html#application-does-not-block-indefinitely-for-initialization
 * </ol>
 *
 * Server-side SDKs have no option to turn evaluation reasons on: every evaluation computes one.
 * Call the {@code *VariationDetail} methods to get it, as {@link ProductService} does.
 */
public final class LaunchDarklyClients {

	private static final Logger log = LoggerFactory.getLogger(LaunchDarklyClients.class);

	private LaunchDarklyClients() {
	}

	/** Builds and starts the client. */
	public static LDClient create(LaunchDarklySettings settings) {
		return create(settings, new LDConfig.Builder());
	}

	/** Builds and starts the client from {@code builder}. Tests pass their own data source this way. */
	public static LDClient create(LaunchDarklySettings settings, LDConfig.Builder builder) {
		if (settings.sdkKey() == null || settings.sdkKey().isBlank()) {
			throw new IllegalStateException(
					"Set LAUNCHDARKLY_SDK_KEY to your environment's server-side SDK key (launchdarkly.sdk-key).");
		}
		builder.startWait(settings.startWait());
		// Application metadata: LaunchDarkly shows which app and version each SDK connection came from.
		if (settings.applicationId() != null && !settings.applicationId().isBlank()) {
			builder.applicationInfo(Components.applicationInfo()
					.applicationId(settings.applicationId())
					.applicationVersion(settings.applicationVersion()));
		}
		if (settings.relayProxyUri() != null) {
			builder.serviceEndpoints(Components.serviceEndpoints().relayProxy(settings.relayProxyUri()));
		}

		double seconds = settings.startWait().toMillis() / 1000.0;
		log.info("Connecting to LaunchDarkly, waiting at most {}s.", seconds);
		// Blocks for at most startWait, then returns whether or not it connected.
		LDClient client = new LDClient(settings.sdkKey(), builder.build());

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
			log.info("LaunchDarkly initialized within {}s.", seconds);
		} else {
			log.warn("LaunchDarkly did not initialize within {}s. Serving fallback values until it connects.", seconds);
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
