package com.paynudge.business;

import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;

import com.paynudge.common.ApiException;
import com.paynudge.common.ErrorCodes;
import com.paynudge.security.Caller;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The caller's own business settings. Lookups are scoped by both business and owner. */
@Service
class BusinessService {

	private final BusinessRepository businesses;

	BusinessService(BusinessRepository businesses) {
		this.businesses = businesses;
	}

	BusinessResponse get(Caller caller) {
		return BusinessResponse.from(find(caller));
	}

	@Transactional
	BusinessResponse update(Caller caller, UpdateBusinessRequest request) {
		validate(request);
		Business business = find(caller);
		business.update(request.name(), request.timezone(), request.quietStart(), request.quietEnd(),
				request.senderName(), request.dailySendCap());
		return BusinessResponse.from(business);
	}

	private Business find(Caller caller) {
		return this.businesses.findByIdAndOwnerId(caller.businessId(), caller.userId())
			.orElseThrow(() -> ApiException.notFound("Business not found"));
	}

	private static void validate(UpdateBusinessRequest request) {
		Map<String, Object> errors = new LinkedHashMap<>();
		if (!ZoneId.getAvailableZoneIds().contains(request.timezone())) {
			errors.put("timezone", "must be a timezone like Asia/Kolkata");
		}
		if (request.quietStart().equals(request.quietEnd())) {
			errors.put("quietEnd", "must be different from the start of quiet hours");
		}
		if (!errors.isEmpty()) {
			throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCodes.VALIDATION_FAILED, "Request validation failed",
					errors);
		}
	}

}
