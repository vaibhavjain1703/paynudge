package com.paynudge.business;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface BusinessRepository extends JpaRepository<Business, Long> {

	Optional<Business> findByOwnerId(long ownerId);

	Optional<Business> findByIdAndOwnerId(long id, long ownerId);

}
