package com.taipei.iot.dept.service;

import com.taipei.iot.common.dept.port.VisibleDeptScopeProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * {@code dept}-module adapter for {@link VisibleDeptScopeProvider}, delegating to
 * {@link DataScopeHelper} which owns the data-scope resolution logic.
 */
@Component
@RequiredArgsConstructor
public class VisibleDeptScopeAdapter implements VisibleDeptScopeProvider {

	private final DataScopeHelper dataScopeHelper;

	@Override
	public List<Long> getVisibleDeptIds() {
		return dataScopeHelper.getVisibleDeptIds();
	}

}
