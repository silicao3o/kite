package com.lite_k8s.incident;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class SuggestionControllerTest {

    private SuggestionController controller;
    private SuggestionService service;

    @BeforeEach
    void setUp() {
        service = mock(SuggestionService.class);
        controller = new SuggestionController(service);
    }

    @Test
    @DisplayName("/suggestions 페이지는 전체 제안 목록을 모델에 담아 반환한다")
    void shouldReturnSuggestionsPage() {
        // given
        Model model = new ExtendedModelMap();
        Suggestion suggestion = buildSuggestion("web-server");
        when(service.findAll()).thenReturn(List.of(suggestion));
        when(service.findPending()).thenReturn(List.of(suggestion));

        // when
        String view = controller.suggestionsPage(model);

        // then
        assertThat(view).isEqualTo("suggestions");
        assertThat(model.asMap()).containsKey("suggestions");
        assertThat(model.asMap()).containsKey("pendingCount");
    }

    @Test
    @DisplayName("POST /suggestions/{id}/approve는 제안을 승인하고 리다이렉트한다")
    void shouldApproveAndRedirect() {
        // given
        RedirectAttributes redirectAttributes = new RedirectAttributesModelMap();

        // when
        String result = controller.approve("some-id", redirectAttributes);

        // then
        verify(service).approve("some-id");
        assertThat(result).isEqualTo("redirect:/suggestions");
    }

    @Test
    @DisplayName("POST /suggestions/{id}/reject는 제안을 거부하고 리다이렉트한다")
    void shouldRejectAndRedirect() {
        // given
        RedirectAttributes redirectAttributes = new RedirectAttributesModelMap();

        // when
        String result = controller.reject("some-id", redirectAttributes);

        // then
        verify(service).reject("some-id");
        assertThat(result).isEqualTo("redirect:/suggestions");
    }

    private Suggestion buildSuggestion(String containerName) {
        return Suggestion.builder()
                .containerName(containerName)
                .type(Suggestion.Type.CONFIG_OPTIMIZATION)
                .content("메모리 한도를 늘리세요")
                .patternOccurrenceCount(3)
                .build();
    }
}
