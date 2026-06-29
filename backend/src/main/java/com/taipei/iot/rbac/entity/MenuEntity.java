package com.taipei.iot.rbac.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "menus")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MenuEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "menu_id")
	private Long menuId;

	@Column(name = "parent_id")
	private Long parentId;

	@Column(name = "name", length = 100, nullable = false)
	private String name;

	@Column(name = "menu_type", length = 20, nullable = false)
	private String menuType;

	@Column(name = "route_name", length = 100)
	private String routeName;

	@Column(name = "route_path", length = 200)
	private String routePath;

	@Column(name = "component", length = 200)
	private String component;

	@Column(name = "permission_code", length = 100)
	private String permissionCode;

	@Column(name = "icon", length = 50)
	private String icon;

	@Column(name = "sort_order")
	@Builder.Default
	private Integer sortOrder = 0;

	@Column(name = "visible")
	@Builder.Default
	private Boolean visible = true;

	@Column(name = "keep_alive")
	@Builder.Default
	private Boolean keepAlive = false;

	@Column(name = "redirect", length = 200)
	private String redirect;

	/** Menu scope: PLATFORM 仅平台可見、TENANT 租戶內、PUBLIC 任何認證使用者 */
	@Column(name = "scope", length = 20, nullable = false)
	@Builder.Default
	private String scope = "TENANT";

	@Column(name = "create_time", nullable = false, updatable = false)
	private LocalDateTime createTime;

	@Column(name = "update_time")
	private LocalDateTime updateTime;

}
