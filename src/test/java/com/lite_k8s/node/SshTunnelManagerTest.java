package com.lite_k8s.node;

import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class SshTunnelManagerTest {

    private SshTunnelManager tunnelManager;

    @BeforeEach
    void setUp() {
        tunnelManager = new SshTunnelManager(new NodeProperties());
    }

    @AfterEach
    void tearDown() {
        tunnelManager.closeAll();
    }

    @Test
    @DisplayName("SSH 터널 포트는 노드마다 유니크하게 할당된다")
    void allocateLocalPort_ShouldReturnUniquePortPerNode() {
        int port1 = tunnelManager.allocateLocalPort("node-dev");
        int port2 = tunnelManager.allocateLocalPort("node-res");

        assertThat(port1).isNotEqualTo(port2);
        assertThat(port1).isBetween(20000, 30000);
        assertThat(port2).isBetween(20000, 30000);
    }

    @Test
    @DisplayName("같은 노드 ID로 재요청하면 동일 포트 반환")
    void allocateLocalPort_SameNodeId_ReturnsSamePort() {
        int port1 = tunnelManager.allocateLocalPort("node-dev");
        int port2 = tunnelManager.allocateLocalPort("node-dev");

        assertThat(port1).isEqualTo(port2);
    }

    @Test
    @DisplayName("터널이 없는 노드에 isActive() 하면 false")
    void isActive_WhenNoTunnel_ReturnsFalse() {
        assertThat(tunnelManager.isActive("node-unknown")).isFalse();
    }

    @Test
    @DisplayName("TCP 노드는 SSH 터널 불필요 — openTunnel 호출 시 예외")
    void openTunnel_WhenTcpNode_ShouldThrowException() {
        Node tcpNode = Node.builder()
                .id("node-gcp")
                .name("chat-quvi")
                .host("10.178.0.12")
                .port(2375)
                .connectionType(NodeConnectionType.TCP)
                .build();

        assertThatThrownBy(() -> tunnelManager.openTunnel(tcpNode))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("TCP");
    }

    @Test
    @DisplayName("SSH_PROXY가 아닌 노드에 openProxyTunnel 호출 시 예외")
    void openProxyTunnel_WhenNotSshProxyNode_ShouldThrowException() {
        Node sshNode = Node.builder()
                .id("node-dev")
                .name("dev")
                .host("192.168.1.10")
                .port(2375)
                .connectionType(NodeConnectionType.SSH)
                .build();

        assertThatThrownBy(() -> tunnelManager.openProxyTunnel(sshNode))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SSH_PROXY");
    }

    @Test
    @DisplayName("proxy 설정 없이 SSH_PROXY 노드에 openProxyTunnel 호출 시 예외")
    void openProxyTunnel_WhenNoProxyConfig_ShouldThrowException() {
        NodeProperties properties = new NodeProperties();
        SshTunnelManager manager = new SshTunnelManager(properties);

        Node proxyNode = Node.builder()
                .id("node-proxy")
                .name("on-prem")
                .host("192.168.1.10")
                .port(2375)
                .connectionType(NodeConnectionType.SSH_PROXY)
                .build();

        assertThatThrownBy(() -> manager.openProxyTunnel(proxyNode))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("proxy");
    }

    @Test
    @DisplayName("SSH_PROXY 노드 2개를 열면 CP 세션은 1번만 생성된다")
    void openProxyTunnel_TwoNodes_SharesSingleCpSession() throws JSchException {
        Session mockCpSession = mock(Session.class);
        when(mockCpSession.isConnected()).thenReturn(true);

        JSchSessionFactory mockFactory = mock(JSchSessionFactory.class);
        when(mockFactory.create(anyString(), anyString(), anyInt(), anyString()))
                .thenReturn(mockCpSession);

        SshTunnelManager manager = new SshTunnelManager(propertiesWithProxy(), mockFactory);

        manager.openProxyTunnel(proxyNode("node-vm1", "192.168.1.10", 2375));
        manager.openProxyTunnel(proxyNode("node-vm2", "192.168.1.11", 2375));

        verify(mockFactory, times(1)).create(anyString(), anyString(), anyInt(), anyString());
        verify(mockCpSession, times(2)).setPortForwardingL(anyInt(), anyString(), anyInt());
    }

    @Test
    @DisplayName("한 노드를 닫아도 다른 노드가 있으면 CP 세션은 유지된다")
    void closeTunnel_WhenOtherNodeRemains_CpSessionStaysConnected() throws JSchException {
        Session mockCpSession = mock(Session.class);
        when(mockCpSession.isConnected()).thenReturn(true);

        JSchSessionFactory mockFactory = mock(JSchSessionFactory.class);
        when(mockFactory.create(anyString(), anyString(), anyInt(), anyString()))
                .thenReturn(mockCpSession);

        SshTunnelManager manager = new SshTunnelManager(propertiesWithProxy(), mockFactory);

        manager.openProxyTunnel(proxyNode("node-vm1", "192.168.1.10", 2375));
        manager.openProxyTunnel(proxyNode("node-vm2", "192.168.1.11", 2375));

        manager.closeTunnel("node-vm1");

        verify(mockCpSession, never()).disconnect();
        verify(mockCpSession).delPortForwardingL(anyInt());
    }

    @Test
    @DisplayName("마지막 SSH_PROXY 노드를 닫으면 CP 세션도 disconnect된다")
    void closeTunnel_WhenLastProxyNode_CpSessionDisconnects() throws JSchException {
        Session mockCpSession = mock(Session.class);
        when(mockCpSession.isConnected()).thenReturn(true);

        JSchSessionFactory mockFactory = mock(JSchSessionFactory.class);
        when(mockFactory.create(anyString(), anyString(), anyInt(), anyString()))
                .thenReturn(mockCpSession);

        SshTunnelManager manager = new SshTunnelManager(propertiesWithProxy(), mockFactory);

        manager.openProxyTunnel(proxyNode("node-vm1", "192.168.1.10", 2375));
        manager.closeTunnel("node-vm1");

        verify(mockCpSession).disconnect();
    }

    @Test
    @DisplayName("CP 세션이 끊어진 상태에서 openProxyTunnel 호출 시 재연결한다")
    void openProxyTunnel_WhenCpSessionDropped_Reconnects() throws JSchException {
        Session droppedSession = mock(Session.class);
        when(droppedSession.isConnected()).thenReturn(false);

        Session newSession = mock(Session.class);
        when(newSession.isConnected()).thenReturn(true);

        JSchSessionFactory mockFactory = mock(JSchSessionFactory.class);
        when(mockFactory.create(anyString(), anyString(), anyInt(), anyString()))
                .thenReturn(droppedSession)
                .thenReturn(newSession);

        SshTunnelManager manager = new SshTunnelManager(propertiesWithProxy(), mockFactory);

        manager.openProxyTunnel(proxyNode("node-vm1", "192.168.1.10", 2375));
        // 세션 끊김 상태에서 새 노드 추가
        manager.openProxyTunnel(proxyNode("node-vm2", "192.168.1.11", 2375));

        verify(mockFactory, times(2)).create(anyString(), anyString(), anyInt(), anyString());
        verify(newSession).setPortForwardingL(anyInt(), eq("192.168.1.11"), eq(2375));
    }

    // --- helpers ---

    private NodeProperties propertiesWithProxy() {
        NodeProperties props = new NodeProperties();
        NodeProperties.ProxyConfig proxy = new NodeProperties.ProxyConfig();
        proxy.setHost("cp.internal");
        proxy.setPort(22);
        proxy.setUser("ubuntu");
        proxy.setKeyPath("/root/.ssh/id_rsa");
        props.setProxy(proxy);
        return props;
    }

    private Node proxyNode(String id, String host, int port) {
        return Node.builder()
                .id(id)
                .name(id)
                .host(host)
                .port(port)
                .connectionType(NodeConnectionType.SSH_PROXY)
                .build();
    }
}
