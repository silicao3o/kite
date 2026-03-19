package com.lite_k8s.node;

public enum NodeConnectionType {
    TCP,  // Docker TCP 직접 연결 (GCP 내부망 등)
    SSH   // SSH 터널 경유 (온프레미스 등)
}
