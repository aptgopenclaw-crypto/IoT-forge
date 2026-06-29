package com.taipei.iot.assettransfer.repository;

import com.taipei.iot.assettransfer.entity.AssetTransferApplicationEntity;
import com.taipei.iot.tenant.TenantScopedRepository;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AssetTransferApplicationRepository
		extends JpaRepository<AssetTransferApplicationEntity, Long>, TenantScopedRepository {

	Optional<AssetTransferApplicationEntity> findByApplicationNo(String applicationNo);

	List<AssetTransferApplicationEntity> findByApplicantIdOrderByCreatedAtDesc(String applicantId);

	List<AssetTransferApplicationEntity> findByWorkflowInstanceIdIn(Collection<Long> workflowInstanceIds);

}
