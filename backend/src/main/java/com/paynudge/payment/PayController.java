package com.paynudge.payment;

import com.paynudge.payment.PayLinks.PayPage;
import com.paynudge.payment.PayLinks.ReturnTrip;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Public: the pay page behind the link in every reminder. The pay token is the only key. */
@RestController
@RequestMapping("/api/public/pay/{token}")
class PayController {

	private final PayLinks payLinks;

	PayController(PayLinks payLinks) {
		this.payLinks = payLinks;
	}

	/** {@code url}: Razorpay's page to pay on. */
	record PayLinkResponse(String url) {
	}

	@GetMapping
	PayPage page(@PathVariable String token) {
		return this.payLinks.page(token);
	}

	@PostMapping("/link")
	PayLinkResponse link(@PathVariable String token) {
		return new PayLinkResponse(this.payLinks.link(token));
	}

	@PostMapping("/confirm")
	PayPage confirm(@PathVariable String token, @RequestBody ReturnTrip trip) {
		return this.payLinks.confirm(token, trip);
	}

}
