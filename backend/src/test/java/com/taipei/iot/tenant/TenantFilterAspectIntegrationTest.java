package com.taipei.iot.tenant;

import com.taipei.iot.test.repository.TestTenantAwareRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@Tag("integration")
@Sql(scripts = "/sql/tenant-seed.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class TenantFilterAspectIntegrationTest {

	@Autowired
	private TestTenantAwareRepository testTenantAwareRepository;

	@AfterEach
	void cleanup() {
		TenantContext.clear();
	}

	@Test
	void withTenantContext_shouldFilterByTenantId() {
		TenantContext.setCurrentTenantId("T1");

		List<TestTenantAwareEntity> result = testTenantAwareRepository.findAll();

		assertEquals(2, result.size());
		assertTrue(result.stream().allMatch(e -> "T1".equals(e.getTenantId())));
	}

	@Test
	void withSystemContext_shouldReturnAll() {
		TenantContext.setSystemContext();

		List<TestTenantAwareEntity> result = testTenantAwareRepository.findAll();

		assertEquals(3, result.size());
	}

	@Test
	void withoutContext_shouldThrowIllegalStateException() {
		// TenantContext 未設定時，TenantFilterAspect 採 fail-closed 策略，應拋出例外
		assertThrows(IllegalStateException.class, () -> testTenantAwareRepository.findAll());
	}

}
