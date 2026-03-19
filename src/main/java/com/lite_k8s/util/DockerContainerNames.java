package com.lite_k8s.util;

import com.github.dockerjava.api.model.Container;

public final class DockerContainerNames {

    private DockerContainerNames() {}

    public static String extractName(Container container) {
        return extractName(container, "");
    }

    public static String extractName(Container container, String fallback) {
        if (container.getNames() == null || container.getNames().length == 0) return fallback;
        String name = container.getNames()[0];
        return name.startsWith("/") ? name.substring(1) : name;
    }

    public static String stripLeadingSlash(String name) {
        return (name != null && name.startsWith("/")) ? name.substring(1) : name;
    }
}
