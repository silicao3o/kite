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

    // === SSH 직접 터널: Unix 소켓 포워딩 ===

    @Test
    @DisplayName("SSH 노드는 setSocketForwardingL로 docker.sock에 포워딩한다")
    void openTunnel_SshNode_UsesSocketForwarding() throws JSchException {
        Session mockSession = mock(Session.class);

        JSchSessionFactory mockFactory = mock(JSchSessionFactory.class);
        when(mockFactory.create(anyString(), anyString(), anyInt(), anyString()))
                .thenReturn(mockSession);

        SshTunnelManager manager = new SshTunnelManager(new NodeProperties(), mockFactory);

        Node sshNode = Node.builder()
                .id("node-dev")
                .name("dev")
                .host("")
                .port(2375)
                .connectionType(NodeConnectionType.SSH)
                .sshPort(22)
                .sshUser("daquv")
                .sshKeyPath("/root/.ssh/id_rsa")
                .build();

        manager.openTunnel(sshNode);

        verify(mockSession).connect(10_000);
        verify(mockSession).setSocketForwardingL(isNull(), anyInt(),
                eq("/var/run/docker.sock"), isNull(), eq(10_000));
        verify(mockSession, never()).setPortForwardingL(anyInt(), anyString(), anyInt());
    }

    // === SSH_PROXY TCP 모드 (하위 호환): sshUser 미설정 ===

    @Test
    @DisplayName("SSH_PROXY sshUser 미설정 → 기존 TCP 포트 포워딩 사용")
    void openProxyTunnel_NoSshUser_UsesTcpForwarding() throws JSchException {
        Session mockCpSession = mock(Session.class);
        when(mockCpSession.isConnected()).thenReturn(true);

        JSchSessionFactory mockFactory = mock(JSchSessionFactory.class);
        when(mockFactory.create(anyString(), anyString(), anyInt(), anyString()))
                .thenReturn(mockCpSession);

        SshTunnelManager manager = new SshTunnelManager(propertiesWithProxy(), mockFactory);

        // sshUser 미설정 → TCP 모드
        Node proxyNode = Node.builder()
                .id("node-gcp")
                .name("gcp-operia")
                .host("10.178.0.15")
                .port(2375)
                .connectionType(NodeConnectionType.SSH_PROXY)
                .build();

        manager.openProxyTunnel(proxyNode);

        // CP 세션에 TCP 포트 포워딩 (기존 방식)
        verify(mockCpSession).setPortForwardingL(anyInt(), eq("10.178.0.15"), eq(2375));
        // setSocketForwardingL은 호출되지 않아야 함
        verify(mockCpSession, never()).setSocketForwardingL(any(), anyInt(), anyString(), any(), anyInt());
        // target 세션은 생성되지 않아야 함 (factory는 CP 세션 1번만 호출)
        verify(mockFactory, times(1)).create(anyString(), anyString(), anyInt(), anyString());
    }

    @Test
    @DisplayName("SSH_PROXY TCP 모드 2개 노드 → CP 세션 공유, 각각 TCP 포워딩")
    void openProxyTunnel_TwoTcpNodes_SharesCpSession() throws JSchException {
        Session mockCpSession = mock(Session.class);
        when(mockCpSession.isConnected()).thenReturn(true);

        JSchSessionFactory mockFactory = mock(JSchSessionFactory.class);
        when(mockFactory.create(anyString(), anyString(), anyInt(), anyString()))
                .thenReturn(mockCpSession);

        SshTunnelManager manager = new SshTunnelManager(propertiesWithProxy(), mockFactory);

        manager.openProxyTunnel(tcpProxyNode("node-vm1", "10.178.0.15", 2375));
        manager.openProxyTunnel(tcpProxyNode("node-vm2", "10.178.0.14", 2375));

        verify(mockFactory, times(1)).create(anyString(), anyString(), anyInt(), anyString());
        verify(mockCpSession, times(2)).setPortForwardingL(anyInt(), anyString(), anyInt());
    }

    // === SSH_PROXY 소켓 모드 (신규): sshUser 설정됨 ===

    @Test
    @DisplayName("SSH_PROXY sshUser 설정됨 → 2홉 SSH + docker.sock 소켓 포워딩")
    void openProxyTunnel_WithSshUser_UsesTwoHopSocketForwarding() throws JSchException {
        Session mockCpSession = mock(Session.class);
        when(mockCpSession.isConnected()).thenReturn(true);
        Session mockTargetSession = mock(Session.class);

        JSchSessionFactory mockFactory = mock(JSchSessionFactory.class);
        when(mockFactory.create(anyString(), anyString(), anyInt(), anyString()))
                .thenReturn(mockCpSession)
                .thenReturn(mockTargetSession);

        SshTunnelManager manager = new SshTunnelManager(propertiesWithProxy(), mockFactory);

        Node proxyNode = socketProxyNode("node-dev", "", 22, "daquv");

        manager.openProxyTunnel(proxyNode);

        // CP: target SSH 포트로 TCP 포워딩
        verify(mockCpSession).setPortForwardingL(anyInt(), eq(""), eq(22));
        // target: docker.sock으로 소켓 포워딩
        verify(mockTargetSession).connect(10_000);
        verify(mockTargetSession).setSocketForwardingL(isNull(), anyInt(),
                eq("/var/run/docker.sock"), isNull(), eq(10_000));
    }

    @Test
    @DisplayName("SSH_PROXY 소켓 모드 2개 노드 → CP 세션 공유, target 세션 각각 생성")
    void openProxyTunnel_TwoSocketNodes_SharesCpButSeparateTargets() throws JSchException {
        Session mockCpSession = mock(Session.class);
        when(mockCpSession.isConnected()).thenReturn(true);
        Session mockTarget1 = mock(Session.class);
        Session mockTarget2 = mock(Session.class);

        JSchSessionFactory mockFactory = mock(JSchSessionFactory.class);
        when(mockFactory.create(anyString(), anyString(), anyInt(), anyString()))
                .thenReturn(mockCpSession)
                .thenReturn(mockTarget1)
                .thenReturn(mockTarget2);

        SshTunnelManager manager = new SshTunnelManager(propertiesWithProxy(), mockFactory);

        manager.openProxyTunnel(socketProxyNode("node-dev", "", 22, "daquv"));
        manager.openProxyTunnel(socketProxyNode("node-res", "183.102.124.146", 2222, "daquv"));

        // CP 1 + target 2 = factory 3번 호출
        verify(mockFactory, times(3)).create(anyString(), anyString(), anyInt(), anyString());
        // CP: 2개 노드의 SSH 포트 포워딩
        verify(mockCpSession, times(2)).setPortForwardingL(anyInt(), anyString(), anyInt());
        // 각 target: docker.sock 포워딩
        verify(mockTarget1).setSocketForwardingL(isNull(), anyInt(),
                eq("/var/run/docker.sock"), isNull(), eq(10_000));
        verify(mockTarget2).setSocketForwardingL(isNull(), anyInt(),
                eq("/var/run/docker.sock"), isNull(), eq(10_000));
    }

    // === TCP/소켓 혼합 ===

    @Test
    @DisplayName("TCP 모드와 소켓 모드 노드를 동시에 연결할 수 있다")
    void openProxyTunnel_MixedModes_WorksTogether() throws JSchException {
        Session mockCpSession = mock(Session.class);
        when(mockCpSession.isConnected()).thenReturn(true);
        Session mockTargetSession = mock(Session.class);

        JSchSessionFactory mockFactory = mock(JSchSessionFactory.class);
        when(mockFactory.create(anyString(), anyString(), anyInt(), anyString()))
                .thenReturn(mockCpSession)
                .thenReturn(mockTargetSession);

        SshTunnelManager manager = new SshTunnelManager(propertiesWithProxy(), mockFactory);

        // GCP 노드: TCP 모드
        manager.openProxyTunnel(tcpProxyNode("node-gcp", "10.178.0.15", 2375));
        // 온프레미스 노드: 소켓 모드
        manager.openProxyTunnel(socketProxyNode("node-dev", "", 22, "daquv"));

        // CP: TCP 포워딩 1개 (GCP) + SSH 터널 1개 (온프레미스)
        verify(mockCpSession).setPortForwardingL(anyInt(), eq("10.178.0.15"), eq(2375));
        verify(mockCpSession).setPortForwardingL(anyInt(), eq(""), eq(22));
        // target 세션: 소켓 포워딩
        verify(mockTargetSession).setSocketForwardingL(isNull(), anyInt(),
                eq("/var/run/docker.sock"), isNull(), eq(10_000));
    }

    // === 종료 ===

    @Test
    @DisplayName("한 노드를 닫아도 다른 노드가 있으면 CP 세션은 유지된다")
    void closeTunnel_WhenOtherNodeRemains_CpSessionStaysConnected() throws JSchException {
        Session mockCpSession = mock(Session.class);
        when(mockCpSession.isConnected()).thenReturn(true);

        JSchSessionFactory mockFactory = mock(JSchSessionFactory.class);
        when(mockFactory.create(anyString(), anyString(), anyInt(), anyString()))
                .thenReturn(mockCpSession);

        SshTunnelManager manager = new SshTunnelManager(propertiesWithProxy(), mockFactory);

        manager.openProxyTunnel(tcpProxyNode("node-vm1", "10.178.0.15", 2375));
        manager.openProxyTunnel(tcpProxyNode("node-vm2", "10.178.0.14", 2375));

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

        manager.openProxyTunnel(tcpProxyNode("node-vm1", "10.178.0.15", 2375));
        manager.closeTunnel("node-vm1");

        verify(mockCpSession).disconnect();
    }

    @Test
    @DisplayName("소켓 모드 노드를 닫으면 target 세션도 disconnect된다")
    void closeTunnel_SocketMode_DisconnectsTargetSession() throws JSchException {
        Session mockCpSession = mock(Session.class);
        when(mockCpSession.isConnected()).thenReturn(true);
        Session mockTargetSession = mock(Session.class);
        when(mockTargetSession.isConnected()).thenReturn(true);

        JSchSessionFactory mockFactory = mock(JSchSessionFactory.class);
        when(mockFactory.create(anyString(), anyString(), anyInt(), anyString()))
                .thenReturn(mockCpSession)
                .thenReturn(mockTargetSession);

        SshTunnelManager manager = new SshTunnelManager(propertiesWithProxy(), mockFactory);

        manager.openProxyTunnel(socketProxyNode("node-dev", "", 22, "daquv"));
        manager.closeTunnel("node-dev");

        verify(mockTargetSession).disconnect();
        verify(mockCpSession).disconnect(); // 마지막 노드이므로 CP도 종료
    }

    @Test
    @DisplayName("CP 세션이 끊어진 상태에서 openProxyTunnel 호출 시 재연결한다")
    void openProxyTunnel_WhenCpSessionDropped_Reconnects() throws JSchException {
        Session droppedSession = mock(Session.class);
        when(droppedSession.isConnected()).thenReturn(false);

        Session newCpSession = mock(Session.class);
        when(newCpSession.isConnected()).thenReturn(true);

        JSchSessionFactory mockFactory = mock(JSchSessionFactory.class);
        when(mockFactory.create(anyString(), anyString(), anyInt(), anyString()))
                .thenReturn(droppedSession)
                .thenReturn(newCpSession);

        SshTunnelManager manager = new SshTunnelManager(propertiesWithProxy(), mockFactory);

        manager.openProxyTunnel(tcpProxyNode("node-vm1", "10.178.0.15", 2375));
        manager.openProxyTunnel(tcpProxyNode("node-vm2", "10.178.0.14", 2375));

        verify(mockFactory, times(2)).create(anyString(), anyString(), anyInt(), anyString());
        verify(newCpSession).setPortForwardingL(anyInt(), eq("10.178.0.14"), eq(2375));
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

    /** TCP 모드 프록시 노드 (sshUser 미설정) */
    private Node tcpProxyNode(String id, String host, int port) {
        return Node.builder()
                .id(id)
                .name(id)
                .host(host)
                .port(port)
                .connectionType(NodeConnectionType.SSH_PROXY)
                .build();
    }

    /** 소켓 모드 프록시 노드 (sshUser 설정됨) */
    private Node socketProxyNode(String id, String host, int sshPort, String sshUser) {
        return Node.builder()
                .id(id)
                .name(id)
                .host(host)
                .port(2375) // 소켓 모드에서는 사용 안됨
                .sshPort(sshPort)
                .sshUser(sshUser)
                .connectionType(NodeConnectionType.SSH_PROXY)
                .build();
    }
}
