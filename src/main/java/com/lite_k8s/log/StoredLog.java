package com.lite_k8s.log;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 저장된 로그 엔티티
 */
@Data
@Builder
public class StoredLog {

    private String id;
    private String containerId;
    private String content;
    private LocalDateTime timestamp;
    private LocalDateTime storedAt;
}
