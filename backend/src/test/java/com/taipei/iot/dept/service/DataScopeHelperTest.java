package com.taipei.iot.dept.service;

import com.taipei.iot.common.dto.UserInfo;
import com.taipei.iot.common.util.SecurityContextUtils;
import com.taipei.iot.dept.entity.DeptInfoEntity;
import com.taipei.iot.dept.enums.DataScopeEnum;
import com.taipei.iot.dept.repository.DeptInfoRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DataScopeHelperTest {

	@InjectMocks
	private DataScopeHelper dataScopeHelper;

	@Mock
	private DeptInfoRepository deptInfoRepository;

	@Test
	void getVisibleDeptIds_allScope_returnsEmpty() {
		try (MockedStatic<SecurityContextUtils> utils = mockStatic(SecurityContextUtils.class)) {
			UserInfo user = UserInfo.builder().userId("u-admin").dataScope("ALL").deptId(1L).build();
			utils.when(SecurityContextUtils::getUserInfo).thenReturn(user);

			List<Long> result = dataScopeHelper.getVisibleDeptIds();

			assertTrue(result.isEmpty());
		}
	}

	@Test
	void getVisibleDeptIds_thisLevel_returnsOwnDeptOnly() {
		try (MockedStatic<SecurityContextUtils> utils = mockStatic(SecurityContextUtils.class)) {
			UserInfo user = UserInfo.builder().userId("u-off1").dataScope("THIS_LEVEL").deptId(10L).build();
			utils.when(SecurityContextUtils::getUserInfo).thenReturn(user);

			List<Long> result = dataScopeHelper.getVisibleDeptIds();

			assertEquals(List.of(10L), result);
		}
	}

	@Test
	void getVisibleDeptIds_thisLevelAndBelow_returnsSubtree() {
		try (MockedStatic<SecurityContextUtils> utils = mockStatic(SecurityContextUtils.class)) {
			UserInfo user = UserInfo.builder().userId("u-mgr").dataScope("THIS_LEVEL_AND_BELOW").deptId(1L).build();
			utils.when(SecurityContextUtils::getUserInfo).thenReturn(user);

			DeptInfoEntity root = DeptInfoEntity.builder().deptId(1L).hierarchyPath("/1/").build();
			DeptInfoEntity child1 = DeptInfoEntity.builder().deptId(10L).hierarchyPath("/1/10/").build();
			DeptInfoEntity child2 = DeptInfoEntity.builder().deptId(11L).hierarchyPath("/1/11/").build();
			DeptInfoEntity otherRoot = DeptInfoEntity.builder().deptId(20L).hierarchyPath("/20/").build();

			when(deptInfoRepository.findById(1L)).thenReturn(Optional.of(root));
			when(deptInfoRepository.findByHierarchyPathStartingWith("/1/")).thenReturn(List.of(root, child1, child2));

			List<Long> result = dataScopeHelper.getVisibleDeptIds();

			assertEquals(3, result.size());
			assertTrue(result.containsAll(List.of(1L, 10L, 11L)));
			assertFalse(result.contains(20L));
		}
	}

	@Test
	void getVisibleDeptIds_nullDeptId_returnsImpossible() {
		try (MockedStatic<SecurityContextUtils> utils = mockStatic(SecurityContextUtils.class)) {
			UserInfo user = UserInfo.builder().userId("u-no-dept").dataScope("THIS_LEVEL").deptId(null).build();
			utils.when(SecurityContextUtils::getUserInfo).thenReturn(user);

			List<Long> result = dataScopeHelper.getVisibleDeptIds();

			assertEquals(List.of(-1L), result);
		}
	}

	@Test
	void getVisibleDeptIds_nullUser_returnsEmpty() {
		try (MockedStatic<SecurityContextUtils> utils = mockStatic(SecurityContextUtils.class)) {
			utils.when(SecurityContextUtils::getUserInfo).thenReturn(null);

			List<Long> result = dataScopeHelper.getVisibleDeptIds();

			assertTrue(result.isEmpty());
		}
	}

	@Test
	void isDeptInScope_allScope_alwaysTrue() {
		try (MockedStatic<SecurityContextUtils> utils = mockStatic(SecurityContextUtils.class)) {
			UserInfo user = UserInfo.builder().userId("u-admin").dataScope("ALL").deptId(1L).build();
			utils.when(SecurityContextUtils::getUserInfo).thenReturn(user);

			assertTrue(dataScopeHelper.isDeptInScope(99L));
		}
	}

	@Test
	void isDeptInScope_thisLevel_ownDeptTrue() {
		try (MockedStatic<SecurityContextUtils> utils = mockStatic(SecurityContextUtils.class)) {
			UserInfo user = UserInfo.builder().userId("u-off1").dataScope("THIS_LEVEL").deptId(10L).build();
			utils.when(SecurityContextUtils::getUserInfo).thenReturn(user);

			assertTrue(dataScopeHelper.isDeptInScope(10L));
		}
	}

	@Test
	void isDeptInScope_thisLevel_otherDeptFalse() {
		try (MockedStatic<SecurityContextUtils> utils = mockStatic(SecurityContextUtils.class)) {
			UserInfo user = UserInfo.builder().userId("u-off1").dataScope("THIS_LEVEL").deptId(10L).build();
			utils.when(SecurityContextUtils::getUserInfo).thenReturn(user);

			assertFalse(dataScopeHelper.isDeptInScope(11L));
		}
	}

	@Test
	void isDeptInScope_nullTargetDept_alwaysTrue() {
		try (MockedStatic<SecurityContextUtils> utils = mockStatic(SecurityContextUtils.class)) {
			UserInfo user = UserInfo.builder().userId("u-off1").dataScope("THIS_LEVEL").deptId(10L).build();
			utils.when(SecurityContextUtils::getUserInfo).thenReturn(user);

			assertTrue(dataScopeHelper.isDeptInScope(null));
		}
	}

}
