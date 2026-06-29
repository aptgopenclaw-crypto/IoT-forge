package com.taipei.iot.rbac.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "permissions")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PermissionEntity {

	@Id
	@Column(name = "permission_id", length = 50)
	private String permissionId;

	@Column(name = "code", length = 100, nullable = false, unique = true)
	private String code;

	@Column(name = "name", length = 200, nullable = false)
	private String name;

	@Column(name = "group_name", length = 100)
	private String groupName;

	@Column(name = "sort_order")
	private Integer sortOrder;

}
