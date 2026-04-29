package com.lite_k8s.util;

/**
 * Docker 이미지 ref 파싱 유틸.
 *
 * 이름의 마지막 path segment("short name") 만 비교하면, 레지스트리 host 나
 * org 가 달라도(예: org 이전) 같은 이미지를 인식하면서 동시에 다른 이미지
 * (예: nginx:alpine vs ghcr.io/.../chat-quvi) 는 분리할 수 있다.
 */
public final class ImageReferences {

    private ImageReferences() {}

    /**
     * 이미지 ref 의 마지막 path segment 만 반환.
     *
     * 예시:
     *  - "nginx:alpine"                              → "nginx"
     *  - "ghcr.io/daquv-core/chat-quvi:v3.0"         → "chat-quvi"
     *  - "ghcr.io/old-org/chat-quvi"                 → "chat-quvi"
     *  - "ghcr.io/x/chat-quvi@sha256:abc"            → "chat-quvi"
     *  - "localhost:5000/myapp:tag"                  → "myapp"
     *  - null / ""                                   → ""
     */
    public static String shortName(String imageRef) {
        if (imageRef == null || imageRef.isBlank()) return "";

        String s = imageRef;

        // 1) digest 제거
        int at = s.indexOf('@');
        if (at >= 0) s = s.substring(0, at);

        // 2) 태그 제거 — 마지막 ':' 가 마지막 '/' 이후일 때만 (host:port 보호)
        int lastSlash = s.lastIndexOf('/');
        int lastColon = s.lastIndexOf(':');
        if (lastColon > lastSlash) {
            s = s.substring(0, lastColon);
        }

        // 3) 마지막 path segment
        int sep = s.lastIndexOf('/');
        return sep >= 0 ? s.substring(sep + 1) : s;
    }

    /** 두 이미지 ref 의 short name 이 같은지 비교. 한쪽이 null/blank 면 false. */
    public static boolean sameShortName(String a, String b) {
        String sa = shortName(a);
        String sb = shortName(b);
        return !sa.isEmpty() && sa.equals(sb);
    }
}
