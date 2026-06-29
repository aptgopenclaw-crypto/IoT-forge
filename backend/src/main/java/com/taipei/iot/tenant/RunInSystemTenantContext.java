package com.taipei.iot.tenant;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * [Tenant v2 T-13] 將標註的方法整段包在
 * {@link TenantContext#runInSystemContext(java.util.function.Supplier)} 之內執行：方法執行期間
 * {@link TenantContext} 為 SYSTEM，結束後自動恢復先前 context。
 *
 * <p>
 * 由 {@link TenantSystemContextAspect} 透過 Spring AOP 實作。
 *
 * <p>
 * <b>使用限制（Spring AOP 通則）</b>：
 * <ul>
 * <li>必須標註在 <b>Spring-managed bean 的 public 方法</b>，否則 AOP proxy 不生效。</li>
 * <li>同類內部自呼叫（self-invocation）不會觸發 aspect；跨類呼叫才有效。</li>
 * <li>需要 inline 或 lambda 範圍切系統 context 的情境，請改用
 * {@link TenantContext#runInSystemContext(Runnable)} /
 * {@link TenantContext#runInSystemContext(java.util.function.Supplier)}。</li>
 * </ul>
 *
 * <p>
 * 典型場景：跨租戶的排程 ({@code @Scheduled})、async 寫入 ({@code @Async}) 的進入點。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RunInSystemTenantContext {

}
