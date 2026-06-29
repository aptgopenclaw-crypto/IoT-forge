package com.taipei.iot.rbac.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserMenuDto {

	private Long menuId;

	private Long parentId;

	private String name;

	private String menuType;

	private String routeName;

	private String routePath;

	private String component;

	private String icon;

	private int sortOrder;

	private String redirect;

	private String scope;

	private List<UserMenuDto> children;

}
