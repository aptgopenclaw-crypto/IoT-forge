package com.taipei.iot.device.repository;

import com.taipei.iot.device.entity.Contract;
import com.taipei.iot.device.enums.ContractStatus;
import com.taipei.iot.common.tenant.TenantScopedRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ContractRepository extends JpaRepository<Contract, Long>, TenantScopedRepository {

	Optional<Contract> findByTenantIdAndContractName(String tenantId, String contractName);

	@Query("""
			SELECT c FROM Contract c
			WHERE (:status IS NULL OR c.status = :status)
			  AND (CAST(:keyword AS string) IS NULL
			       OR LOWER(c.contractCode) LIKE LOWER(CONCAT('%', CAST(:keyword AS string), '%'))
			       OR LOWER(c.contractName) LIKE LOWER(CONCAT('%', CAST(:keyword AS string), '%')))
			""")
	Page<Contract> findByFilters(@Param("status") ContractStatus status, @Param("keyword") String keyword,
			Pageable pageable);

}
