package com.paicoding.paiswitch.domain.entity;

import com.paicoding.paiswitch.domain.enums.SwitchType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "switch_history")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SwitchHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "from_provider_id")
    private ModelProvider fromProvider;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "to_provider_id", nullable = false)
    private ModelProvider toProvider;

    @Enumerated(EnumType.STRING)
    @Column(name = "switch_type", nullable = false, length = 20)
    @Builder.Default
    private SwitchType switchType = SwitchType.MANUAL;

    @Column(name = "ai_prompt", columnDefinition = "text")
    private String aiPrompt;

    @Column(nullable = false)
    @Builder.Default
    private Boolean success = true;

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    @Column(name = "client_info")
    private String clientInfo;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
