package com.lite_k8s.node;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.zerodep.ZerodepDockerHttpClient;
import com.jcraft.jsch.JSchException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class NodeDockerClientFactory {

    private final SshTunnelManager tunnelManager;
    private final ConcurrentHashMap<String, DockerClient> clientCache = new ConcurrentHashMap<>();
    private final java.util.Set<String> failedNodes = java.util.Collections.newSetFromMap(new ConcurrentHashMap<>());

    public NodeDockerClientFactory(SshTunnelManager tunnelManager) {
        this.tunnelManager = tunnelManager;
    }

    public DockerClient createClient(Node node) {
        if (failedNodes.contains(node.getId())) {
            throw new RuntimeException("노드 연결 실패 (스킵): " + node.getName());
        }
        return clientCache.computeIfAbsent(node.getId(), id -> {
            try {
                return buildClient(node);
            } catch (Exception e) {
                failedNodes.add(node.getId());
                throw e;
            }
        });
    }

    private DockerClient buildClient(Node node) {
        String dockerHost;

        if (node.isSshProxy()) {
            try {
                tunnelManager.openProxyTunnel(node);
            } catch (JSchException e) {
                log.error("SSH 프록시 터널 연결 실패 [{}] host={} via CP — {}",
                        node.getName(), node.getHost(), e.getMessage());
                throw new RuntimeException("SSH 프록시 터널 연결 실패: " + node.getId(), e);
            }
            int localPort = tunnelManager.allocateLocalPort(node.getId());
            dockerHost = "tcp://localhost:" + localPort;
        } else if (node.isSsh()) {
            try {
                tunnelManager.openTunnel(node);
            } catch (JSchException e) {
                log.error("SSH 터널 연결 실패 [{}] user={} host={}:{} keyPath={} — {}",
                        node.getName(), node.getSshUser(), node.getHost(), node.getSshPort(),
                        node.getSshKeyPath(), e.getMessage());
                throw new RuntimeException("SSH 터널 연결 실패: " + node.getId(), e);
            }
            int localPort = tunnelManager.allocateLocalPort(node.getId());
            dockerHost = "tcp://localhost:" + localPort;
        } else {
            throw new IllegalArgumentException("지원하지 않는 연결 타입: " + node.getConnectionType());
        }

        log.info("노드 DockerClient 생성: {} → {}", node.getName(), dockerHost);

        DefaultDockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder()
                .withDockerHost(dockerHost)
                .build();

        ZerodepDockerHttpClient httpClient = new ZerodepDockerHttpClient.Builder()
                .dockerHost(URI.create(dockerHost))
                .build();

        return DockerClientImpl.getInstance(config, httpClient);
    }

    public void evict(String nodeId) {
        DockerClient client = clientCache.remove(nodeId);
        if (client != null) {
            try { client.close(); } catch (Exception ignored) {}
        }
        failedNodes.remove(nodeId);
        tunnelManager.closeTunnel(nodeId);
    }
}
