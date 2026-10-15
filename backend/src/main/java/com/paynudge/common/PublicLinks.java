package com.paynudge.common;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Links that go out in messages: pages on the web app's public address ({@code APP_URL}), and the one-click
 * unsubscribe endpoint on the API's ({@code API_URL}), which mail clients call directly.
 */
@ConfigurationProperties("paynudge")
public record PublicLinks(@DefaultValue("http://localhost:3000") String appUrl,
		@DefaultValue("http://localhost:8080") String apiUrl) {

	public PublicLinks {
		appUrl = withoutTrailingSlash(appUrl);
		apiUrl = withoutTrailingSlash(apiUrl);
	}

	public String pay(String payToken) {
		return this.appUrl + "/pay/" + payToken;
	}

	public String unsubscribe(String unsubscribeToken) {
		return this.appUrl + "/unsubscribe/" + unsubscribeToken;
	}

	/** Where a mail client POSTs when the customer presses its unsubscribe button (RFC 8058). */
	public String oneClickUnsubscribe(String unsubscribeToken) {
		return this.apiUrl + "/api/public/unsubscribe/" + unsubscribeToken;
	}

	public String due(long dueId) {
		return this.appUrl + "/dues/" + dueId;
	}

	private static String withoutTrailingSlash(String url) {
		return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
	}

}
