package com.taipei.iot.user.repository;

import com.taipei.iot.user.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<UserEntity, String>, JpaSpecificationExecutor<UserEntity> {

	Optional<UserEntity> findByEmail(String email);

	boolean existsByEmail(String email);

	@Query("select u.userId from UserEntity u where u.isSuperAdmin = true")
	List<String> findSuperAdminUserIds();

}
