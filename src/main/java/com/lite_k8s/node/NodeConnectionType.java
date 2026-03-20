package com.lite_k8s.node;

public enum NodeConnectionType {
    TCP,        // Docker TCP 직접 연결 (GCP 내부망 등)
    SSH,        // SSH 터널 경유 (온프레미스 등)
    SSH_PROXY   // SSH 점프 호스트(CP) 경유 연결
}
