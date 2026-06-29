package com.taipei.iot.dept.aspect;

import com.taipei.iot.common.dto.UserInfo;
import com.taipei.iot.common.util.SecurityContextUtils;
import com.taipei.iot.dept.annotation.DataPermission;
import com.taipei.iot.dept.context.DataScopeContext;
import com.taipei.iot.dept.context.DataScopeFilter;
import com.taipei.iot.dept.entity.DeptInfoEntity;
import com.taipei.iot.dept.repository.DeptInfoRepository;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DataPermissionAspectTest {

	@InjectMocks
	private DataPermissionAspect aspect;

	@Mock
	private DeptInfoRepository deptInfoRepository;

	@Mock
	private ProceedingJoinPoint pjp;

	@Mock
	private DataPermission dataPermission;

	private MockedStatic<SecurityContextUtils> securityContextMock;

	@BeforeEach
	void setUp() {
		securityContextMock = mockStatic(SecurityContextUtils.class);
		when(dataPermission.deptIdField()).thenReturn("deptId");
		when(dataPermission.hierarchyPathField()).thenReturn("hierarchyPath");
	}

	@AfterEach
	void tearDown() {
		securityContextMock.close();
		DataScopeContext.clear();
	}

	@Test
	void enforce_ALL_shouldNotSetFilter() throws Throwable {
		UserInfo user = UserInfo.builder().userId("user-001").deptId(1L).dataScope("ALL").build();
		securityContextMock.when(SecurityContextUtils::getUserInfo).thenReturn(user);

		when(pjp.proceed()).thenReturn("result");

		Object result = aspect.enforce(pjp, dataPermission);

		assertEquals("result", result);
		assertNull(DataScopeContext.get()); // cleared in finally
		verify(pjp).proceed();
	}

	@Test
	void enforce_THIS_LEVEL_shouldSetExactFilter() throws Throwable {
		UserInfo user = UserInfo.builder().userId("user-001").deptId(2L).dataScope("THIS_LEVEL").build();
		securityContextMock.when(SecurityContextUtils::getUserInfo).thenReturn(user);

		final DataScopeFilter[] capturedFilter = new DataScopeFilter[1];
		when(pjp.proceed()).thenAnswer(invocation -> {
			capturedFilter[0] = DataScopeContext.get();
			return "result";
		});

		Object result = aspect.enforce(pjp, dataPermission);

		assertEquals("result", result);
		assertNotNull(capturedFilter[0]);
		assertEquals(DataScopeFilter.FilterType.EXACT, capturedFilter[0].getType());
		assertEquals("deptId", capturedFilter[0].getFieldName());
		assertEquals(2L, capturedFilter[0].getValue());
	}

	@Test
	void enforce_THIS_LEVEL_AND_BELOW_shouldSetHierarchyPrefixFilter() throws Throwable {
		UserInfo user = UserInfo.builder().userId("user-001").deptId(1L).dataScope("THIS_LEVEL_AND_BELOW").build();
		securityContextMock.when(SecurityContextUtils::getUserInfo).thenReturn(user);

		DeptInfoEntity dept = DeptInfoEntity.builder().deptId(1L).hierarchyPath("/1/").build();
		when(deptInfoRepository.findById(1L)).thenReturn(Optional.of(dept));

		final DataScopeFilter[] capturedFilter = new DataScopeFilter[1];
		when(pjp.proceed()).thenAnswer(invocation -> {
			capturedFilter[0] = DataScopeContext.get();
			return "result";
		});

		Object result = aspect.enforce(pjp, dataPermission);

		assertEquals("result", result);
		assertNotNull(capturedFilter[0]);
		assertEquals(DataScopeFilter.FilterType.HIERARCHY_PREFIX, capturedFilter[0].getType());
		assertEquals("hierarchyPath", capturedFilter[0].getHierarchyPathField());
		assertEquals("/1/", capturedFilter[0].getHierarchyPathPrefix());
	}

	@Test
	void enforce_nullDeptId_shouldSetImpossibleFilter() throws Throwable {
		UserInfo user = UserInfo.builder().userId("user-001").deptId(null).dataScope("THIS_LEVEL").build();
		securityContextMock.when(SecurityContextUtils::getUserInfo).thenReturn(user);

		final DataScopeFilter[] capturedFilter = new DataScopeFilter[1];
		when(pjp.proceed()).thenAnswer(invocation -> {
			capturedFilter[0] = DataScopeContext.get();
			return "result";
		});

		Object result = aspect.enforce(pjp, dataPermission);

		assertEquals("result", result);
		assertNotNull(capturedFilter[0]);
		assertEquals(DataScopeFilter.FilterType.EXACT, capturedFilter[0].getType());
		assertEquals(-1L, capturedFilter[0].getValue());
	}

	@Test
	void enforce_THIS_LEVEL_AND_BELOW_hierarchyPathNotFound_shouldSetImpossibleFilter() throws Throwable {
		UserInfo user = UserInfo.builder().userId("user-001").deptId(999L).dataScope("THIS_LEVEL_AND_BELOW").build();
		securityContextMock.when(SecurityContextUtils::getUserInfo).thenReturn(user);

		when(deptInfoRepository.findById(999L)).thenReturn(Optional.empty());

		final DataScopeFilter[] capturedFilter = new DataScopeFilter[1];
		when(pjp.proceed()).thenAnswer(invocation -> {
			capturedFilter[0] = DataScopeContext.get();
			return "result";
		});

		Object result = aspect.enforce(pjp, dataPermission);

		assertNotNull(capturedFilter[0]);
		assertEquals(DataScopeFilter.FilterType.EXACT, capturedFilter[0].getType());
		assertEquals(-1L, capturedFilter[0].getValue());
	}

	@Test
	void enforce_nullUserInfo_shouldSetImpossibleFilter() throws Throwable {
		securityContextMock.when(SecurityContextUtils::getUserInfo).thenReturn(null);

		final DataScopeFilter[] capturedFilter = new DataScopeFilter[1];
		when(pjp.proceed()).thenAnswer(invocation -> {
			capturedFilter[0] = DataScopeContext.get();
			return "result";
		});

		Object result = aspect.enforce(pjp, dataPermission);

		assertNotNull(capturedFilter[0]);
		assertEquals(DataScopeFilter.FilterType.EXACT, capturedFilter[0].getType());
		assertEquals(-1L, capturedFilter[0].getValue());
	}

	@Test
	void enforce_shouldClearContextInFinally() throws Throwable {
		UserInfo user = UserInfo.builder().userId("user-001").deptId(2L).dataScope("THIS_LEVEL").build();
		securityContextMock.when(SecurityContextUtils::getUserInfo).thenReturn(user);

		when(pjp.proceed()).thenThrow(new RuntimeException("test error"));

		assertThrows(RuntimeException.class, () -> aspect.enforce(pjp, dataPermission));

		// DataScopeContext should be cleared even on exception
		assertNull(DataScopeContext.get());
	}

}
