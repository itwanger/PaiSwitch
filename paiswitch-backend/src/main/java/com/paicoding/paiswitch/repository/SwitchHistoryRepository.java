package com.paicoding.paiswitch.repository;

import com.paicoding.paiswitch.domain.entity.SwitchHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SwitchHistoryRepository extends JpaRepository<SwitchHistory, Long> {

    List<SwitchHistory> findByUserIdOrderByCreatedAtDesc(Long userId);

    Page<SwitchHistory> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);
}
