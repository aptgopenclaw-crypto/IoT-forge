package com.taipei.iot.device.repository;

import com.taipei.iot.device.entity.Circuit;
import com.taipei.iot.tenant.TenantScopedRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CircuitRepository extends JpaRepository<Circuit, Long>, TenantScopedRepository {

	Optional<Circuit> findByTenantIdAndCircuitNumber(String tenantId, String circuitNumber);

	@Query("""
			SELECT c FROM Circuit c
			WHERE (CAST(:keyword AS string) IS NULL
			       OR LOWER(c.circuitNumber) LIKE LOWER(CONCAT('%', CAST(:keyword AS string), '%'))
			       OR LOWER(c.circuitName) LIKE LOWER(CONCAT('%', CAST(:keyword AS string), '%')))
			""")
	Page<Circuit> findByFilters(@Param("keyword") String keyword, Pageable pageable);

}
