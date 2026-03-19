package com.lite_k8s.update;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;

/**
 * GHCR(GitHub Container Registry) v2 API 클라이언트
 * 이미지의 최신 digest를 조회하여 새 버전 감지에 사용
 * GHCR 인증: PAT를 Basic 자격증명으로 토큰 교환 후 Bearer로 사용
 */
@Slf4j
public class GhcrClient {

    private static final String DIGEST_HEADER = "Docker-Content-Digest";
    private static final String MANIFEST_ACCEPT =
            "application/vnd.docker.distribution.manifest.v2+json," +
            "application/vnd.oci.image.manifest.v1+json," +
            "application/vnd.oci.image.index.v1+json";

    private final String pat;
    private final HttpClient httpClient;

    public GhcrClient(String pat) {
        this.pat = pat;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    /**
     * 이미지의 최신 digest 조회 (SHA256 해시)
     *
     * @param imageRef ghcr.io/owner/image 형태의 이미지 참조
     * @param tag      태그 (latest, v1.2.3 등)
     * @return sha256:... 형태의 digest, 실패 시 null
     */
    public String getLatestDigest(String imageRef, String tag) {
        String url = buildUrl(imageRef, tag);
        try {
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Accept", MANIFEST_ACCEPT)
                    .timeout(Duration.ofSeconds(15))
                    .GET();

            if (!isAnonymous()) {
                String bearerToken = exchangeToken(imageRef);
                if (bearerToken != null) {
                    requestBuilder.header("Authorization", "Bearer " + bearerToken);
                }
            }

            HttpResponse<String> response = httpClient.send(
                    requestBuilder.build(),
                    HttpResponse.BodyHandlers.ofString()
            );

            if (response.statusCode() == 200 || response.statusCode() == 302) {
                return response.headers()
                        .firstValue(DIGEST_HEADER)
                        .orElse(null);
            }

            log.warn("GHCR digest 조회 실패: {} → HTTP {}", url, response.statusCode());
            return null;

        } catch (IOException | InterruptedException e) {
            log.error("GHCR 요청 오류: {}", url, e);
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return null;
        }
    }

    /**
     * GHCR 토큰 교환: Basic(username:PAT) → Bearer token
     * imageRef에서 owner를 추출하여 scope를 구성
     */
    String exchangeToken(String imageRef) {
        String repoPath = imageRef.replaceFirst("^ghcr\\.io/", "");
        // owner/image에서 owner 추출
        String owner = repoPath.contains("/") ? repoPath.split("/")[0] : repoPath;
        String scope = "repository:" + repoPath + ":pull";
        String tokenUrl = "https://ghcr.io/token?service=ghcr.io&scope=" + scope;

        // username은 imageRef의 owner로 사용
        String basic = Base64.getEncoder().encodeToString((owner + ":" + pat).getBytes());

        try {
            HttpResponse<String> resp = httpClient.send(
                    HttpRequest.newBuilder()
                            .uri(URI.create(tokenUrl))
                            .header("Authorization", "Basic " + basic)
                            .timeout(Duration.ofSeconds(10))
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofString()
            );
            if (resp.statusCode() == 200) {
                return parseToken(resp.body());
            }
            log.warn("GHCR 토큰 교환 실패: HTTP {}", resp.statusCode());
        } catch (IOException | InterruptedException e) {
            log.error("GHCR 토큰 교환 오류", e);
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
        }
        return null;
    }

    /** JSON에서 "token" 필드 추출 (Jackson 없이 간단 파싱) */
    private String parseToken(String json) {
        int idx = json.indexOf("\"token\"");
        if (idx < 0) idx = json.indexOf("\"access_token\"");
        if (idx < 0) return null;
        int start = json.indexOf('"', idx + 7) + 1;
        int end = json.indexOf('"', start);
        return (start > 0 && end > start) ? json.substring(start, end) : null;
    }

    /**
     * GHCR v2 manifests API URL 생성
     * ghcr.io/owner/app:latest → https://ghcr.io/v2/owner/app/manifests/latest
     */
    String buildUrl(String imageRef, String tag) {
        String path = imageRef.replaceFirst("^ghcr\\.io/", "");
        return "https://ghcr.io/v2/" + path + "/manifests/" + tag;
    }

    boolean isAnonymous() {
        return pat == null || pat.isEmpty();
    }
}
