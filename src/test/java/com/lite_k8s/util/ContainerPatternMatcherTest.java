package com.lite_k8s.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ContainerPatternMatcherTest {

    @Test
    @DisplayName("null/빈 패턴은 모두 매칭")
    void nullOrEmptyPatternMatchesAll() {
        assertThat(ContainerPatternMatcher.matches("anything", null)).isTrue();
        assertThat(ContainerPatternMatcher.matches("anything", "")).isTrue();
    }

    @Test
    @DisplayName("glob '*' 가 0개 이상 임의 문자에 매칭")
    void globStarMatchesAnyChars() {
        assertThat(ContainerPatternMatcher.matches("chat-quvi-test", "chat-quvi*")).isTrue();
        assertThat(ContainerPatternMatcher.matches("chat-quvi-test-nginx", "chat-quvi*")).isTrue();
        assertThat(ContainerPatternMatcher.matches("chat-quvi", "chat-quvi*")).isTrue();
        assertThat(ContainerPatternMatcher.matches("chat-other", "chat-quvi*")).isFalse();
    }

    @Test
    @DisplayName("glob '*X*' 양쪽 와일드카드")
    void globStarBothSides() {
        assertThat(ContainerPatternMatcher.matches("admin-quvi-prod", "*quvi*")).isTrue();
        assertThat(ContainerPatternMatcher.matches("quvi", "*quvi*")).isTrue();
        assertThat(ContainerPatternMatcher.matches("nope", "*quvi*")).isFalse();
    }

    @Test
    @DisplayName("glob '?' 한 글자 매칭")
    void globQuestionMatchesOneChar() {
        assertThat(ContainerPatternMatcher.matches("engine1", "engine?")).isTrue();
        assertThat(ContainerPatternMatcher.matches("engine12", "engine?")).isFalse();
    }

    @Test
    @DisplayName("정규식 패턴(.*) 도 그대로 동작")
    void regexPatternStillWorks() {
        assertThat(ContainerPatternMatcher.matches("myapp-1", "myapp-.*")).isTrue();
        assertThat(ContainerPatternMatcher.matches("other", "myapp-.*")).isFalse();
    }

    @Test
    @DisplayName("정확 일치 패턴(와일드카드 없음)")
    void exactMatchPattern() {
        assertThat(ContainerPatternMatcher.matches("engine", "engine")).isTrue();
        assertThat(ContainerPatternMatcher.matches("engine-2", "engine")).isFalse();
    }

    @Test
    @DisplayName("'*' 포함 글로브에서 점(.) 은 리터럴로 이스케이프")
    void globEscapesDotWhenWildcardPresent() {
        // '*' 가 있어 글로브 모드 — '.' 는 리터럴 점, '*' 는 .* 로 변환
        assertThat(ContainerPatternMatcher.matches("v3.0-app", "v3.0*")).isTrue();
        assertThat(ContainerPatternMatcher.matches("v3X0-app", "v3.0*")).isFalse();
    }
}
