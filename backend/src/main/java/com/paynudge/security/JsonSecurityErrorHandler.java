package com.paynudge.security;

import java.io.IOException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import com.paynudge.common.ApiError;
import com.paynudge.common.ErrorCodes;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

/** Writes 401 and 403 responses from the security filter chain in the shared {@link ApiError} shape. */
@Component
class JsonSecurityErrorHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

	private final JsonMapper json;

	JsonSecurityErrorHandler(JsonMapper json) {
		this.json = json;
	}

	@Override
	public void commence(HttpServletRequest request, HttpServletResponse response,
			AuthenticationException authException) throws IOException {
		String message = (authException instanceof OAuth2AuthenticationException) ? "Invalid or expired token"
				: "Authentication required";
		response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
		write(response, HttpStatus.UNAUTHORIZED, ApiError.of(ErrorCodes.UNAUTHORIZED, message));
	}

	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response,
			AccessDeniedException accessDeniedException) throws IOException {
		write(response, HttpStatus.FORBIDDEN, ApiError.of(ErrorCodes.FORBIDDEN, "Access denied"));
	}

	private void write(HttpServletResponse response, HttpStatus status, ApiError body) throws IOException {
		response.setStatus(status.value());
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		this.json.writeValue(response.getOutputStream(), body);
	}

}
