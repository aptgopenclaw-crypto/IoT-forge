package com.taipei.iot.user.service;

import com.taipei.iot.common.user.port.SuperAdminDirectory;
import com.taipei.iot.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * {@code user}-module adapter for {@link SuperAdminDirectory}, backed by
 * {@link UserRepository}.
 */
@Component
@RequiredArgsConstructor
public class SuperAdminDirectoryAdapter implements SuperAdminDirectory {

	private final UserRepository userRepository;

	@Override
	@Transactional(readOnly = true)
	public List<String> getSuperAdminUserIds() {
		return userRepository.findSuperAdminUserIds();
	}

}
