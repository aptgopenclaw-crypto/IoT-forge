package com.taipei.iot.dept.dto;

import jakarta.validation.constraints.NotBlank;
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
public class CreateDeptRequest {

	@NotBlank
	@Size(max = 100)
	private String deptName;

	private Long pid;

	private Integer deptSort;

}
