package com.taipei.iot.test.repository;

import com.taipei.iot.tenant.TenantScopedRepository;
import com.taipei.iot.tenant.TestTenantAwareEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TestTenantAwareRepository extends JpaRepository<TestTenantAwareEntity, Long>, TenantScopedRepository {

}
