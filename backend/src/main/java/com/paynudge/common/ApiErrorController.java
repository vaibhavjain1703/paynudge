package com.paynudge.common;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;

import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Replaces Spring Boot's default {@code /error} body so failures raised outside a controller (servlet container,
 * filters) still use the {@link ApiError} shape.
 */
@RestController
class ApiErrorController implements ErrorController {

	@RequestMapping("/error")
	ResponseEntity<ApiError> error(HttpServletRequest request) {
		Object attribute = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
		HttpStatus status = (attribute instanceof Integer code) ? HttpStatus.resolve(code) : null;
		if (status == null) {
			status = HttpStatus.INTERNAL_SERVER_ERROR;
		}
		String message = status.is5xxServerError() ? "Something went wrong" : status.getReasonPhrase();
		return ResponseEntity.status(status).body(ApiError.of(ErrorCodes.forStatus(status.value()), message));
	}

}
