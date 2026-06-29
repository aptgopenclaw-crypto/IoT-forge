package com.taipei.iot.common.dto;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class UserInfoTest {

	// ─── equals / hashCode ────────────────────────────────────────────────────

	@Test
	void equalsAndHashCode_sameValues_areEqual() {
		UserInfo a = UserInfo.builder().userId("u1").tenantId("t1").build();
		UserInfo b = UserInfo.builder().userId("u1").tenantId("t1").build();

		assertEquals(a, b, "相同欄位值的 UserInfo 應相等");
		assertEquals(a.hashCode(), b.hashCode(), "相等物件的 hashCode 必須相同");
	}

	@Test
	void equalsAndHashCode_differentUserId_notEqual() {
		UserInfo a = UserInfo.builder().userId("u1").tenantId("t1").build();
		UserInfo b = UserInfo.builder().userId("u2").tenantId("t1").build();

		assertNotEquals(a, b, "userId 不同的 UserInfo 不應相等");
	}

	@Test
	void equalsAndHashCode_allFieldsNull_equalToAnotherAllNull() {
		UserInfo a = UserInfo.builder().build();
		UserInfo b = UserInfo.builder().build();

		assertEquals(a, b);
		assertEquals(a.hashCode(), b.hashCode());
	}

	@Test
	void equalsAndHashCode_nullDeptId_doesNotThrow() {
		UserInfo a = UserInfo.builder().userId("u1").deptId(null).build();
		UserInfo b = UserInfo.builder().userId("u1").deptId(null).build();

		assertDoesNotThrow(() -> a.equals(b));
		assertEquals(a, b);
	}

	// ─── 建構子 ───────────────────────────────────────────────────────────────

	@Test
	void noArgsConstructor_allFieldsNull_doesNotThrow() {
		assertDoesNotThrow(() -> {
			UserInfo info = new UserInfo();
			assertNull(info.getUserId());
			assertNull(info.getUsername());
			assertNull(info.getTenantId());
			assertNull(info.getDeptId());
			assertNull(info.getDataScope());
		}, "@NoArgsConstructor 建立時所有欄位應為 null，不應拋出例外");
	}

	@Test
	void allArgsConstructor_setsAllFields() {
		UserInfo info = new UserInfo("uid-1", "alice", "tenant-99", 5L, "DEPT");

		assertEquals("uid-1", info.getUserId());
		assertEquals("alice", info.getUsername());
		assertEquals("tenant-99", info.getTenantId());
		assertEquals(5L, info.getDeptId());
		assertEquals("DEPT", info.getDataScope());
	}

	// ─── builder ─────────────────────────────────────────────────────────────

	@Test
	void builder_partialFields_unsetFieldsAreNull() {
		UserInfo info = UserInfo.builder().userId("u1").build();

		assertEquals("u1", info.getUserId());
		assertNull(info.getUsername());
		assertNull(info.getTenantId());
		assertNull(info.getDeptId());
		assertNull(info.getDataScope());
	}

}
