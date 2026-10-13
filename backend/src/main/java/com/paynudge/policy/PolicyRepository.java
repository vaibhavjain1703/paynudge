package com.paynudge.policy;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

interface PolicyRepository extends JpaRepository<Policy, Long> {

	List<Policy> findByBusinessId(long businessId);

	Optional<Policy> findByIdAndBusinessId(long id, long businessId);

	boolean existsByBusinessIdAndName(long businessId, String name);

	boolean existsByBusinessIdAndNameAndIdNot(long businessId, String name, long id);

	long countByBusinessIdAndStarterTrue(long businessId);

}
