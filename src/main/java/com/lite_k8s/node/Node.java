package com.lite_k8s.node;

import com.lite_k8s.crypto.EncryptedStringConverter;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "nodes")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Node {

    @Id
    private String id;
    private String name;
    private String host;
    private int port;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private NodeConnectionType connectionType = NodeConnectionType.SSH;
    private int sshPort;
    private String sshUser;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(length = 1024)
    private String sshPassword;

    private String sshKeyPath;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(length = 1024)
    private String sshPassphrase;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private NodeStatus status = NodeStatus.UNKNOWN;

    public boolean isSsh() {
        return NodeConnectionType.SSH.equals(connectionType);
    }

    public boolean isSshProxy() {
        return NodeConnectionType.SSH_PROXY.equals(connectionType);
    }

    public boolean requiresTunnel() {
        return isSsh() || isSshProxy();
    }

    @Builder.Default
    private double cpuUsagePercent = 0.0;

    @Builder.Default
    private double memoryUsagePercent = 0.0;

    @Builder.Default
    private int runningContainers = 0;

    private LocalDateTime lastHeartbeat;
}
