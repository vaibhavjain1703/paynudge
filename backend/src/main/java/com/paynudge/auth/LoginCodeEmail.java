package com.paynudge.auth;

import java.time.Duration;

import com.paynudge.channel.EmailMessage;

/** The sign-in code email. The code stays out of the subject so it does not show on a locked phone screen. */
final class LoginCodeEmail {

	static final String SUBJECT = "Your PayNudge sign-in code";

	private LoginCodeEmail() {
	}

	static EmailMessage render(String to, String code, Duration validFor) {
		long minutes = validFor.toMinutes();
		String text = """
				Your PayNudge sign-in code is %s

				It expires in %d minutes. If you did not try to sign in, ignore this email: \
				nobody can get in without the code.
				""".formatted(code, minutes);
		String html = """
				<div style="font-family:system-ui,-apple-system,Segoe UI,sans-serif;max-width:420px;margin:0 auto;\
				padding:24px;color:#111">
				  <p style="margin:0 0 16px">Your PayNudge sign-in code is</p>
				  <p style="margin:0 0 16px;font-size:32px;font-weight:700;letter-spacing:8px;\
				font-family:ui-monospace,monospace">%s</p>
				  <p style="margin:0;color:#555;font-size:14px">It expires in %d minutes. If you did not try to sign \
				in, ignore this email: nobody can get in without the code.</p>
				</div>
				""".formatted(code, minutes);
		return new EmailMessage(to, SUBJECT, text, html);
	}

}
