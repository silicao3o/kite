package com.lite_k8s.deploy;

public enum DeploymentType {
    ROLLING_UPDATE,
    RECREATE,
    BLUE_GREEN,
    CANARY
}
