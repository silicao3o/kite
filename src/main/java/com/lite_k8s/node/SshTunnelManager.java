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
    private static final String DOCKER_SOCKET_PATH = "/var/run/docker.sock";

    private final NodeProperties properties;
    private final JSchSessionFactory sessionFactory;

    private final Map<String, Integer> nodePortMap = new ConcurrentHashMap<>();
    private final Map<String, Session> activeSessions = new ConcurrentHashMap<>();

    // SSH_PROXY 2홉: CP→target SSH 터널용 포트 + target 세션
    private final Map<String, Integer> sshTunnelPorts = new ConcurrentHashMap<>();
    private final Map<String, Session> targetSessions = new ConcurrentHashMap<>();

    // 공유 CP 세션 — SSH_PROXY 노드 전체가 하나를 재사용
    private volatile Session sharedCpSession;
    private final Object cpSessionLock = new Object();
    private final Set<String> proxyNodeIds = ConcurrentHashMap.newKeySet();

    private final AtomicInteger portCounter = new AtomicInteger(PORT_RANGE_START);

    @org.springframework.beans.factory.annotation.Autowired
    public SshTunnelManager(NodeProperties properties) {
        this(properties, (user, host, port, keyPath, passphrase) -> {
            JSch jsch = new JSch();
            if (passphrase != null && !passphrase.isBlank()) {
                jsch.addIdentity(keyPath, passphrase);
            } else {
                jsch.addIdentity(keyPath);
            }
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
        if (session == null || !session.isConnected()) return false;

        Session target = targetSessions.get(nodeId);
        if (target != null) {
            return target.isConnected();
        }
        return true;
    }

    /**
     * 직접 SSH 터널 — Unix 소켓 포워딩
     * docker-monitor → target SSH → /var/run/docker.sock
     */
    public void openTunnel(Node node) throws JSchException {
        if (!node.isSsh()) {
            throw new IllegalArgumentException("TCP node does not require SSH tunnel: " + node.getId());
        }

        int localPort = allocateLocalPort(node.getId());

        Session session = sessionFactory.create(node.getSshUser(), node.getHost(), node.getSshPort(), node.getSshKeyPath(), node.getSshPassphrase());
        session.connect(10_000);
        session.setSocketForwardingL(null, localPort, DOCKER_SOCKET_PATH, null, 10_000);

        activeSessions.put(node.getId(), session);
        log.info("SSH tunnel opened for node {} on local port {} → docker.sock", node.getName(), localPort);
    }

    /**
     * SSH_PROXY 노드에 sshUser가 설정되어 있으면 2홉 + Unix 소켓 포워딩,
     * 없으면 기존 TCP 포트 포워딩 (하위 호환)
     */
    private boolean useSocketForwarding(Node node) {
        return node.getSshUser() != null && !node.getSshUser().isBlank();
    }

    /**
     * CP 경유 SSH 프록시 터널
     *
     * [소켓 모드] sshUser 설정됨 → 2홉 + Unix 소켓 포워딩
     *   docker-monitor → CP → target SSH → /var/run/docker.sock
     *   대상 서버에 Docker TCP 2375 불필요
     *
     * [TCP 모드] sshUser 미설정 → 기존 TCP 포트 포워딩 (하위 호환)
     *   docker-monitor → CP → target:2375
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

        if (useSocketForwarding(node)) {
            openProxySocketTunnel(node, cpSession, localPort, proxy);
        } else {
            openProxyTcpTunnel(node, cpSession, localPort);
        }

        activeSessions.put(node.getId(), cpSession);
        proxyNodeIds.add(node.getId());
    }

    /**
     * 2홉 + Unix 소켓 포워딩 (신규)
     */
    private void openProxySocketTunnel(Node node, Session cpSession, int localPort,
                                       NodeProperties.ProxyConfig proxy) throws JSchException {
        // 1) CP를 통해 target의 SSH 포트로 터널
        int sshTunnelPort = portCounter.getAndIncrement();
        int targetSshPort = node.getSshPort() > 0 ? node.getSshPort() : 22;
        cpSession.setPortForwardingL(sshTunnelPort, node.getHost(), targetSshPort);
        sshTunnelPorts.put(node.getId(), sshTunnelPort);

        // 2) 터널을 통해 target에 SSH 접속
        String targetUser = node.getSshUser();
        String targetKeyPath = node.getSshKeyPath() != null && !node.getSshKeyPath().isBlank() ? node.getSshKeyPath() : proxy.getKeyPath();

        Session targetSession = sessionFactory.create(targetUser, "localhost", sshTunnelPort, targetKeyPath, node.getSshPassphrase());
        targetSession.connect(10_000);
        targetSessions.put(node.getId(), targetSession);

        // 3) target의 docker.sock으로 Unix 소켓 포워딩
        targetSession.setSocketForwardingL(null, localPort, DOCKER_SOCKET_PATH, null, 10_000);

        log.info("SSH proxy tunnel (socket) opened for node {} via CP: localhost:{} → {}:docker.sock",
                node.getName(), localPort, node.getHost());
    }

    /**
     * 기존 TCP 포트 포워딩 (하위 호환)
     */
    private void openProxyTcpTunnel(Node node, Session cpSession, int localPort) throws JSchException {
        cpSession.setPortForwardingL(localPort, node.getHost(), node.getPort());

        log.info("SSH proxy tunnel (tcp) opened for node {} via CP: localhost:{} → {}:{}",
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
            Session session = sessionFactory.create(proxy.getUser(), proxy.getHost(), proxy.getPort(), proxy.getKeyPath(), proxy.getPassphrase());
            session.connect(10_000);
            sharedCpSession = session;
            return sharedCpSession;
        }
    }

    public void closeTunnel(String nodeId) {
        // 1. target 세션 종료 (2홉 소켓 모드)
        Session targetSession = targetSessions.remove(nodeId);
        if (targetSession != null && targetSession.isConnected()) {
            targetSession.disconnect();
        }

        // 2. CP의 포트포워딩 제거
        Integer sshTunnelPort = sshTunnelPorts.remove(nodeId);

        if (proxyNodeIds.remove(nodeId)) {
            Integer localPort = nodePortMap.get(nodeId);
            if (sharedCpSession != null) {
                // 소켓 모드: SSH 터널 포트 제거
                if (sshTunnelPort != null) {
                    try {
                        sharedCpSession.delPortForwardingL(sshTunnelPort);
                    } catch (JSchException e) {
                        log.warn("SSH 터널 포트포워딩 제거 실패 (node={}): {}", nodeId, e.getMessage());
                    }
                }
                // TCP 모드: Docker 포트 포워딩 제거
                if (sshTunnelPort == null && localPort != null) {
                    try {
                        sharedCpSession.delPortForwardingL(localPort);
                    } catch (JSchException e) {
                        log.warn("포트포워딩 룰 제거 실패 (node={}): {}", nodeId, e.getMessage());
                    }
                }
            }
            activeSessions.remove(nodeId);

            if (proxyNodeIds.isEmpty()) {
                disconnectCpSession();
            }
        } else {
            Session session = activeSessions.remove(nodeId);
            if (session != null && session.isConnected()) {
                session.disconnect();
            }
        }

        nodePortMap.remove(nodeId);
        log.info("Tunnel closed for node {}", nodeId);
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
        // target 세션 종료
        targetSessions.forEach((id, session) -> {
            if (session.isConnected()) session.disconnect();
        });
        targetSessions.clear();
        sshTunnelPorts.clear();

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
