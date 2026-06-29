package com.taipei.iot.common.tenant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * [common v2 F-11] {@link TenantScopeJpql} 單元測試。 主要為「常數值規格鎖定」回歸測試，避免有人不小心改寫片段語意。
 */
class TenantScopeJpqlTest {

	@Test
	@DisplayName("RP_GLOBAL_OR_TENANT 應為 rp.tenantId IS NULL OR rp.tenantId = :tenantId")
	void rpGlobalOrTenantConstant() {
		assertThat(TenantScopeJpql.RP_GLOBAL_OR_TENANT).isEqualTo("(rp.tenantId IS NULL OR rp.tenantId = :tenantId)");
	}

	@Test
	@DisplayName("TENANT_ID_PARAM 應為 tenantId（與 :tenantId 對應）")
	void tenantIdParamConstant() {
		assertThat(TenantScopeJpql.TENANT_ID_PARAM).isEqualTo("tenantId");
	}

	@Nested
	@DisplayName("globalOrTenant(alias)")
	class GlobalOrTenant {

		@Test
		@DisplayName("應根據 alias 產生對應片段")
		void shouldBuildFragmentForAlias() {
			assertThat(TenantScopeJpql.globalOrTenant("e")).isEqualTo("(e.tenantId IS NULL OR e.tenantId = :tenantId)");
			assertThat(TenantScopeJpql.globalOrTenant("rp"))
				.isEqualTo("(rp.tenantId IS NULL OR rp.tenantId = :tenantId)");
		}

		@Test
		@DisplayName("alias 為 rp 時應與 RP_GLOBAL_OR_TENANT 常數一致")
		void shouldMatchPredefinedConstant() {
			assertThat(TenantScopeJpql.globalOrTenant("rp")).isEqualTo(TenantScopeJpql.RP_GLOBAL_OR_TENANT);
		}

		@Test
		@DisplayName("alias 為 null 應拋 IllegalArgumentException")
		void shouldRejectNullAlias() {
			assertThatThrownBy(() -> TenantScopeJpql.globalOrTenant(null)).isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("alias");
		}

		@Test
		@DisplayName("alias 為空白應拋 IllegalArgumentException")
		void shouldRejectBlankAlias() {
			assertThatThrownBy(() -> TenantScopeJpql.globalOrTenant("   ")).isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("alias");
		}

	}

}
