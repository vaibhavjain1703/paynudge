package com.paynudge.customer;

import com.paynudge.customer.Unsubscribes.UnsubscribeStatus;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public: reached from the link in a reminder, without signing in. The web app's unsubscribe page calls these; mail
 * clients POST straight to the same URL when the customer uses their own unsubscribe button (RFC 8058, with a form
 * body that is ignored here).
 */
@RestController
@RequestMapping("/api/public/unsubscribe/{token}")
class UnsubscribeController {

	private final Unsubscribes unsubscribes;

	UnsubscribeController(Unsubscribes unsubscribes) {
		this.unsubscribes = unsubscribes;
	}

	@GetMapping
	UnsubscribeStatus status(@PathVariable String token) {
		return this.unsubscribes.status(token);
	}

	@PostMapping
	UnsubscribeStatus unsubscribe(@PathVariable String token) {
		return this.unsubscribes.unsubscribe(token);
	}

}
