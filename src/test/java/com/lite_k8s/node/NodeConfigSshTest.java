package com.lite_k8s.node;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NodeConfigSshTest {

    @Test
    @DisplayName("NodeConfig 기본 connectionType은 SSH")
    void nodeConfig_DefaultConnectionType_IsSsh() {
        NodeProperties.NodeConfig config = new NodeProperties.NodeConfig();

        assertThat(config.getConnectionType()).isEqualTo("SSH");
    }

    @Test
    @DisplayName("SSH 타입 NodeConfig는 sshPort, sshUser, sshKeyPath를 가진다")
    void nodeConfig_SshType_HasSshFields() {
        NodeProperties.NodeConfig config = new NodeProperties.NodeConfig();
        config.setConnectionType("SSH");
        config.setSshPort(2222);
        config.setSshUser("ubuntu");
        config.setSshKeyPath("/home/user/.ssh/id_rsa");

        assertThat(config.getConnectionType()).isEqualTo("SSH");
        assertThat(config.getSshPort()).isEqualTo(2222);
        assertThat(config.getSshUser()).isEqualTo("ubuntu");
        assertThat(config.getSshKeyPath()).isEqualTo("/home/user/.ssh/id_rsa");
    }

    @Test
    @DisplayName("SSH 타입 Node는 isSsh()가 true")
    void node_SshType_IsSshReturnsTrue() {
        Node node = Node.builder()
                .id("node-dev")
                .name("dev")
                .host("")
                .port(2375)
                .connectionType(NodeConnectionType.SSH)
                .sshPort(22)
                .sshUser("ubuntu")
                .sshKeyPath("/root/.ssh/id_rsa")
                .build();

        assertThat(node.isSsh()).isTrue();
    }

    @Test
    @DisplayName("SSH_PROXY 타입 Node는 isSshProxy()가 true")
    void node_SshProxyType_IsSshProxyReturnsTrue() {
        Node node = Node.builder()
                .id("node-onprem")
                .name("on-prem")
                .host("192.168.1.10")
                .port(2375)
                .connectionType(NodeConnectionType.SSH_PROXY)
                .build();

        assertThat(node.isSshProxy()).isTrue();
        assertThat(node.isSsh()).isFalse();
    }

    @Test
    @DisplayName("SSH/SSH_PROXY 타입 Node는 requiresTunnel()이 true")
    void node_RequiresTunnel_ForAllSshTypes() {
        Node sshNode = Node.builder().id("1").name("ssh").host("h").port(1)
                .connectionType(NodeConnectionType.SSH).build();
        Node proxyNode = Node.builder().id("2").name("proxy").host("h").port(1)
                .connectionType(NodeConnectionType.SSH_PROXY).build();

        assertThat(sshNode.requiresTunnel()).isTrue();
        assertThat(proxyNode.requiresTunnel()).isTrue();
    }
}
