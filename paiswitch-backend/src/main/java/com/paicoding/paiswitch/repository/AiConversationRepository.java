package com.paicoding.paiswitch.repository;

import com.paicoding.paiswitch.domain.entity.AiConversation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AiConversationRepository extends JpaRepository<AiConversation, Long> {

    List<AiConversation> findBySessionIdOrderByCreatedAtAsc(String sessionId);

    List<AiConversation> findByUserIdOrderByCreatedAtDesc(Long userId);
}
