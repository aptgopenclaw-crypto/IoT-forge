package com.taipei.iot.tenant;

/** 標記需要 tenant 過濾的 Entity */
public interface TenantAware {

	String getTenantId();

	void setTenantId(String tenantId);

}
