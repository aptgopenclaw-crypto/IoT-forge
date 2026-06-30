package com.taipei.iot.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.library.Architectures.layeredArchitecture;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

/**
 * Architectural boundary rules that encode the L0–L4 layered architecture defined in
 * {@code 00-history/03-model/module-boundary-assessment.md}.
 *
 * <p>
 * <b>Layered structure (top → bottom, dependencies flow downward only):</b>
 *
 * <pre>
 *   L4 — 複合業務 / 管理門面  : platform, assettransfer, dispatch
 *   L3 — 領域服務             : announcement, device, schema, workflow, notification
 *   L2 — 身分與存取           : auth, user, rbac, dept
 *   L1 — 平台核心             : tenant, audit, setting, config
 *   L0 — 基礎層（純工具）     : common
 * </pre>
 *
 * <p>
 * <b>Rules enforced:</b>
 * <ol>
 * <li>{@link #layers_are_respected} — upper layers may NOT be accessed by lower layers
 * (strict: all upward violations resolved)</li>
 * <li>{@link #common_has_no_business_dependencies} — L0 ({@code common}) must not import
 * any business-layer module</li>
 * <li>{@link #no_cyclic_dependencies} — no module-level package cycles (strict: all
 * cycles resolved)</li>
 * </ol>
 */
@AnalyzeClasses(packages = "com.taipei.iot", importOptions = ImportOption.DoNotIncludeTests.class)
class LayeredArchitectureTest {

	// ─────────────────────────────────────────────────────────────────
	// Rule 1: Layered access — upper layers may only be reached from above
	// STRICT: all upward violations resolved (audit→dept/user via ports; config→auth by
	// moving SecurityConfig into the auth module). Any new upward dependency now fails.
	// ─────────────────────────────────────────────────────────────────

	@ArchTest
	static final ArchRule layers_are_respected = layeredArchitecture().consideringOnlyDependenciesInLayers()

		// Layer definitions
		.layer("L0_common")
		.definedBy("com.taipei.iot.common..")
		.layer("L1_platform")
		.definedBy("com.taipei.iot.tenant..", "com.taipei.iot.audit..", "com.taipei.iot.setting..",
				"com.taipei.iot.config..")
		.layer("L2_identity")
		.definedBy("com.taipei.iot.auth..", "com.taipei.iot.user..", "com.taipei.iot.rbac..", "com.taipei.iot.dept..")
		.layer("L3_domain")
		.definedBy("com.taipei.iot.announcement..", "com.taipei.iot.device..", "com.taipei.iot.schema..",
				"com.taipei.iot.workflow..", "com.taipei.iot.notification..")
		.layer("L4_facade")
		.definedBy("com.taipei.iot.platform..", "com.taipei.iot.assettransfer..", "com.taipei.iot.dispatch..")

		// Access constraints: upper layers may ONLY be accessed by layers above them
		// L0 has no constraint — everyone may depend on common
		.whereLayer("L1_platform")
		.mayOnlyBeAccessedByLayers("L2_identity", "L3_domain", "L4_facade")
		.whereLayer("L2_identity")
		.mayOnlyBeAccessedByLayers("L3_domain", "L4_facade")
		.whereLayer("L3_domain")
		.mayOnlyBeAccessedByLayers("L4_facade");

	// ─────────────────────────────────────────────────────────────────
	// Rule 2: common is the pure foundation — must not depend on business modules
	// ─────────────────────────────────────────────────────────────────

	@ArchTest
	static final ArchRule common_has_no_business_dependencies = com.tngtech.archunit.lang.syntax.ArchRuleDefinition
		.noClasses()
		.that()
		.resideInAPackage("com.taipei.iot.common..")
		.should()
		.dependOnClassesThat()
		.resideInAnyPackage("com.taipei.iot.tenant..", "com.taipei.iot.audit..", "com.taipei.iot.setting..",
				"com.taipei.iot.config..", "com.taipei.iot.auth..", "com.taipei.iot.user..", "com.taipei.iot.rbac..",
				"com.taipei.iot.dept..", "com.taipei.iot.announcement..", "com.taipei.iot.device..",
				"com.taipei.iot.schema..", "com.taipei.iot.workflow..", "com.taipei.iot.notification..",
				"com.taipei.iot.platform..", "com.taipei.iot.assettransfer..", "com.taipei.iot.dispatch..")
		.because("L0 (common) must stay pure — no business-layer dependencies allowed; "
				+ "see module-boundary-assessment.md §3");

	// ─────────────────────────────────────────────────────────────────
	// Rule 3: No package cycles between top-level business modules
	// STRICT: all 問題二 (platform cluster), 問題四 (device↔schema, device↔dispatch) and
	// the dept↔user cycle are resolved. The freeze is removed — any new module cycle now
	// fails the build.
	// ─────────────────────────────────────────────────────────────────

	@ArchTest
	static final ArchRule no_cyclic_dependencies = slices().matching("com.taipei.iot.(*)..").should().beFreeOfCycles();

}
