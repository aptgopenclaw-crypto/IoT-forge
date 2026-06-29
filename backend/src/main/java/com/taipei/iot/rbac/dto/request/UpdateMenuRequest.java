package com.taipei.iot.rbac.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateMenuRequest {

	@NotNull
	private Long menuId;

	private Long parentId;

	@NotBlank
	private String name;

	@NotBlank
	@Pattern(regexp = "DIRECTORY|PAGE|BUTTON", message = "menuType 必須為 DIRECTORY、PAGE 或 BUTTON")
	private String menuType;

	private String routeName;

	private String routePath;

	private String component;

	@Pattern(regexp = "^$|^[A-Z][A-Z0-9_]*$", message = "permissionCode 必須為空或符合 UPPER_SNAKE_CASE")
	private String permissionCode;

	private String icon;

	private int sortOrder;

	private boolean visible;

	private boolean keepAlive;

	private String redirect;

	@Pattern(regexp = "PLATFORM|TENANT|PUBLIC", message = "scope 必須為 PLATFORM、TENANT 或 PUBLIC")
	private String scope;

}
