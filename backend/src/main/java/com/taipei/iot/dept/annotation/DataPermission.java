package com.taipei.iot.dept.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 【預留框架】宣告式資料範圍標記 — 標註於 Service 方法上，觸發
 * {@link com.taipei.iot.dept.aspect.DataPermissionAspect} 自動注入資料範圍過濾。
 *
 * <p>
 * <b>目前狀態（2026-05）：</b>無任何方法使用此註解；各模組採
 * {@link com.taipei.iot.dept.service.DataScopeHelper} 手動控制。 保留為日後統一改造時啟用。
 * </p>
 *
 * @see com.taipei.iot.dept.aspect.DataPermissionAspect
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface DataPermission {

	String deptIdField() default "deptId";

	String hierarchyPathField() default "hierarchyPath";

}
