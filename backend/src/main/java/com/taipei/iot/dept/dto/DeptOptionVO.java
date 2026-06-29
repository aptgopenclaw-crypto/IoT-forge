package com.taipei.iot.dept.dto;

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
public class DeptOptionVO {

	private Long value;

	private Long pid;

	private String label;

	private List<DeptOptionVO> children;

}
