package com.lite_k8s.node;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SshTunnelManagerTest {

    private SshTunnelManager tunnelManager;

    @BeforeEach
    void setUp() {
        tunnelManager = new SshTunnelManager();
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
}
