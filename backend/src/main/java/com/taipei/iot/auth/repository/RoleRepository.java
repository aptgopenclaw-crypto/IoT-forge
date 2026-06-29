package com.taipei.iot.auth.repository;

import com.taipei.iot.auth.entity.RoleEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RoleRepository extends JpaRepository<RoleEntity, String> {

	boolean existsByCode(String code);

	Optional<RoleEntity> findByCode(String code);

	List<RoleEntity> findAllByOrderByBuiltInDescCodeAsc();

	List<RoleEntity> findAllByOrderByBuiltInDescCreateTimeDesc();

}
