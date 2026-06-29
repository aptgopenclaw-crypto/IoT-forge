package com.taipei.iot.tenant;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * [Tenant v2 T-13] 實作 {@link RunInSystemTenantContext}：用
 * {@link TenantContext#runInSystemContext} 包裹標註方法，確保 SYSTEM context 進入/離開絕對成對。
 *
 * <p>
 * 必須比 {@link TenantFilterAspect} 早執行，否則 repository 呼叫進到 filter aspect 時 SYSTEM context
 * 還沒設好。Spring AOP 預設順序未定義，所以這裡顯式給 {@link Order} 較高優先序 （數字越小越外層）。
 */
@Aspect
@Component
@Order(0)
public class TenantSystemContextAspect {

	@Around("@annotation(com.taipei.iot.tenant.RunInSystemTenantContext)")
	public Object aroundSystemContext(ProceedingJoinPoint pjp) throws Throwable {
		try {
			return TenantContext.runInSystemContext(() -> {
				try {
					return pjp.proceed();
				}
				catch (RuntimeException | Error e) {
					throw e;
				}
				catch (Throwable t) {
					throw new CheckedAroundException(t);
				}
			});
		}
		catch (CheckedAroundException wrapper) {
			throw wrapper.getCause();
		}
	}

	/** 內部使用：把 checked exception 穿過 Supplier 邊界後再解開，呼叫端不會看到此型別。 */
	private static final class CheckedAroundException extends RuntimeException {

		CheckedAroundException(Throwable cause) {
			super(cause);
		}

	}

}
