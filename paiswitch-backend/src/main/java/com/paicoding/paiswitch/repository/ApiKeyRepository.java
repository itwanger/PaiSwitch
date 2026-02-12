package com.paicoding.paiswitch.repository;

import com.paicoding.paiswitch.domain.entity.ApiKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ApiKeyRepository extends JpaRepository<ApiKey, Long> {

    Optional<ApiKey> findByUserIdAndProviderId(Long userId, Long providerId);

    Optional<ApiKey> findByUserIdAndProviderCode(Long userId, String providerCode);

    List<ApiKey> findByUserId(Long userId);

    void deleteByUserIdAndProviderId(Long userId, Long providerId);

    boolean existsByUserIdAndProviderId(Long userId, Long providerId);
}
