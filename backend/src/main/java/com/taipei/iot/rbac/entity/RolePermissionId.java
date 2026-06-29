package com.taipei.iot.rbac.entity;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

/**
 * 角色-權限複合主鍵。
 * <p>
 * 目前主鍵為 (roleId, permissionId)，tenantId 不包含在 PK 中。 這代表同一 role+permission 組合只能有一筆記錄（全域設定）。
 * 若未來需要租戶級權限覆寫（同一組合存在全域版 tenantId=NULL 與租戶版 tenantId=X）， 需將 tenantId 加入此複合主鍵並配合 DB
 * migration 調整。
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class RolePermissionId implements Serializable {

	private String roleId;

	private String permissionId;

}
