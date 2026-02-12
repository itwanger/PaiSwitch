package com.paicoding.paiswitch.repository;

import com.paicoding.paiswitch.domain.entity.UserConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserConfigRepository extends JpaRepository<UserConfig, Long> {

    Optional<UserConfig> findByUserId(Long userId);

    boolean existsByUserId(Long userId);
}
