package com.lite_k8s.node;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/nodes")
@RequiredArgsConstructor
public class NodeManagementController {

    private final NodeRegistry nodeRegistry;

    @PostMapping
    public NodeResponse addNode(@RequestBody AddNodeRequest request) {
        NodeConnectionType connectionType = "SSH_PROXY".equalsIgnoreCase(request.getConnectionType())
                ? NodeConnectionType.SSH_PROXY : "SSH".equalsIgnoreCase(request.getConnectionType())
                ? NodeConnectionType.SSH : NodeConnectionType.TCP;

        Node node = Node.builder()
                .id(UUID.randomUUID().toString())
                .name(request.getName())
                .host(request.getHost())
                .port(request.getPort())
                .connectionType(connectionType)
                .sshPort(request.getSshPort())
                .sshUser(request.getSshUser())
                .sshKeyPath(request.getSshKeyPath())
                .status(NodeStatus.UNKNOWN)
                .build();
        nodeRegistry.register(node);
        return NodeResponse.from(node);
    }

    @DeleteMapping("/{id}")
    public void removeNode(@PathVariable String id) {
        nodeRegistry.unregister(id);
    }

    @GetMapping
    public List<NodeResponse> listNodes() {
        return nodeRegistry.findAll().stream()
                .map(NodeResponse::from)
                .collect(Collectors.toList());
    }

    @Getter
    @Setter
    public static class AddNodeRequest {
        private String name;
        private String host;
        private int port = 2375;
        private String connectionType = "TCP";
        private int sshPort = 22;
        private String sshUser;
        private String sshKeyPath;
    }

    @Getter
    public static class NodeResponse {
        private final String id;
        private final String name;
        private final String host;
        private final int port;
        private final String connectionType;
        private final String status;

        private NodeResponse(String id, String name, String host, int port, String connectionType, String status) {
            this.id = id;
            this.name = name;
            this.host = host;
            this.port = port;
            this.connectionType = connectionType;
            this.status = status;
        }

        static NodeResponse from(Node node) {
            return new NodeResponse(
                    node.getId(),
                    node.getName(),
                    node.getHost(),
                    node.getPort(),
                    node.getConnectionType().name(),
                    node.getStatus().name()
            );
        }
    }
}
