package com.interview.modules.llmprovider.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "llm_global_setting")
public class LlmGlobalSettingEntity {

  public static final Long SINGLETON_ID = 1L;

  @Id
  private Long id;

  @Column(name = "default_chat_provider_id", nullable = false, length = 64)
  private String defaultChatProviderId;

  @Column(name = "default_embedding_provider_id", nullable = false, length = 64)
  private String defaultEmbeddingProviderId;

  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;

  @PrePersist
  void prePersist() {
    LocalDateTime now = LocalDateTime.now();
    createdAt = now;
    updatedAt = now;
  }

  @PreUpdate
  void preUpdate() {
    updatedAt = LocalDateTime.now();
  }
}
