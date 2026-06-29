package com.taipei.iot.rbac.dto.request;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * [RBAC N-10] 驗證 CreateMenuRequest / UpdateMenuRequest 的 permissionCode 必須為空字串或
 * UPPER_SNAKE_CASE 格式（null 允許，DIRECTORY 類可不提供）。
 */
class MenuRequestPermissionCodeValidationTest {

	private static Validator validator;

	@BeforeAll
	static void setUp() {
		try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
			validator = factory.getValidator();
		}
	}

	// === CreateMenuRequest ===

	private CreateMenuRequest validCreateRequest() {
		return CreateMenuRequest.builder().name("Test Menu").menuType("PAGE").permissionCode("USER_LIST").build();
	}

	@Test
	void create_nullPermissionCode_shouldPass() {
		CreateMenuRequest request = validCreateRequest();
		request.setPermissionCode(null);

		Set<ConstraintViolation<CreateMenuRequest>> violations = validator.validate(request);

		assertThat(violations).noneMatch(v -> v.getPropertyPath().toString().equals("permissionCode"));
	}

	@Test
	void create_emptyPermissionCode_shouldPass() {
		CreateMenuRequest request = validCreateRequest();
		request.setPermissionCode("");

		Set<ConstraintViolation<CreateMenuRequest>> violations = validator.validate(request);

		assertThat(violations).noneMatch(v -> v.getPropertyPath().toString().equals("permissionCode"));
	}

	@ParameterizedTest
	@ValueSource(strings = { "USER_LIST", "ROLE_MANAGE", "A", "DEPT_CREATE_SUB" })
	void create_validUpperSnakeCase_shouldPass(String code) {
		CreateMenuRequest request = validCreateRequest();
		request.setPermissionCode(code);

		Set<ConstraintViolation<CreateMenuRequest>> violations = validator.validate(request);

		assertThat(violations).noneMatch(v -> v.getPropertyPath().toString().equals("permissionCode"));
	}

	@ParameterizedTest
	@ValueSource(strings = { "user_list", "User_List", "USER LIST", "user-list", "_LEADING", "123ABC" })
	void create_invalidPermissionCode_shouldFail(String code) {
		CreateMenuRequest request = validCreateRequest();
		request.setPermissionCode(code);

		Set<ConstraintViolation<CreateMenuRequest>> violations = validator.validate(request);

		assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("permissionCode")
				&& v.getMessage().contains("permissionCode 必須為空或符合 UPPER_SNAKE_CASE"));
	}

	// === UpdateMenuRequest ===

	private UpdateMenuRequest validUpdateRequest() {
		return UpdateMenuRequest.builder()
			.menuId(1L)
			.name("Test Menu")
			.menuType("PAGE")
			.permissionCode("USER_LIST")
			.build();
	}

	@Test
	void update_nullPermissionCode_shouldPass() {
		UpdateMenuRequest request = validUpdateRequest();
		request.setPermissionCode(null);

		Set<ConstraintViolation<UpdateMenuRequest>> violations = validator.validate(request);

		assertThat(violations).noneMatch(v -> v.getPropertyPath().toString().equals("permissionCode"));
	}

	@Test
	void update_emptyPermissionCode_shouldPass() {
		UpdateMenuRequest request = validUpdateRequest();
		request.setPermissionCode("");

		Set<ConstraintViolation<UpdateMenuRequest>> violations = validator.validate(request);

		assertThat(violations).noneMatch(v -> v.getPropertyPath().toString().equals("permissionCode"));
	}

	@ParameterizedTest
	@ValueSource(strings = { "USER_LIST", "ROLE_MANAGE", "A", "DEPT_CREATE_SUB" })
	void update_validUpperSnakeCase_shouldPass(String code) {
		UpdateMenuRequest request = validUpdateRequest();
		request.setPermissionCode(code);

		Set<ConstraintViolation<UpdateMenuRequest>> violations = validator.validate(request);

		assertThat(violations).noneMatch(v -> v.getPropertyPath().toString().equals("permissionCode"));
	}

	@ParameterizedTest
	@ValueSource(strings = { "user_list", "User_List", "USER LIST", "user-list", "_LEADING", "123ABC" })
	void update_invalidPermissionCode_shouldFail(String code) {
		UpdateMenuRequest request = validUpdateRequest();
		request.setPermissionCode(code);

		Set<ConstraintViolation<UpdateMenuRequest>> violations = validator.validate(request);

		assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("permissionCode")
				&& v.getMessage().contains("permissionCode 必須為空或符合 UPPER_SNAKE_CASE"));
	}

}
