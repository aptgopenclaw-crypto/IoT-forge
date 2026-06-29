package com.taipei.iot.rbac.controller;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * [RBAC N-6] 驗證 PermissionController 有方法層 @PreAuthorize 做縱深防禦， 與 SecurityConfig
 * 的路由規則形成雙層保護。
 */
class PermissionControllerPreAuthorizeTest {

	@Test
	void listPermissions_shouldHavePreAuthorizeAnnotation() throws NoSuchMethodException {
		Method method = PermissionController.class.getMethod("listPermissions");
		PreAuthorize annotation = method.getAnnotation(PreAuthorize.class);

		assertThat(annotation).as("listPermissions() must have @PreAuthorize").isNotNull();
		assertThat(annotation.value()).isEqualTo("hasAuthority('ROLE_LIST')");
	}

	@Test
	void listPermissions_shouldHaveGetMapping() throws NoSuchMethodException {
		Method method = PermissionController.class.getMethod("listPermissions");
		GetMapping getMapping = method.getAnnotation(GetMapping.class);

		assertThat(getMapping).as("listPermissions() must have @GetMapping").isNotNull();
	}

	@Test
	void controller_shouldHaveCorrectRequestMapping() {
		RequestMapping requestMapping = PermissionController.class.getAnnotation(RequestMapping.class);

		assertThat(requestMapping).isNotNull();
		assertThat(requestMapping.value()).contains("/v1/auth/permissions");
	}

	@Test
	void preAuthorize_shouldUseRoleListAuthority() throws NoSuchMethodException {
		// Verify the authority matches what SecurityConfig enforces:
		// .requestMatchers(HttpMethod.GET,
		// "/v1/auth/permissions/**").hasAuthority("ROLE_LIST")
		Method method = PermissionController.class.getMethod("listPermissions");
		PreAuthorize annotation = method.getAnnotation(PreAuthorize.class);

		assertThat(annotation.value()).as("@PreAuthorize authority must match SecurityConfig's ROLE_LIST")
			.contains("ROLE_LIST");
	}

}
