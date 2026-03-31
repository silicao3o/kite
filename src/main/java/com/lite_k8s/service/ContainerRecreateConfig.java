package com.lite_k8s.service;

import com.github.dockerjava.api.model.HostConfig;

import java.util.Map;

public record ContainerRecreateConfig(
        String imageName,
        String containerName,
        String[] env,
        HostConfig hostConfig,
        Map<String, String> labels
) {}
