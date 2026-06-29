package com.taipei.iot.device.repository;

import com.taipei.iot.device.entity.Device;
import com.taipei.iot.device.enums.DeviceStatus;
import com.taipei.iot.tenant.TenantScopedRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface DeviceRepository extends JpaRepository<Device, Long>, TenantScopedRepository {

	Optional<Device> findByTenantIdAndDeviceCode(String tenantId, String deviceCode);

	@Query("""
			SELECT d FROM Device d
			WHERE (:deviceType IS NULL OR d.deviceType = :deviceType)
			  AND (:status IS NULL OR d.status = :status)
			  AND (:keyword IS NULL OR LOWER(d.deviceCode) LIKE LOWER(CONCAT('%', CAST(:keyword AS string), '%'))
			       OR LOWER(d.deviceName) LIKE LOWER(CONCAT('%', CAST(:keyword AS string), '%')))
			  AND (:deptIds IS NULL OR d.deptId IN :deptIds)
			""")
	Page<Device> findByFilters(@Param("deviceType") String deviceType, @Param("status") DeviceStatus status,
			@Param("keyword") String keyword, @Param("deptIds") Collection<Long> deptIds, Pageable pageable);

	@Query("""
			SELECT d FROM Device d
			WHERE (:deviceType IS NULL OR d.deviceType = :deviceType)
			  AND (:status IS NULL OR d.status = :status)
			  AND (:keyword IS NULL OR LOWER(d.deviceCode) LIKE LOWER(CONCAT('%', CAST(:keyword AS string), '%'))
			       OR LOWER(d.deviceName) LIKE LOWER(CONCAT('%', CAST(:keyword AS string), '%')))
			  AND (:deptIds IS NULL OR d.deptId IN :deptIds)
			ORDER BY d.id
			""")
	List<Device> findAllByFilters(@Param("deviceType") String deviceType, @Param("status") DeviceStatus status,
			@Param("keyword") String keyword, @Param("deptIds") Collection<Long> deptIds);

	long countByCircuitId(Long circuitId);

	boolean existsByParentDeviceId(Long parentDeviceId);

	long countByParentDeviceIdAndStatusNot(Long parentDeviceId, DeviceStatus status);

	List<Device> findByParentDeviceIdAndStatusNot(Long parentDeviceId, DeviceStatus status);

	List<Device> findByParentDeviceId(Long parentDeviceId);

	@Query(value = """
			WITH RECURSIVE ancestors AS (
			    SELECT id, parent_device_id FROM devices WHERE id = :parentId
			    UNION ALL
			    SELECT d.id, d.parent_device_id
			    FROM devices d JOIN ancestors a ON d.id = a.parent_device_id
			)
			SELECT CASE WHEN EXISTS (SELECT 1 FROM ancestors WHERE id = :childId) THEN true ELSE false END
			""", nativeQuery = true)
	boolean checkCircularReference(@Param("childId") Long childId, @Param("parentId") Long parentId);

	Optional<Device> findByDeviceToken(String deviceToken);

	@Query("""
			SELECT d FROM Device d
			WHERE d.deviceToken IS NOT NULL
			  AND d.lastHeartbeatAt IS NOT NULL
			  AND d.lastHeartbeatAt < :threshold
			""")
	List<Device> findOfflineDevices(@Param("threshold") LocalDateTime threshold);

	long countByDeviceTypeAndStatusNot(String deviceType, DeviceStatus status);

	long countByDeviceType(String deviceType);

	List<Device> findByDeptId(Long deptId);

	long countByStatusNot(DeviceStatus status);

}
