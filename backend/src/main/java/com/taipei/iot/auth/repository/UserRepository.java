package com.taipei.iot.auth.repository;

import com.taipei.iot.auth.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface UserRepository extends JpaRepository<UserEntity, String>, JpaSpecificationExecutor<UserEntity> {

	Optional<UserEntity> findByEmail(String email);

	boolean existsByEmail(String email);

}
