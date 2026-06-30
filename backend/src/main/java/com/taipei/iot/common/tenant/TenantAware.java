package com.taipei.iot.common.tenant;

/** 標記需要 tenant 過濾的 Entity */
public interface TenantAware {

	String getTenantId();

	void setTenantId(String tenantId);

}
