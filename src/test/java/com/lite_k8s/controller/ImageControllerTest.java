package com.lite_k8s.controller;

import com.lite_k8s.service.ImageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ImageControllerTest {

    @Mock private ImageService imageService;

    private ImageController controller;

    @BeforeEach
    void setUp() {
        controller = new ImageController(imageService);
    }

    @Test
    @DisplayName("DELETE /api/images 성공 시 200 OK를 반환한다")
    void shouldReturn200WhenDeleteSucceeds() {
        doNothing().when(imageService).deleteImage("nginx:latest", null);

        ResponseEntity<String> response = controller.deleteImage("nginx:latest", null);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
    }

    @Test
    @DisplayName("DELETE /api/images 실패 시 500과 에러 메시지를 반환한다")
    void shouldReturn500WhenDeleteFails() {
        doThrow(new RuntimeException("image is being used by container abc123"))
                .when(imageService).deleteImage("nginx:latest", null);

        ResponseEntity<String> response = controller.deleteImage("nginx:latest", null);

        assertThat(response.getStatusCode().value()).isEqualTo(500);
        assertThat(response.getBody()).contains("image is being used by container abc123");
    }
}
