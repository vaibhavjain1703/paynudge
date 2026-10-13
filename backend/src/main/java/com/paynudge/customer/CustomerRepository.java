package com.paynudge.customer;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** Every method takes the business id: there is no way to reach a customer without it. */
public interface CustomerRepository extends JpaRepository<Customer, Long> {

	Optional<Customer> findByIdAndBusinessId(long id, long businessId);

	boolean existsByBusinessIdAndEmail(long businessId, String email);

	boolean existsByBusinessIdAndEmailAndIdNot(long businessId, String email, long id);

	List<Customer> findByBusinessIdAndEmailIn(long businessId, Collection<String> emails);

	List<Customer> findByBusinessIdAndPhoneIn(long businessId, Collection<String> phones);

	/** {@code pattern} is a lower-case LIKE pattern with {@code \} as the escape character. */
	@Query("""
			SELECT c FROM Customer c
			WHERE c.businessId = :businessId
			  AND (lower(c.name) LIKE :pattern ESCAPE '\\' OR c.email LIKE :pattern ESCAPE '\\'
			       OR c.phone LIKE :pattern ESCAPE '\\')
			""")
	Page<Customer> search(long businessId, String pattern, Pageable pageable);

	Page<Customer> findByBusinessId(long businessId, Pageable pageable);

}
