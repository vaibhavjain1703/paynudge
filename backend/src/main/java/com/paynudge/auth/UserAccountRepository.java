package com.paynudge.auth;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

interface UserAccountRepository extends JpaRepository<UserAccount, Long> {

	Optional<UserAccount> findByEmail(String email);

	boolean existsByEmail(String email);

}
