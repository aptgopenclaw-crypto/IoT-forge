package com.taipei.iot.rbac.dto.request;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * [RBAC N-7] 驗證 AssignRolePermissionsRequest.permissionIds 使用 @NotEmpty， 拒絕 null 與空
 * list，防止意外清空角色所有權限。
 */
class AssignRolePermissionsRequestValidationTest {

	private static Validator validator;

	@BeforeAll
	static void setUp() {
		try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
			validator = factory.getValidator();
		}
	}

	@Test
	void nullPermissionIds_shouldFailValidation() {
		AssignRolePermissionsRequest request = AssignRolePermissionsRequest.builder().permissionIds(null).build();

		Set<ConstraintViolation<AssignRolePermissionsRequest>> violations = validator.validate(request);

		assertThat(violations).isNotEmpty();
		assertThat(violations.iterator().next().getMessage()).isEqualTo("permissionIds 不能為空");
	}

	@Test
	void emptyPermissionIds_shouldFailValidation() {
		AssignRolePermissionsRequest request = AssignRolePermissionsRequest.builder().permissionIds(List.of()).build();

		Set<ConstraintViolation<AssignRolePermissionsRequest>> violations = validator.validate(request);

		assertThat(violations).isNotEmpty();
		assertThat(violations.iterator().next().getMessage()).isEqualTo("permissionIds 不能為空");
	}

	@Test
	void nonEmptyPermissionIds_shouldPassValidation() {
		AssignRolePermissionsRequest request = AssignRolePermissionsRequest.builder()
			.permissionIds(List.of("PERM_001", "PERM_002"))
			.build();

		Set<ConstraintViolation<AssignRolePermissionsRequest>> violations = validator.validate(request);

		assertThat(violations).isEmpty();
	}

	@Test
	void singlePermissionId_shouldPassValidation() {
		AssignRolePermissionsRequest request = AssignRolePermissionsRequest.builder()
			.permissionIds(List.of("PERM_001"))
			.build();

		Set<ConstraintViolation<AssignRolePermissionsRequest>> violations = validator.validate(request);

		assertThat(violations).isEmpty();
	}

}
