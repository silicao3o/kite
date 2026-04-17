package com.lite_k8s.update;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "image_update_history")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImageUpdateHistoryEntity {

    public enum Status {
        DETECTED, SUCCESS, FAILED
    }

    @Id
    @Builder.Default
    private String id = UUID.randomUUID().toString();

    @Column(nullable = false)
    private String watchId;

    @Column(nullable = false)
    private String image;

    private String tag;

    private String previousDigest;

    private String newDigest;

    @Enumerated(EnumType.STRING)
    private Status status;

    private String nodeName;

    private String containerName;

    private String message;

    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
