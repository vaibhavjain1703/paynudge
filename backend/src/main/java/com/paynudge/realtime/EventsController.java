package com.paynudge.realtime;

import com.paynudge.security.Caller;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * The signed-in owner's live stream (server-sent events). The web app reads it with fetch, so the bearer token
 * travels in a header, never in the URL.
 */
@RestController
class EventsController {

	private final LiveUpdates live;

	EventsController(LiveUpdates live) {
		this.live = live;
	}

	@GetMapping(path = "/api/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	SseEmitter events(Caller caller) {
		return this.live.subscribe(caller.businessId());
	}

}
