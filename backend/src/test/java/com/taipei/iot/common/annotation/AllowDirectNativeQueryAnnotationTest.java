package com.taipei.iot.common.annotation;

import com.taipei.iot.auth.policy.PasswordPolicyDao;
import org.junit.jupiter.api.Test;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * F-1 contract test for {@link AllowDirectNativeQuery}.
 *
 * <p>
 * The annotation is the only opt-in exemption for the F-1 ArchUnit rule
 * ({@code ForbiddenNativeQueryArchTest}). Misconfiguring it (e.g. dropping
 * {@code @Retention(RUNTIME)} or making {@code reason()} optional) would silently break
 * the guardrail without breaking any business test. This class locks the contract
 * independently.
 */
class AllowDirectNativeQueryAnnotationTest {

	@Test
	void annotation_hasRuntimeRetention_soArchUnitCanSeeIt() {
		Retention retention = AllowDirectNativeQuery.class.getAnnotation(Retention.class);
		assertThat(retention).isNotNull();
		assertThat(retention.value()).isEqualTo(RetentionPolicy.RUNTIME);
	}

	@Test
	void annotation_targetsTypeAndMethod() {
		Target target = AllowDirectNativeQuery.class.getAnnotation(Target.class);
		assertThat(target).isNotNull();
		assertThat(target.value()).containsExactlyInAnyOrder(ElementType.TYPE, ElementType.METHOD);
	}

	@Test
	void reason_isMandatory_noDefaultValue() throws NoSuchMethodException {
		Method reason = AllowDirectNativeQuery.class.getDeclaredMethod("reason");
		assertThat(reason.getReturnType()).isEqualTo(String.class);
		assertThat(reason.getDefaultValue())
			.as("reason() must NOT have a default value — every exemption must explain itself")
			.isNull();
	}

	@Test
	void passwordPolicyDao_isAnnotated_withNonBlankReason() {
		AllowDirectNativeQuery marker = PasswordPolicyDao.class.getAnnotation(AllowDirectNativeQuery.class);
		assertThat(marker)
			.as("PasswordPolicyDao is the currently audited exemption; "
					+ "removing the annotation must be a deliberate, reviewed change")
			.isNotNull();
		assertThat(marker.reason()).as("reason() must clearly explain why TenantAwareQuery cannot be used")
			.isNotBlank();
	}

}
