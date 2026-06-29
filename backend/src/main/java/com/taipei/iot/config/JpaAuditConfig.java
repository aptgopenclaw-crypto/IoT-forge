package com.taipei.iot.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

@Configuration
@EnableJpaAuditing
public class JpaAuditConfig {

	/**
	 * 提供 JPA {@code @CreatedBy} / {@code @LastModifiedBy} 的當前使用者資訊。 未認證或 anonymous 使用者回傳
	 * {@link Optional#empty()}，Spring 會保留欄位既有值。
	 */
	@Bean
	public AuditorAware<String> auditorAware() {
		return () -> Optional.ofNullable(SecurityContextHolder.getContext().getAuthentication())
			.filter(auth -> auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal()))
			.map(Authentication::getName);
	}

}
