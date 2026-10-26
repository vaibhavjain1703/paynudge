package com.paynudge.demo;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Runs {@link DemoCleanup} on its schedule. */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
class DemoConfig {

}
