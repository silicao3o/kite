package com.lite_k8s.deploy;

import com.github.dockerjava.api.DockerClient;

public interface DeploymentStrategy {
    DeployResult deploy(DeploymentSpec spec, DockerClient dockerClient);
    DeploymentType type();
}
