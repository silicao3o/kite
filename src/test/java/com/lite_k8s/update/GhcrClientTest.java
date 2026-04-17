package com.lite_k8s.update;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GhcrClientTest {

    private GhcrClient client;

    @BeforeEach
    void setUp() {
        client = new GhcrClient("test-token");
    }

    @Test
    @DisplayName("ghcr.io 이미지로 올바른 manifests URL 생성")
    void buildUrl_WithGhcrImage_ReturnsCorrectManifestUrl() {
        String url = client.buildUrl("ghcr.io/myorg/myapp", "latest");
        assertThat(url).isEqualTo("https://ghcr.io/v2/myorg/myapp/manifests/latest");
    }

    @Test
    @DisplayName("특정 태그 포함한 URL 생성")
    void buildUrl_WithSpecificTag_IncludesTag() {
        String url = client.buildUrl("ghcr.io/myorg/myapp", "v1.2.3");
        assertThat(url).isEqualTo("https://ghcr.io/v2/myorg/myapp/manifests/v1.2.3");
    }

    @Test
    @DisplayName("중첩 경로 이미지도 올바르게 처리")
    void buildUrl_WithNestedPath_PreservesPath() {
        String url = client.buildUrl("ghcr.io/myorg/sub/myapp", "latest");
        assertThat(url).isEqualTo("https://ghcr.io/v2/myorg/sub/myapp/manifests/latest");
    }

    @Test
    @DisplayName("빈 토큰으로 생성 시 isAnonymous() true")
    void isAnonymous_WhenEmptyToken_ReturnsTrue() {
        GhcrClient anonymous = new GhcrClient("");
        assertThat(anonymous.isAnonymous()).isTrue();
    }

    @Test
    @DisplayName("토큰 있으면 isAnonymous() false")
    void isAnonymous_WhenTokenProvided_ReturnsFalse() {
        assertThat(client.isAnonymous()).isFalse();
    }

    @Test
    @DisplayName("overrideToken이 있으면 해당 토큰의 isAnonymous 판별")
    void isAnonymousWithToken_WhenOverrideProvided() {
        GhcrClient anonymous = new GhcrClient("");
        // 글로벌은 anonymous지만 override 토큰이 있으면 false
        assertThat(anonymous.isAnonymous("override-token")).isFalse();
    }

    @Test
    @DisplayName("overrideToken이 null이면 글로벌 PAT로 폴백")
    void isAnonymousWithToken_WhenNull_FallsBackToGlobal() {
        // 글로벌 토큰이 있는 client
        assertThat(client.isAnonymous(null)).isFalse();
        // 글로벌 토큰이 없는 client
        GhcrClient anonymous = new GhcrClient("");
        assertThat(anonymous.isAnonymous(null)).isTrue();
    }
}
