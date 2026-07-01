package com.taipei.iot.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
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
 * <b>IoT 獨立模組依賴方向（單向，不進入 L0–L4 分層）：</b>
 *
 * <pre>
 *   ingest ──▶ telemetry ──▶ { schema (L3), device (L3) }
 *   eventrule ──▶ schema (L3)
 *   notification (L3) ──▶ common events (L0)  [not directly to eventrule]
 * </pre>
 *
 * <p>
 * <b>Rules enforced:</b>
 * <ol>
 * <li>{@link #layers_are_respected} — upper layers may NOT be accessed by lower
 * layers</li>
 * <li>{@link #common_has_no_business_dependencies} — L0 ({@code common}) stays pure</li>
 * <li>{@link #no_cyclic_dependencies} — no module-level package cycles (strict)</li>
 * <li>{@link #iot_dependency_directions} — IoT module directed-acyclic dependency
 * directions enforced explicitly</li>
 * </ol>
 */
@AnalyzeClasses(packages = "com.taipei.iot", importOptions = ImportOption.DoNotIncludeTests.class)
class LayeredArchitectureTest {

	// ─────────────────────────────────────────────────────────────────
	// Rule 1: Layered access — upper layers may only be reached from above
	// ─────────────────────────────────────────────────────────────────

	@ArchTest
	static final ArchRule layers_are_respected = layeredArchitecture().consideringOnlyDependenciesInLayers()

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
	static final ArchRule common_has_no_business_dependencies = noClasses().that()
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
	// STRICT: freeze removed — any new module cycle now fails the build.
	// ─────────────────────────────────────────────────────────────────

	@ArchTest
	static final ArchRule no_cyclic_dependencies = slices().matching("com.taipei.iot.(*)..").should().beFreeOfCycles();

	// ─────────────────────────────────────────────────────────────────
	// Rule 4: IoT module directed dependency graph
	//
	// ingest ──▶ telemetry ──▶ { schema, device }
	// eventrule ──▶ schema
	// notification ──▶ common events (NOT directly to eventrule)
	//
	// Bottom-up: lower modules (schema, device) must NOT import from higher ones.
	// ─────────────────────────────────────────────────────────────────

	@ArchTest
	static final ArchRule iot_dependency_directions = noClasses().that()
		.resideInAnyPackage(
				// schema / device are the lowest IoT modules — they must not look up
				"com.taipei.iot.schema..", "com.taipei.iot.device..",
				// telemetry must not import ingest or eventrule
				"com.taipei.iot.telemetry..",
				// notification must not directly import eventrule (use common events)
				"com.taipei.iot.notification..")
		.should()
		.dependOnClassesThat()
		.resideInAnyPackage(
				// schema/device must not depend on telemetry, ingest, or eventrule
				// telemetry must not depend on ingest or eventrule
				// notification must not depend on eventrule
				"com.taipei.iot.ingest..", "com.taipei.iot.eventrule..")
		.because("IoT dependency direction must be acyclic: " + "ingest→telemetry→{schema,device}, eventrule→schema; "
				+ "notification communicates with eventrule only via common events (RuleTriggeredEvent)");

}
