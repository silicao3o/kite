package com.lite_k8s.compose;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@Builder
public class ParsedService {
    private String serviceName;
    private String image;
    private String containerName;
    private List<String> ports;       // "8080:8080" 형태
    private List<String> volumes;     // "/host:/container" 형태
    private Map<String, String> environment;
    private List<String> networks;
    private String restartPolicy;     // "always", "unless-stopped", "no", "on-failure"
    private Map<String, String> labels;
    private List<String> extraHosts;  // "host.docker.internal:host-gateway" 형태
    private List<String> dependsOn;
    private String memoryLimit;       // "2G", "512M"
    private String cpuLimit;          // "1", "0.5"
}
