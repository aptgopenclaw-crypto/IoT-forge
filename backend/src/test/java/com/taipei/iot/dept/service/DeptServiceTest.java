package com.taipei.iot.dept.service;

import com.taipei.iot.auth.entity.UserEntity;
import com.taipei.iot.auth.entity.UserTenantMappingEntity;
import com.taipei.iot.auth.repository.UserRepository;
import com.taipei.iot.auth.repository.UserTenantMappingRepository;
import com.taipei.iot.common.enums.ErrorCode;
import com.taipei.iot.common.exception.BusinessException;
import com.taipei.iot.dept.dto.CreateDeptRequest;
import com.taipei.iot.dept.dto.DeptDto;
import com.taipei.iot.dept.dto.DeptOptionVO;
import com.taipei.iot.dept.dto.UpdateDeptRequest;
import com.taipei.iot.dept.entity.DeptInfoEntity;
import com.taipei.iot.dept.repository.DeptInfoRepository;
import com.taipei.iot.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeptServiceTest {

	@InjectMocks
	private DeptService deptService;

	@Mock
	private DeptInfoRepository deptInfoRepository;

	@Mock
	private UserTenantMappingRepository userTenantMappingRepository;

	@Mock
	private UserRepository userRepository;

	private DeptInfoEntity root;

	private DeptInfoEntity child1;

	private DeptInfoEntity child2;

	@BeforeEach
	void setUp() {
		root = DeptInfoEntity.builder()
			.deptId(1L)
			.tenantId("TENANT_A")
			.pid(null)
			.deptName("總公司")
			.deptSort(1)
			.status((short) 1)
			.hierarchyPath("/1/")
			.build();
		child1 = DeptInfoEntity.builder()
			.deptId(2L)
			.tenantId("TENANT_A")
			.pid(1L)
			.deptName("研發部")
			.deptSort(1)
			.status((short) 1)
			.hierarchyPath("/1/2/")
			.build();
		child2 = DeptInfoEntity.builder()
			.deptId(3L)
			.tenantId("TENANT_A")
			.pid(1L)
			.deptName("營運部")
			.deptSort(2)
			.status((short) 1)
			.hierarchyPath("/1/3/")
			.build();
	}

	@AfterEach
	void tearDown() {
		TenantContext.clear();
	}

	@Test
	void getDeptTree_shouldReturnTreeStructure() {
		when(deptInfoRepository.findAllByStatusOrderByDeptSortAsc((short) 1)).thenReturn(List.of(root, child1, child2));

		List<DeptDto> tree = deptService.getDeptTree();

		assertEquals(1, tree.size());
		assertEquals("總公司", tree.get(0).getDeptName());
		assertEquals(2, tree.get(0).getChildren().size());
		assertEquals("研發部", tree.get(0).getChildren().get(0).getDeptName());
		assertEquals("營運部", tree.get(0).getChildren().get(1).getDeptName());
	}

	@Test
	void getDeptTree_emptyList_shouldReturnEmpty() {
		when(deptInfoRepository.findAllByStatusOrderByDeptSortAsc((short) 1)).thenReturn(Collections.emptyList());

		List<DeptDto> tree = deptService.getDeptTree();

		assertTrue(tree.isEmpty());
	}

	@Test
	void getDeptOptions_shouldReturnOptionTree() {
		when(deptInfoRepository.findAllByStatusOrderByDeptSortAsc((short) 1)).thenReturn(List.of(root, child1, child2));

		List<DeptOptionVO> options = deptService.getDeptOptions();

		assertEquals(1, options.size());
		assertEquals(1L, options.get(0).getValue());
		assertEquals("總公司", options.get(0).getLabel());
		assertEquals(2, options.get(0).getChildren().size());
	}

	@Test
	void getDeptById_shouldReturnDto() {
		when(deptInfoRepository.findByDeptId(1L)).thenReturn(Optional.of(root));

		DeptDto dto = deptService.getDeptById(1L);

		assertEquals(1L, dto.getId());
		assertEquals("總公司", dto.getDeptName());
		assertNull(dto.getPid());
	}

	@Test
	void getDeptById_notFound_shouldThrow() {
		when(deptInfoRepository.findByDeptId(999L)).thenReturn(Optional.empty());

		BusinessException ex = assertThrows(BusinessException.class, () -> deptService.getDeptById(999L));
		assertEquals(ErrorCode.DEPT_NOT_FOUND, ex.getErrorCode());
	}

	@Test
	void createDept_rootDept_shouldSucceed() {
		TenantContext.setCurrentTenantId("TENANT_A");

		when(deptInfoRepository.existsByTenantIdAndDeptNameAndPid("TENANT_A", "新部門", null)).thenReturn(false);
		when(deptInfoRepository.save(any(DeptInfoEntity.class))).thenAnswer(invocation -> {
			DeptInfoEntity entity = invocation.getArgument(0);
			if (entity.getDeptId() == null) {
				entity.setDeptId(10L);
			}
			return entity;
		});

		CreateDeptRequest request = CreateDeptRequest.builder().deptName("新部門").pid(null).deptSort(5).build();

		DeptDto result = deptService.createDept(request);

		assertEquals(10L, result.getId());
		assertEquals("新部門", result.getDeptName());
		assertEquals("/10/", result.getHierarchyPath());
		verify(deptInfoRepository, times(2)).save(any(DeptInfoEntity.class));
	}

	@Test
	void createDept_childDept_shouldBuildHierarchyPath() {
		TenantContext.setCurrentTenantId("TENANT_A");

		when(deptInfoRepository.existsByTenantIdAndDeptNameAndPid("TENANT_A", "子部門", 1L)).thenReturn(false);
		when(deptInfoRepository.findByDeptId(1L)).thenReturn(Optional.of(root));
		when(deptInfoRepository.save(any(DeptInfoEntity.class))).thenAnswer(invocation -> {
			DeptInfoEntity entity = invocation.getArgument(0);
			if (entity.getDeptId() == null) {
				entity.setDeptId(20L);
			}
			return entity;
		});

		CreateDeptRequest request = CreateDeptRequest.builder().deptName("子部門").pid(1L).deptSort(3).build();

		DeptDto result = deptService.createDept(request);

		assertEquals("/1/20/", result.getHierarchyPath());
	}

	@Test
	void createDept_duplicateName_shouldThrow() {
		TenantContext.setCurrentTenantId("TENANT_A");

		when(deptInfoRepository.existsByTenantIdAndDeptNameAndPid("TENANT_A", "研發部", 1L)).thenReturn(true);

		CreateDeptRequest request = CreateDeptRequest.builder().deptName("研發部").pid(1L).deptSort(1).build();

		BusinessException ex = assertThrows(BusinessException.class, () -> deptService.createDept(request));
		assertEquals(ErrorCode.DEPT_ALREADY_EXISTS, ex.getErrorCode());
	}

	@Test
	void updateDept_shouldSucceed() {
		when(deptInfoRepository.findByDeptId(2L)).thenReturn(Optional.of(child1));
		when(deptInfoRepository.existsByTenantIdAndDeptNameAndPid("TENANT_A", "新名稱", 1L)).thenReturn(false);
		when(deptInfoRepository.save(any(DeptInfoEntity.class))).thenAnswer(i -> i.getArgument(0));

		UpdateDeptRequest request = UpdateDeptRequest.builder()
			.deptId(2L)
			.deptName("新名稱")
			.deptSort(10)
			.status((short) 1)
			.build();

		DeptDto result = deptService.updateDept(request);

		assertEquals("新名稱", result.getDeptName());
		assertEquals(10, result.getDeptSort());
	}

	@Test
	void updateDept_duplicateName_shouldThrow() {
		when(deptInfoRepository.findByDeptId(2L)).thenReturn(Optional.of(child1));
		when(deptInfoRepository.existsByTenantIdAndDeptNameAndPid("TENANT_A", "營運部", 1L)).thenReturn(true);

		UpdateDeptRequest request = UpdateDeptRequest.builder().deptId(2L).deptName("營運部").build();

		BusinessException ex = assertThrows(BusinessException.class, () -> deptService.updateDept(request));
		assertEquals(ErrorCode.DEPT_ALREADY_EXISTS, ex.getErrorCode());
	}

	@Test
	void deleteDept_noChildrenNoUsers_shouldSucceed() {
		when(deptInfoRepository.findByDeptId(2L)).thenReturn(Optional.of(child1));
		when(deptInfoRepository.existsByPid(2L)).thenReturn(false);
		when(userTenantMappingRepository.findByTenantIdAndDeptIdAndEnabledTrue("TENANT_A", 2L))
			.thenReturn(Collections.emptyList());

		deptService.deleteDept(2L);

		verify(userTenantMappingRepository).clearDeptIdByTenantIdAndDeptId("TENANT_A", 2L);
		verify(deptInfoRepository).delete(child1);
	}

	@Test
	void deleteDept_hasChildren_shouldThrow() {
		when(deptInfoRepository.findByDeptId(1L)).thenReturn(Optional.of(root));
		when(deptInfoRepository.existsByPid(1L)).thenReturn(true);

		BusinessException ex = assertThrows(BusinessException.class, () -> deptService.deleteDept(1L));
		assertEquals(ErrorCode.DEPT_HAS_CHILDREN, ex.getErrorCode());
	}

	@Test
	void deleteDept_hasUsers_shouldThrow() {
		when(deptInfoRepository.findByDeptId(2L)).thenReturn(Optional.of(child1));
		when(deptInfoRepository.existsByPid(2L)).thenReturn(false);

		UserTenantMappingEntity mapping = UserTenantMappingEntity.builder()
			.userId("user-admin-001")
			.tenantId("TENANT_A")
			.roleId("ROLE_ADMIN")
			.deptId(2L)
			.enabled(true)
			.build();
		when(userTenantMappingRepository.findByTenantIdAndDeptIdAndEnabledTrue("TENANT_A", 2L))
			.thenReturn(List.of(mapping));

		UserEntity user = UserEntity.builder()
			.userId("user-admin-001")
			.displayName("測試用戶")
			.email("test@example.com")
			.passwordHash("hash")
			.build();
		when(userRepository.findAllById(List.of("user-admin-001"))).thenReturn(List.of(user));

		BusinessException ex = assertThrows(BusinessException.class, () -> deptService.deleteDept(2L));
		assertEquals(ErrorCode.DEPT_HAS_USERS, ex.getErrorCode());
		assertEquals("測試用戶", ex.getDetail());
	}

	@Test
	void deleteDept_allUsersDeleted_shouldSucceed() {
		when(deptInfoRepository.findByDeptId(2L)).thenReturn(Optional.of(child1));
		when(deptInfoRepository.existsByPid(2L)).thenReturn(false);

		UserTenantMappingEntity mapping = UserTenantMappingEntity.builder()
			.userId("user-viewer-001")
			.tenantId("TENANT_A")
			.roleId("ROLE_VIEWER")
			.deptId(2L)
			.enabled(true)
			.build();
		when(userTenantMappingRepository.findByTenantIdAndDeptIdAndEnabledTrue("TENANT_A", 2L))
			.thenReturn(List.of(mapping));

		UserEntity deletedUser = UserEntity.builder()
			.userId("user-viewer-001")
			.displayName("Viewer User")
			.email("viewer@test.com")
			.passwordHash("hash")
			.deleted(true)
			.build();
		when(userRepository.findAllById(List.of("user-viewer-001"))).thenReturn(List.of(deletedUser));

		deptService.deleteDept(2L);

		verify(userTenantMappingRepository).clearDeptIdByTenantIdAndDeptId("TENANT_A", 2L);
		verify(deptInfoRepository).delete(child1);
	}

	@Test
	void deleteDept_notFound_shouldThrow() {
		when(deptInfoRepository.findByDeptId(999L)).thenReturn(Optional.empty());

		BusinessException ex = assertThrows(BusinessException.class, () -> deptService.deleteDept(999L));
		assertEquals(ErrorCode.DEPT_NOT_FOUND, ex.getErrorCode());
	}

	// ──────────────── [N-3] TOCTOU race condition ────────────────

	@Test
	void createDept_concurrentDuplicate_shouldThrowBusinessException() {
		// Simulates TOCTOU race: exists-check returns false, but save hits UNIQUE
		// constraint
		TenantContext.setCurrentTenantId("TENANT_A");

		when(deptInfoRepository.existsByTenantIdAndDeptNameAndPid("TENANT_A", "競爭部門", null)).thenReturn(false);
		when(deptInfoRepository.save(any(DeptInfoEntity.class)))
			.thenThrow(new DataIntegrityViolationException("duplicate key"));

		CreateDeptRequest request = CreateDeptRequest.builder().deptName("競爭部門").pid(null).deptSort(1).build();

		BusinessException ex = assertThrows(BusinessException.class, () -> deptService.createDept(request));
		assertEquals(ErrorCode.DEPT_ALREADY_EXISTS, ex.getErrorCode());
	}

}
