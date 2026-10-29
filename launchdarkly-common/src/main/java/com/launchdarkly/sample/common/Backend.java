package com.launchdarkly.sample.common;

/**
 * Which backend is answering. It's returned with every product, so the UIs show the backend that
 * actually served the request, and it's added to the server-side context, so targeting rules can
 * use it (e.g. "50% off when {@code backend} is {@code quarkus}").
 *
 * @param id      {@code spring-boot} or {@code quarkus}; the value of the {@code backend} attribute
 * @param name    the framework's display name
 * @param version the framework's version, e.g. {@code 3.40.1.1}
 */
public record Backend(String id, String name, String version) {

	public static Backend springBoot(String version) {
		return new Backend("spring-boot", "Spring Boot", version);
	}

	public static Backend quarkus(String version) {
		return new Backend("quarkus", "Quarkus", version);
	}

}
