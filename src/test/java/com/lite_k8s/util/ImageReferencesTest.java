package com.lite_k8s.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ImageReferencesTest {

    @Test
    @DisplayName("간단한 이미지명 + 태그")
    void simpleNameWithTag() {
        assertThat(ImageReferences.shortName("nginx:alpine")).isEqualTo("nginx");
    }

    @Test
    @DisplayName("호스트 + org + 이미지 + 태그")
    void hostOrgImageTag() {
        assertThat(ImageReferences.shortName("ghcr.io/daquv-core/chat-quvi:v3.0"))
                .isEqualTo("chat-quvi");
    }

    @Test
    @DisplayName("태그/digest 없는 ref")
    void noTagOrDigest() {
        assertThat(ImageReferences.shortName("ghcr.io/old-org/chat-quvi")).isEqualTo("chat-quvi");
    }

    @Test
    @DisplayName("digest 가 붙은 ref")
    void digestRef() {
        assertThat(ImageReferences.shortName("ghcr.io/x/chat-quvi@sha256:abc"))
                .isEqualTo("chat-quvi");
    }

    @Test
    @DisplayName("host:port 가 있는 사설 레지스트리")
    void hostPortRegistry() {
        assertThat(ImageReferences.shortName("localhost:5000/myapp:tag")).isEqualTo("myapp");
        assertThat(ImageReferences.shortName("localhost:5000/myapp")).isEqualTo("myapp");
    }

    @Test
    @DisplayName("null/빈 입력은 빈 문자열")
    void nullOrBlank() {
        assertThat(ImageReferences.shortName(null)).isEmpty();
        assertThat(ImageReferences.shortName("")).isEmpty();
        assertThat(ImageReferences.shortName("   ")).isEmpty();
    }

    @Test
    @DisplayName("sameShortName: 같은 short name 매칭 (org 변경 케이스)")
    void sameShortNameAcrossOrgs() {
        assertThat(ImageReferences.sameShortName(
                "ghcr.io/old-org/chat-quvi:latest",
                "ghcr.io/new-org/chat-quvi")).isTrue();
    }

    @Test
    @DisplayName("sameShortName: 다른 short name 분리 (nginx vs chat-quvi)")
    void differentShortNamesSeparated() {
        assertThat(ImageReferences.sameShortName(
                "nginx:alpine",
                "ghcr.io/daquv-core/chat-quvi:v3.0")).isFalse();
    }

    @Test
    @DisplayName("sameShortName: null/blank 입력은 false")
    void sameShortNameWithNull() {
        assertThat(ImageReferences.sameShortName(null, "nginx")).isFalse();
        assertThat(ImageReferences.sameShortName("nginx", null)).isFalse();
        assertThat(ImageReferences.sameShortName("", "")).isFalse();
    }

    @Test
    @DisplayName("isImageReference: ref 형식 (repo:tag, host/repo, digest pin) 은 true")
    void isImageReference_RefForms() {
        assertThat(ImageReferences.isImageReference("nginx:alpine")).isTrue();
        assertThat(ImageReferences.isImageReference("ghcr.io/org/app:latest")).isTrue();
        assertThat(ImageReferences.isImageReference("ghcr.io/org/app@sha256:abc")).isTrue();
        assertThat(ImageReferences.isImageReference("nginx")).isTrue(); // tag 없는 이름도 ref
    }

    @Test
    @DisplayName("isImageReference: image ID (sha256: 시작, 순수 hex 12자+) 은 false")
    void isImageReference_ImageIds() {
        assertThat(ImageReferences.isImageReference("sha256:abc123def456")).isFalse();
        assertThat(ImageReferences.isImageReference("f16c946af7c5")).isFalse();
        assertThat(ImageReferences.isImageReference("fcc45ae6f9aeba93bed1")).isFalse();
    }

    @Test
    @DisplayName("isImageReference: null/blank 은 false")
    void isImageReference_NullOrBlank() {
        assertThat(ImageReferences.isImageReference(null)).isFalse();
        assertThat(ImageReferences.isImageReference("")).isFalse();
        assertThat(ImageReferences.isImageReference("   ")).isFalse();
    }
}
