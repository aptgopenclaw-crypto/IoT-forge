package com.taipei.iot.rbac.dto.request;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * [RBAC N-9] 驗證 UpdateRoleRequest.dataScope 使用 @NotBlank， PUT 語意下必須明確提供
 * dataScope（與 @PutMapping 完整資源替換一致）。
 */
class UpdateRoleRequestValidationTest {

	private static Validator validator;

	@BeforeAll
	static void setUp() {
		try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
			validator = factory.getValidator();
		}
	}

	private UpdateRoleRequest validRequest() {
		return UpdateRoleRequest.builder().name("Admin Role").enabled(true).dataScope("ALL").build();
	}

	@Test
	void nullDataScope_shouldFailValidation() {
		UpdateRoleRequest request = validRequest();
		request.setDataScope(null);

		Set<ConstraintViolation<UpdateRoleRequest>> violations = validator.validate(request);

		assertThat(violations).isNotEmpty();
		assertThat(violations).anyMatch(v -> v.getMessage().equals("dataScope 不能為空"));
	}

	@Test
	void emptyDataScope_shouldFailValidation() {
		UpdateRoleRequest request = validRequest();
		request.setDataScope("");

		Set<ConstraintViolation<UpdateRoleRequest>> violations = validator.validate(request);

		assertThat(violations).isNotEmpty();
	}

	@Test
	void invalidDataScope_shouldFailValidation() {
		UpdateRoleRequest request = validRequest();
		request.setDataScope("INVALID_VALUE");

		Set<ConstraintViolation<UpdateRoleRequest>> violations = validator.validate(request);

		assertThat(violations).isNotEmpty();
		assertThat(violations).anyMatch(v -> v.getMessage().contains("dataScope 必須為"));
	}

	@Test
	void dataScopeAll_shouldPassValidation() {
		UpdateRoleRequest request = validRequest();
		request.setDataScope("ALL");

		Set<ConstraintViolation<UpdateRoleRequest>> violations = validator.validate(request);

		assertThat(violations).isEmpty();
	}

	@Test
	void dataScopeThisLevel_shouldPassValidation() {
		UpdateRoleRequest request = validRequest();
		request.setDataScope("THIS_LEVEL");

		Set<ConstraintViolation<UpdateRoleRequest>> violations = validator.validate(request);

		assertThat(violations).isEmpty();
	}

	@Test
	void dataScopeThisLevelAndBelow_shouldPassValidation() {
		UpdateRoleRequest request = validRequest();
		request.setDataScope("THIS_LEVEL_AND_BELOW");

		Set<ConstraintViolation<UpdateRoleRequest>> violations = validator.validate(request);

		assertThat(violations).isEmpty();
	}

}
