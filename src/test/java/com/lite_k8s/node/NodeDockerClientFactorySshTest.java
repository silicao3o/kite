package com.lite_k8s.node;

import com.github.dockerjava.api.DockerClient;
import com.jcraft.jsch.JSchException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NodeDockerClientFactorySshTest {

    @Mock
    private SshTunnelManager tunnelManager;

    private NodeDockerClientFactory factory;

    @BeforeEach
    void setUp() {
        factory = new NodeDockerClientFactory(tunnelManager);
    }

    @Test
    @DisplayName("SSH 노드는 터널 포트로 DockerClient를 생성한다")
    void createClient_SshNode_UsesTunnelPort() throws JSchException {
        Node sshNode = Node.builder()
                .id("node-dev")
                .name("dev")
                .host("")
                .port(2375)
                .connectionType(NodeConnectionType.SSH)
                .sshPort(22)
                .sshUser("ubuntu")
                .sshKeyPath("/root/.ssh/id_rsa")
                .build();

        when(tunnelManager.allocateLocalPort("node-dev")).thenReturn(20000);

        DockerClient client = factory.createClient(sshNode);

        assertThat(client).isNotNull();
        verify(tunnelManager).openTunnel(sshNode);
        verify(tunnelManager).allocateLocalPort("node-dev");
    }

    @Test
    @DisplayName("같은 SSH 노드를 두 번 요청하면 터널은 한 번만 연다")
    void createClient_SshNode_OpensTunnelOnlyOnce() throws JSchException {
        Node sshNode = Node.builder()
                .id("node-dev")
                .name("dev")
                .host("")
                .port(2375)
                .connectionType(NodeConnectionType.SSH)
                .sshPort(22)
                .sshUser("ubuntu")
                .sshKeyPath("/root/.ssh/id_rsa")
                .build();

        when(tunnelManager.allocateLocalPort("node-dev")).thenReturn(20000);

        factory.createClient(sshNode);
        factory.createClient(sshNode);

        verify(tunnelManager, times(1)).openTunnel(sshNode);
    }

    @Test
    @DisplayName("SSH_PROXY 노드는 openProxyTunnel을 호출한다")
    void createClient_SshProxyNode_CallsOpenProxyTunnel() throws JSchException {
        Node proxyNode = Node.builder()
                .id("node-onprem")
                .name("on-prem")
                .host("192.168.1.10")
                .port(2375)
                .connectionType(NodeConnectionType.SSH_PROXY)
                .build();

        when(tunnelManager.allocateLocalPort("node-onprem")).thenReturn(21000);

        DockerClient client = factory.createClient(proxyNode);

        assertThat(client).isNotNull();
        verify(tunnelManager).openProxyTunnel(proxyNode);
        verify(tunnelManager, never()).openTunnel(any());
    }

    @Test
    @DisplayName("같은 SSH_PROXY 노드를 두 번 요청하면 proxyTunnel은 한 번만 연다")
    void createClient_SshProxyNode_OpensProxyTunnelOnlyOnce() throws JSchException {
        Node proxyNode = Node.builder()
                .id("node-onprem")
                .name("on-prem")
                .host("192.168.1.10")
                .port(2375)
                .connectionType(NodeConnectionType.SSH_PROXY)
                .build();

        when(tunnelManager.allocateLocalPort("node-onprem")).thenReturn(21000);

        factory.createClient(proxyNode);
        factory.createClient(proxyNode);

        verify(tunnelManager, times(1)).openProxyTunnel(proxyNode);
    }
}
