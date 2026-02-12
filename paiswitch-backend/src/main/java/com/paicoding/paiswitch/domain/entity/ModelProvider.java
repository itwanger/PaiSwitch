package com.paicoding.paiswitch.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "model_provider")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ModelProvider {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 500)
    private String description;

    @Column(name = "base_url", nullable = false)
    private String baseUrl;

    @Column(name = "model_name", nullable = false, length = 100)
    private String modelName;

    @Column(name = "model_name_small", length = 100)
    private String modelNameSmall;

    @Column(name = "is_builtin", nullable = false)
    @Builder.Default
    private Boolean isBuiltin = false;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "sort_order", nullable = false)
    @Builder.Default
    private Integer sortOrder = 0;

    @Column(name = "icon_url", length = 500)
    private String iconUrl;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
