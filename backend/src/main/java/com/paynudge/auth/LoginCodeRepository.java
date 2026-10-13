package com.paynudge.auth;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

interface LoginCodeRepository extends JpaRepository<LoginCode, Long> {

	/** The only code that counts for an email is the newest one. */
	Optional<LoginCode> findFirstByEmailOrderByCreatedAtDesc(String email);

	long countByEmailAndCreatedAtAfter(String email, Instant after);

}
