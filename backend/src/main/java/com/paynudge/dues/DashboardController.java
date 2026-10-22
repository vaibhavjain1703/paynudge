package com.paynudge.dues;

import com.paynudge.dues.DashboardQueries.Dashboard;
import com.paynudge.security.Caller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class DashboardController {

	private final DashboardQueries dashboard;

	DashboardController(DashboardQueries dashboard) {
		this.dashboard = dashboard;
	}

	@GetMapping("/api/dashboard")
	Dashboard dashboard(Caller caller) {
		return this.dashboard.load(caller.businessId());
	}

}
