package com.paynudge.common;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Links that go out in messages, on the web app's public address ({@code APP_URL}). */
@ConfigurationProperties("paynudge")
public record PublicLinks(@DefaultValue("http://localhost:3000") String appUrl) {

	public PublicLinks {
		appUrl = appUrl.endsWith("/") ? appUrl.substring(0, appUrl.length() - 1) : appUrl;
	}

	public String pay(String payToken) {
		return this.appUrl + "/pay/" + payToken;
	}

	public String unsubscribe(String unsubscribeToken) {
		return this.appUrl + "/unsubscribe/" + unsubscribeToken;
	}

	public String due(long dueId) {
		return this.appUrl + "/dues/" + dueId;
	}

}
