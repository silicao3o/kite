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

    private final Map<String, Integer> nodePortMap = new ConcurrentHashMap<>();
    private final Map<String, Session> activeSessions = new ConcurrentHashMap<>();
    private final AtomicInteger portCounter = new AtomicInteger(PORT_RANGE_START);

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
        log.info("SSH tunnel opened for node {} on local port {}", node.getId(), localPort);
    }

    public void closeAll() {
        activeSessions.values().forEach(session -> {
            if (session.isConnected()) {
                session.disconnect();
            }
        });
        activeSessions.clear();
    }
}
