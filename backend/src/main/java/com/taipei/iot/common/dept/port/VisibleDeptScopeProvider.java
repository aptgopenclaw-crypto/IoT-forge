package com.taipei.iot.common.dept.port;

import java.util.List;

/**
 * Port exposing the current principal's visible department scope, owned by the
 * {@code dept} module. Lets lower-layer modules (e.g. {@code audit}) apply data-scope
 * filtering without depending upward on {@code dept}.
 */
public interface VisibleDeptScopeProvider {

	/**
	 * Resolve the department IDs visible to the current principal under their data scope.
	 * An empty list means {@code ALL} scope (no department restriction).
	 * @return visible department IDs, or empty for unrestricted scope
	 */
	List<Long> getVisibleDeptIds();

}
