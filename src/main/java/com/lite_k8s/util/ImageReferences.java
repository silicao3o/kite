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

    /**
     * 입력이 image reference (예: nginx:alpine, ghcr.io/.../app@sha256:...) 인지,
     * 아니면 image ID (sha256:abcd... 또는 순수 hex 12자 이상) 인지 판별.
     *
     * digest 로 pin 해 만든 컨테이너는 docker 의 latest 태그가 다른 이미지로 이동하면
     * 더 이상 repo:tag 형태로 표현되지 않고 image ID 만 노출되는데, 이 경우
     * sameShortName 비교가 항상 false 가 돼 와치 대상이 false-negative 로 빠지는
     * 문제가 있다. 호출부에서 isImageReference 가 false 면 short name 가드를
     * 우회하고 컨테이너 이름 패턴 매칭만 신뢰하면 된다.
     */
    public static boolean isImageReference(String s) {
        if (s == null || s.isBlank()) return false;
        if (s.startsWith("sha256:")) return false;
        // 순수 hex 12자 이상 = image ID
        if (s.length() >= 12 && s.matches("[0-9a-f]+")) return false;
        return true;
    }
}
