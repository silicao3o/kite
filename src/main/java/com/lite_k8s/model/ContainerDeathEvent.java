package com.lite_k8s.model;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
public class ContainerDeathEvent {
    private String containerId;
    private String containerName;
    private String imageName;
    private LocalDateTime deathTime;
    private Long exitCode;
    private boolean oomKilled;
    private String deathReason;
    private String lastLogs;
    private String action; // die, kill, stop, oom
    private Map<String, String> labels;
    private String nodeId; // null = 로컬 단일 모드
    private String nodeName; // 사람이 읽을 수 있는 노드 이름 (예: "res", "dev", "local")
}
