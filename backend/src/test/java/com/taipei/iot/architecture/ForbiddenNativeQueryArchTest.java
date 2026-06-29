package com.taipei.iot.architecture;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * F-1 ArchUnit guard — forbids every business-code class from calling
 * {@link jakarta.persistence.EntityManager#createNativeQuery(String)} (and its overloads)
 * directly.
 *
 * <p>
 * The canonical, tenant-safe entry point is
 * {@code com.taipei.iot.common.util.TenantAwareQuery#create(EntityManager, String)} (or
 * {@code createGlobal(...)} for platform-scoped queries). Bypassing this wrapper means
 * bypassing Hibernate's {@code @Filter(name="tenantFilter")} and the
 * {@code TenantFilterAspect}, which leaks tenant isolation. This rule is the rigid
 * guardrail behind N-3.
 *
 * <p>
 * <b>Two legitimate exemptions</b> (both opt-in, both reviewed):
 * <ul>
 * <li>{@code TenantAwareQuery} itself — the wrapper implementation must call
 * {@code EntityManager.createNativeQuery} once.</li>
 * <li>Any class or method annotated with {@link AllowDirectNativeQuery} (carries a
 * mandatory {@code reason}). Currently: {@code PasswordPolicyDao} (cross-tenant +
 * platform sentinel by design).</li>
 * </ul>
 *
 * <p>
 * If you hit this rule, refactor to {@code TenantAwareQuery.create(...)}. If you
 * genuinely cannot (e.g. cross-tenant admin work), annotate the class with
 * {@code @AllowDirectNativeQuery(reason = "...")} and get a second reviewer to sign off.
 */
class ForbiddenNativeQueryArchTest {

	private static final String TENANT_AWARE_QUERY_FQCN = "com.taipei.iot.common.util.TenantAwareQuery";

	private static final String ALLOW_DIRECT_FQCN = "com.taipei.iot.common.annotation.AllowDirectNativeQuery";

	private static final String ENTITY_MANAGER_FQCN = "jakarta.persistence.EntityManager";

	@Test
	void businessCodeMustNotCallCreateNativeQuery_unlessExempt() {
		ArchRule rule = noClasses().that(new com.tngtech.archunit.base.DescribedPredicate<JavaClass>(
				"are business code (not TenantAwareQuery, not @AllowDirectNativeQuery)") {
			@Override
			public boolean test(JavaClass javaClass) {
				if (javaClass.getFullName().equals(TENANT_AWARE_QUERY_FQCN)) {
					return false;
				}
				return !javaClass.isAnnotatedWith(ALLOW_DIRECT_FQCN);
			}
		})
			.should(callCreateNativeQueryOnEntityManager().as("not call EntityManager.createNativeQuery directly — "
					+ "use TenantAwareQuery.create() / createGlobal() instead"))
			.because("F-1: enforce tenant isolation via TenantAwareQuery; "
					+ "see @AllowDirectNativeQuery for the audited exemption path");

		rule.check(new ClassFileImporter().withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
			.importPackages("com.taipei.iot"));
	}

	private static ArchCondition<JavaClass> callCreateNativeQueryOnEntityManager() {
		return new ArchCondition<>("call EntityManager.createNativeQuery") {
			@Override
			public void check(JavaClass item, ConditionEvents events) {
				for (JavaMethodCall call : item.getMethodCallsFromSelf()) {
					if (!"createNativeQuery".equals(call.getTarget().getName())) {
						continue;
					}
					String ownerName = call.getTarget().getOwner().getName();
					if (!ownerName.equals(ENTITY_MANAGER_FQCN)) {
						continue;
					}
					if (call.getOrigin().isAnnotatedWith(ALLOW_DIRECT_FQCN)) {
						continue;
					}
					String message = String.format("%s calls EntityManager.createNativeQuery in %s", item.getFullName(),
							call.getSourceCodeLocation());
					events.add(SimpleConditionEvent.satisfied(call, message));
				}
			}
		};
	}

}
