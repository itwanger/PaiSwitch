package com.paicoding.paiswitch.repository;

import com.paicoding.paiswitch.domain.entity.ConfigBackup;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ConfigBackupRepository extends JpaRepository<ConfigBackup, Long> {

    List<ConfigBackup> findByUserIdOrderByCreatedAtDesc(Long userId);

    Page<ConfigBackup> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);
}
