package com.lite_k8s.node;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class SshTunnelManager {

    private static final Logger log = LoggerFactory.getLogger(SshTunnelManager.class);
    private static final int PORT_RANGE_START = 20000;
    private static final int PORT_RANGE_END = 30000;

    private final NodeProperties properties;
    private final Map<String, Integer> nodePortMap = new ConcurrentHashMap<>();
    private final Map<String, Session> activeSessions = new ConcurrentHashMap<>();
    private final Map<String, Session> proxySessions = new ConcurrentHashMap<>();
    private final AtomicInteger portCounter = new AtomicInteger(PORT_RANGE_START);

    public SshTunnelManager(NodeProperties properties) {
        this.properties = properties;
    }

    public int allocateLocalPort(String nodeId) {
        return nodePortMap.computeIfAbsent(nodeId, id -> {
            int port = portCounter.getAndIncrement();
            if (port > PORT_RANGE_END) {
                throw new IllegalStateException("No more local ports available in range " + PORT_RANGE_START + "-" + PORT_RANGE_END);
            }
            return port;
        });
    }

    public boolean isActive(String nodeId) {
        Session session = activeSessions.get(nodeId);
        return session != null && session.isConnected();
    }

    /**
     * 직접 SSH 터널 (기존 방식)
     * Kite -> 대상 서버 Docker API
     */
    public void openTunnel(Node node) throws JSchException {
        if (!node.isSsh()) {
            throw new IllegalArgumentException("TCP node does not require SSH tunnel: " + node.getId());
        }

        int localPort = allocateLocalPort(node.getId());

        JSch jsch = new JSch();
        jsch.addIdentity(node.getSshKeyPath());

        Session session = jsch.getSession(node.getSshUser(), node.getHost(), node.getSshPort());
        session.setConfig("StrictHostKeyChecking", "no");
        session.connect(10_000);

        session.setPortForwardingL(localPort, "localhost", node.getPort());

        activeSessions.put(node.getId(), session);
        log.info("SSH tunnel opened for node {} on local port {}", node.getName(), localPort);
    }

    /**
     * CP 경유 SSH 프록시 터널
     * Kite -> CP(점프 호스트) -> 대상 서버 Docker API
     *
     * JSch 체이닝: CP 세션에서 대상 호스트로 포트포워딩 후,
     * 로컬에서 CP의 포워딩 포트로 다시 매핑
     */
    public void openProxyTunnel(Node node) throws JSchException {
        if (!node.isSshProxy()) {
            throw new IllegalArgumentException("Node is not SSH_PROXY type: " + node.getId());
        }

        NodeProperties.ProxyConfig proxy = properties.getProxy();
        if (proxy == null) {
            throw new IllegalStateException("Proxy config is required for SSH_PROXY nodes. Set docker.monitor.nodes.proxy in application.yml");
        }

        int localPort = allocateLocalPort(node.getId());

        // Step 1: CP(점프 호스트)에 SSH 연결
        JSch proxyJsch = new JSch();
        proxyJsch.addIdentity(proxy.getKeyPath());

        Session proxySession = proxyJsch.getSession(proxy.getUser(), proxy.getHost(), proxy.getPort());
        proxySession.setConfig("StrictHostKeyChecking", "no");
        proxySession.connect(10_000);
        proxySessions.put(node.getId(), proxySession);

        log.info("Proxy SSH session established to CP {}@{}:{}",
                proxy.getUser(), proxy.getHost(), proxy.getPort());

        // Step 2: CP 세션을 통해 대상 호스트의 Docker API 포트로 포워딩
        // localPort(Kite) -> CP -> targetHost:targetPort
        proxySession.setPortForwardingL(localPort, node.getHost(), node.getPort());

        activeSessions.put(node.getId(), proxySession);
        log.info("SSH proxy tunnel opened for node {} via CP: localhost:{} -> {}:{}",
                node.getName(), localPort, node.getHost(), node.getPort());
    }

    public void closeTunnel(String nodeId) {
        Session session = activeSessions.remove(nodeId);
        if (session != null && session.isConnected()) {
            session.disconnect();
        }
        Session proxySession = proxySessions.remove(nodeId);
        if (proxySession != null && proxySession.isConnected()) {
            proxySession.disconnect();
        }
        nodePortMap.remove(nodeId);
        log.info("Tunnel closed for node {}", nodeId);
    }

    public void closeAll() {
        activeSessions.values().forEach(session -> {
            if (session.isConnected()) {
                session.disconnect();
            }
        });
        activeSessions.clear();
        proxySessions.values().forEach(session -> {
            if (session.isConnected()) {
                session.disconnect();
            }
        });
        proxySessions.clear();
    }
}
