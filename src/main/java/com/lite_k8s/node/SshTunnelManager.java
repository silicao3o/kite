package com.lite_k8s.node;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class SshTunnelManager {

    private static final Logger log = LoggerFactory.getLogger(SshTunnelManager.class);
    private static final int PORT_RANGE_START = 20000;
    private static final int PORT_RANGE_END = 30000;

    private final NodeProperties properties;
    private final JSchSessionFactory sessionFactory;

    private final Map<String, Integer> nodePortMap = new ConcurrentHashMap<>();
    private final Map<String, Session> activeSessions = new ConcurrentHashMap<>();

    // 공유 CP 세션 — SSH_PROXY 노드 전체가 하나를 재사용
    private volatile Session sharedCpSession;
    private final Object cpSessionLock = new Object();
    private final Set<String> proxyNodeIds = ConcurrentHashMap.newKeySet();

    private final AtomicInteger portCounter = new AtomicInteger(PORT_RANGE_START);

    @org.springframework.beans.factory.annotation.Autowired
    public SshTunnelManager(NodeProperties properties) {
        this(properties, (user, host, port, keyPath) -> {
            JSch jsch = new JSch();
            jsch.addIdentity(keyPath);
            Session session = jsch.getSession(user, host, port);
            session.setConfig("StrictHostKeyChecking", "no");
            return session;
        });
    }

    public SshTunnelManager(NodeProperties properties, JSchSessionFactory sessionFactory) {
        this.properties = properties;
        this.sessionFactory = sessionFactory;
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
     * docker-monitor → 대상 서버 Docker API
     */
    public void openTunnel(Node node) throws JSchException {
        if (!node.isSsh()) {
            throw new IllegalArgumentException("TCP node does not require SSH tunnel: " + node.getId());
        }

        int localPort = allocateLocalPort(node.getId());

        Session session = sessionFactory.create(node.getSshUser(), node.getHost(), node.getSshPort(), node.getSshKeyPath());
        session.connect(10_000);
        session.setPortForwardingL(localPort, "localhost", node.getPort());

        activeSessions.put(node.getId(), session);
        log.info("SSH tunnel opened for node {} on local port {}", node.getName(), localPort);
    }

    /**
     * CP 경유 SSH 프록시 터널 — CP 세션 1개 공유
     * docker-monitor → CP(점프 호스트) → 대상 서버 Docker API
     *
     * CP 세션은 처음 1번만 생성하고 이후 재사용.
     * 각 VM 노드는 CP 세션에 포트포워딩 룰만 추가.
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
        Session cpSession = getOrCreateCpSession(proxy);

        cpSession.setPortForwardingL(localPort, node.getHost(), node.getPort());

        activeSessions.put(node.getId(), cpSession);
        proxyNodeIds.add(node.getId());

        log.info("SSH proxy tunnel opened for node {} via CP: localhost:{} → {}:{}",
                node.getName(), localPort, node.getHost(), node.getPort());
    }

    private Session getOrCreateCpSession(NodeProperties.ProxyConfig proxy) throws JSchException {
        if (sharedCpSession != null && sharedCpSession.isConnected()) {
            return sharedCpSession;
        }
        synchronized (cpSessionLock) {
            if (sharedCpSession != null && sharedCpSession.isConnected()) {
                return sharedCpSession;
            }
            log.info("CP SSH 세션 생성: {}@{}:{}", proxy.getUser(), proxy.getHost(), proxy.getPort());
            Session session = sessionFactory.create(proxy.getUser(), proxy.getHost(), proxy.getPort(), proxy.getKeyPath());
            session.connect(10_000);
            sharedCpSession = session;
            return sharedCpSession;
        }
    }

    public void closeTunnel(String nodeId) {
        if (proxyNodeIds.remove(nodeId)) {
            closeProxyNodeTunnel(nodeId);
        } else {
            Session session = activeSessions.remove(nodeId);
            if (session != null && session.isConnected()) {
                session.disconnect();
            }
        }
        nodePortMap.remove(nodeId);
        log.info("Tunnel closed for node {}", nodeId);
    }

    private void closeProxyNodeTunnel(String nodeId) {
        Integer localPort = nodePortMap.get(nodeId);
        activeSessions.remove(nodeId);

        if (localPort != null && sharedCpSession != null) {
            try {
                sharedCpSession.delPortForwardingL(localPort);
            } catch (JSchException e) {
                log.warn("포트포워딩 룰 제거 실패 (node={}): {}", nodeId, e.getMessage());
            }
        }

        if (proxyNodeIds.isEmpty()) {
            disconnectCpSession();
        }
    }

    private void disconnectCpSession() {
        synchronized (cpSessionLock) {
            if (sharedCpSession != null) {
                sharedCpSession.disconnect();
                sharedCpSession = null;
                log.info("CP SSH 세션 종료 (프록시 노드 없음)");
            }
        }
    }

    public void closeAll() {
        // 직접 SSH 노드 종료
        activeSessions.forEach((nodeId, session) -> {
            if (!proxyNodeIds.contains(nodeId) && session.isConnected()) {
                session.disconnect();
            }
        });
        activeSessions.clear();

        // 공유 CP 세션 종료
        proxyNodeIds.clear();
        disconnectCpSession();

        nodePortMap.clear();
    }
}
