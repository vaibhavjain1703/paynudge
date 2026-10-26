package com.paynudge.security;

import java.io.IOException;
import java.time.Duration;
import java.util.List;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;

import com.paynudge.common.ApiError;
import com.paynudge.common.ApiException;
import com.paynudge.common.BodyTooLargeException;
import com.paynudge.common.ErrorCodes;
import com.paynudge.security.RateLimits.Rule;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Two cheap guards in front of every endpoint. Public routes (sign-in, the demo, pay and unsubscribe pages, Razorpay's
 * webhook) are rate limited per client address; the address comes from the platform's proxy headers
 * ({@code server.forward-headers-strategy}). JSON bodies are capped at {@link #MAX_BODY_BYTES}; CSV uploads have
 * their own multipart limits.
 */
@Component
class RequestLimitsFilter extends OncePerRequestFilter {

	static final int MAX_BODY_BYTES = 256 * 1024;

	private record Route(HttpMethod method, String pattern, Rule rule) {
	}

	/** First match wins. */
	private static final List<Route> ROUTES = List.of(
			new Route(HttpMethod.POST, "/api/auth/email/start", Rule.EMAIL_CODE),
			new Route(HttpMethod.POST, "/api/auth/demo", Rule.DEMO),
			new Route(HttpMethod.POST, "/api/auth/**", Rule.SIGN_IN),
			new Route(HttpMethod.POST, "/api/public/pay/*/link", Rule.PAY_LINK),
			new Route(null, "/api/public/**", Rule.PUBLIC),
			new Route(HttpMethod.POST, "/webhooks/razorpay", Rule.WEBHOOK));

	private final AntPathMatcher paths = new AntPathMatcher();

	private final RateLimits limits;

	private final JsonMapper json;

	RequestLimitsFilter(RateLimits limits, JsonMapper json) {
		this.limits = limits;
		this.json = json;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		Rule rule = ruleFor(request);
		if (rule != null) {
			Duration wait = this.limits.take(rule, request.getRemoteAddr());
			if (!wait.isZero()) {
				ApiError refused = RateLimits.tooMany(wait).toBody();
				response.setHeader(HttpHeaders.RETRY_AFTER, refused.details().get("retryAfterSeconds").toString());
				write(response, HttpStatus.TOO_MANY_REQUESTS, refused);
				return;
			}
		}
		if (isMultipart(request)) {
			chain.doFilter(request, response);
			return;
		}
		if (request.getContentLengthLong() > MAX_BODY_BYTES) {
			write(response, HttpStatus.CONTENT_TOO_LARGE, ApiError.of(ErrorCodes.BODY_TOO_LARGE, tooLarge()));
			return;
		}
		chain.doFilter(new CappedBody(request), response);
	}

	private Rule ruleFor(HttpServletRequest request) {
		if (HttpMethod.OPTIONS.matches(request.getMethod())) {
			return null; // a CORS preflight is not a request for anything
		}
		String path = request.getRequestURI().substring(request.getContextPath().length());
		return ROUTES.stream()
			.filter((route) -> route.method() == null || route.method().matches(request.getMethod()))
			.filter((route) -> this.paths.match(route.pattern(), path))
			.map(Route::rule)
			.findFirst()
			.orElse(null);
	}

	private static boolean isMultipart(HttpServletRequest request) {
		String type = request.getContentType();
		return type != null && type.toLowerCase().startsWith(MediaType.MULTIPART_FORM_DATA_VALUE);
	}

	private void write(HttpServletResponse response, HttpStatus status, ApiError body) throws IOException {
		response.setStatus(status.value());
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		this.json.writeValue(response.getOutputStream(), body);
	}

	private static String tooLarge() {
		return "Request body is larger than " + (MAX_BODY_BYTES / 1024) + " KB";
	}

	/** A body sent without a length (chunked) is cut off at the cap too: reading past it fails. */
	private static final class CappedBody extends HttpServletRequestWrapper {

		CappedBody(HttpServletRequest request) {
			super(request);
		}

		@Override
		public ServletInputStream getInputStream() throws IOException {
			ServletInputStream body = super.getInputStream();
			return new ServletInputStream() {

				private long read;

				@Override
				public int read() throws IOException {
					int next = body.read();
					if (next >= 0) {
						count(1);
					}
					return next;
				}

				@Override
				public int read(byte[] buffer, int offset, int length) throws IOException {
					int count = body.read(buffer, offset, length);
					if (count > 0) {
						count(count);
					}
					return count;
				}

				private void count(int bytes) throws IOException {
					this.read += bytes;
					if (this.read > MAX_BODY_BYTES) {
						throw new BodyTooLargeException(tooLarge());
					}
				}

				@Override
				public boolean isFinished() {
					return body.isFinished();
				}

				@Override
				public boolean isReady() {
					return body.isReady();
				}

				@Override
				public void setReadListener(ReadListener listener) {
					body.setReadListener(listener);
				}

			};
		}

	}

}
