package com.taipei.iot.announcement.repository;

import com.taipei.iot.tenant.TenantScopedRepository;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 回歸測試：確保操作 TenantAware 實體的 Repository 必須 implements {@link TenantScopedRepository}，否則
 * {@code TenantFilterAspect} 不會啟用 Hibernate {@code @Filter}，將導致跨租戶資料外洩（見
 * tenant-module-code-review-v2 T-1）。
 */
class AnnouncementAttachmentRepositoryTest {

	@Test
	void shouldImplementTenantScopedRepository() {
		assertTrue(TenantScopedRepository.class.isAssignableFrom(AnnouncementAttachmentRepository.class),
				"AnnouncementAttachmentRepository 必須 implements TenantScopedRepository，"
						+ "否則 TenantFilterAspect 不會啟用 tenantFilter，會導致跨租戶讀取附件。");
	}

}
