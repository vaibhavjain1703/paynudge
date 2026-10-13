package com.paynudge.common;

import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/** Turns every exception raised by a controller into the shared {@link ApiError} shape. */
@RestControllerAdvice
class GlobalExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	@ExceptionHandler(ApiException.class)
	ResponseEntity<ApiError> handleApi(ApiException ex) {
		return ResponseEntity.status(ex.status()).body(ex.toBody());
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	ResponseEntity<ApiError> handleInvalidBody(MethodArgumentNotValidException ex) {
		Map<String, Object> fields = new LinkedHashMap<>();
		for (FieldError error : ex.getBindingResult().getFieldErrors()) {
			fields.putIfAbsent(error.getField(), error.getDefaultMessage());
		}
		ex.getBindingResult()
			.getGlobalErrors()
			.forEach(error -> fields.putIfAbsent(error.getObjectName(), error.getDefaultMessage()));
		return validationFailed(fields);
	}

	@ExceptionHandler(HandlerMethodValidationException.class)
	ResponseEntity<ApiError> handleInvalidParameters(HandlerMethodValidationException ex) {
		Map<String, Object> fields = new LinkedHashMap<>();
		ex.getParameterValidationResults().forEach(result -> {
			String name = result.getMethodParameter().getParameterName();
			result.getResolvableErrors()
				.forEach(error -> fields.putIfAbsent(name == null ? "parameter" : name, error.getDefaultMessage()));
		});
		return validationFailed(fields);
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	ResponseEntity<ApiError> handleUnreadable(HttpMessageNotReadableException ex) {
		return error(HttpStatus.BAD_REQUEST, ErrorCodes.MALFORMED_REQUEST, "Request body is missing or is not valid JSON");
	}

	@ExceptionHandler({ MethodArgumentTypeMismatchException.class, MissingServletRequestParameterException.class })
	ResponseEntity<ApiError> handleBadParameter(Exception ex) {
		return error(HttpStatus.BAD_REQUEST, ErrorCodes.MALFORMED_REQUEST, ex.getMessage());
	}

	@ExceptionHandler(MaxUploadSizeExceededException.class)
	ResponseEntity<ApiError> handleTooLarge(MaxUploadSizeExceededException ex) {
		return error(HttpStatus.CONTENT_TOO_LARGE, ErrorCodes.FILE_TOO_LARGE,
				"This file is larger than 2 MB. Split it into smaller files.");
	}

	@ExceptionHandler({ MissingServletRequestPartException.class, MultipartException.class })
	ResponseEntity<ApiError> handleMissingFile(Exception ex) {
		return error(HttpStatus.BAD_REQUEST, ErrorCodes.MALFORMED_REQUEST, "Choose a CSV file to upload");
	}

	@ExceptionHandler(NoResourceFoundException.class)
	ResponseEntity<ApiError> handleNoResource(NoResourceFoundException ex) {
		return error(HttpStatus.NOT_FOUND, ErrorCodes.NOT_FOUND, "No such endpoint");
	}

	@ExceptionHandler(HttpRequestMethodNotSupportedException.class)
	ResponseEntity<ApiError> handleMethod(HttpRequestMethodNotSupportedException ex) {
		return error(HttpStatus.METHOD_NOT_ALLOWED, ErrorCodes.METHOD_NOT_ALLOWED, ex.getMessage());
	}

	@ExceptionHandler(HttpMediaTypeNotSupportedException.class)
	ResponseEntity<ApiError> handleMediaType(HttpMediaTypeNotSupportedException ex) {
		return error(HttpStatus.UNSUPPORTED_MEDIA_TYPE, ErrorCodes.UNSUPPORTED_MEDIA_TYPE, ex.getMessage());
	}

	@ExceptionHandler(AuthenticationException.class)
	ResponseEntity<ApiError> handleAuthentication(AuthenticationException ex) {
		return error(HttpStatus.UNAUTHORIZED, ErrorCodes.UNAUTHORIZED, "Authentication required");
	}

	@ExceptionHandler(AccessDeniedException.class)
	ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException ex) {
		return error(HttpStatus.FORBIDDEN, ErrorCodes.FORBIDDEN, "Access denied");
	}

	@ExceptionHandler(Exception.class)
	ResponseEntity<ApiError> handleUnexpected(Exception ex) {
		log.error("Unhandled exception", ex);
		return error(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCodes.INTERNAL_ERROR, "Something went wrong");
	}

	private static ResponseEntity<ApiError> validationFailed(Map<String, Object> fields) {
		return ResponseEntity.badRequest()
			.body(new ApiError(ErrorCodes.VALIDATION_FAILED, "Request validation failed", fields));
	}

	private static ResponseEntity<ApiError> error(HttpStatus status, String code, String message) {
		return ResponseEntity.status(status).body(ApiError.of(code, message));
	}

}
