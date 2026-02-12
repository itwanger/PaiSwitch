package com.paicoding.paiswitch.repository;

import com.paicoding.paiswitch.domain.entity.ModelProvider;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ModelProviderRepository extends JpaRepository<ModelProvider, Long> {

    Optional<ModelProvider> findByCode(String code);

    List<ModelProvider> findByIsActiveTrueOrderBySortOrderAsc();

    List<ModelProvider> findByIsBuiltinTrueOrderBySortOrderAsc();

    boolean existsByCode(String code);
}
