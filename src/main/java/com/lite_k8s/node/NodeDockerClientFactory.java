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

/**
 * 노드별 DockerClient 생성 및 캐싱
 * TCP를 통해 원격 Docker 데몬에 연결 (SSH 노드는 터널 경유)
 */
@Slf4j
@Component
public class NodeDockerClientFactory {

    private final SshTunnelManager tunnelManager;
    private final ConcurrentHashMap<String, DockerClient> clientCache = new ConcurrentHashMap<>();

    public NodeDockerClientFactory(SshTunnelManager tunnelManager) {
        this.tunnelManager = tunnelManager;
    }

    public DockerClient createClient(Node node) {
        return clientCache.computeIfAbsent(node.getId(), id -> buildClient(node));
    }

    private DockerClient buildClient(Node node) {
        String dockerHost;

        if (node.isSsh()) {
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
            dockerHost = "tcp://" + node.getHost() + ":" + node.getPort();
        }

        log.debug("노드 DockerClient 생성: {} ({})", node.getName(), dockerHost);

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
    }
}
