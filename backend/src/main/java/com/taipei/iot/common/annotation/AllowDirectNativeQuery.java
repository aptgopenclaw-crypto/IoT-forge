package com.taipei.iot.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 標記允許直接呼叫 {@code EntityManager.createNativeQuery(...)} 的類別 / 方法， 跳過 F-1 ArchUnit
 * 規則的禁用檢查。
 *
 * <p>
 * 專為以下「<b>合法但繞過 tenant 隔離</b>」情境設計：
 * <ul>
 * <li>平台層 DAO（如 {@code PasswordPolicyDao}），以顯式 {@code tenant_id} 參數 處理跨租戶 / 平台 sentinel
 * 資料</li>
 * <li>{@code TenantAwareQuery} 本身的內部實作</li>
 * <li>migration / repair / one-shot 系統管理腳本（需配合 {@code SystemContext}）</li>
 * </ul>
 *
 * <p>
 * <b>使用本註解 == 你已通過 security review</b>。請在 {@link #reason()} 中 明確說明為何不能走
 * {@code TenantAwareQuery}，並於 PR description 取得另一位 reviewer 同意；否則應重構為走
 * {@code TenantAwareQuery.create()} 或 {@code createGlobal()}。
 *
 * <p>
 * F-1 ArchUnit 規則： <pre>
 *   ForbiddenNativeQueryArchTest.businessCodeMustNotCallCreateNativeQuery_unlessExempt
 * </pre>
 */
@Target({ ElementType.TYPE, ElementType.METHOD })
@Retention(RetentionPolicy.RUNTIME)
public @interface AllowDirectNativeQuery {

	/**
	 * 為何此處需要繞過 {@code TenantAwareQuery}。Code review 必看欄位。
	 */
	String reason();

}
