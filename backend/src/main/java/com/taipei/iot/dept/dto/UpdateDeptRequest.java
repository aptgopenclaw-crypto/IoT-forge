package com.taipei.iot.dept.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
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
public class UpdateDeptRequest {

	@NotNull
	private Long deptId;

	@Size(max = 100)
	private String deptName;

	private Integer deptSort;

	@Min(0)
	@Max(1)
	private Short status;

}
