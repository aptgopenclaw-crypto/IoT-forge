package com.taipei.iot.announcement.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "announcement_depts")
@IdClass(AnnouncementDeptId.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnnouncementDept {

	@Id
	@Column(name = "announcement_id", nullable = false)
	private Long announcementId;

	@Id
	@Column(name = "dept_id", nullable = false)
	private Long deptId;

}
