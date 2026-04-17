package com.lite_k8s.update;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class ImageWatchControllerTest {

    @Mock private ImageWatchService watchService;
    @Mock private ImageUpdateHistoryService historyService;

    @InjectMocks
    private ImageWatchController controller;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    @DisplayName("18. POST /api/image-watches 로 와치를 생성할 수 있다")
    void create() throws Exception {
        ImageWatchEntity saved = ImageWatchEntity.builder()
                .image("ghcr.io/org/app")
                .tag("latest")
                .containerPattern("app-.*")
                .maxUnavailable(1)
                .build();
        when(watchService.save(any())).thenReturn(saved);

        mockMvc.perform(post("/api/image-watches")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "image", "ghcr.io/org/app",
                                "containerPattern", "app-.*"
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.image").value("ghcr.io/org/app"));
    }

    @Test
    @DisplayName("18-1. POST /api/image-watches image 없으면 400")
    void create_withoutImage_returns400() throws Exception {
        mockMvc.perform(post("/api/image-watches")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "containerPattern", "app-.*"
                        ))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("19. GET /api/image-watches 로 와치 목록을 조회할 수 있다")
    void list() throws Exception {
        when(watchService.findAll()).thenReturn(List.of(
                ImageWatchEntity.builder().image("ghcr.io/org/app1").build(),
                ImageWatchEntity.builder().image("ghcr.io/org/app2").build()
        ));

        mockMvc.perform(get("/api/image-watches"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    @DisplayName("20. PUT /api/image-watches/{id} 로 와치를 수정할 수 있다")
    void update() throws Exception {
        ImageWatchEntity entity = ImageWatchEntity.builder()
                .image("ghcr.io/org/app")
                .tag("latest")
                .containerPattern("app-.*")
                .build();
        when(watchService.findById(entity.getId())).thenReturn(Optional.of(entity));
        when(watchService.save(any())).thenReturn(entity);

        mockMvc.perform(put("/api/image-watches/" + entity.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "tag", "v2.0",
                                "maxUnavailable", 3
                        ))))
                .andExpect(status().isOk());

        verify(watchService).save(any());
    }

    @Test
    @DisplayName("21. DELETE /api/image-watches/{id} 로 와치를 비활성화할 수 있다")
    void deleteWatch() throws Exception {
        mockMvc.perform(delete("/api/image-watches/some-id"))
                .andExpect(status().isNoContent());

        verify(watchService).disable("some-id");
    }

    @Test
    @DisplayName("22. GET /api/image-watches/{id}/history 로 업데이트 이력을 조회할 수 있다")
    void history() throws Exception {
        when(historyService.findByWatchId("w1")).thenReturn(List.of(
                ImageUpdateHistoryEntity.builder()
                        .watchId("w1").image("app")
                        .status(ImageUpdateHistoryEntity.Status.SUCCESS).build()
        ));

        mockMvc.perform(get("/api/image-watches/w1/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("SUCCESS"));
    }

    @Test
    @DisplayName("GET 응답에서 ghcrToken이 마스킹된다")
    void list_MasksGhcrToken() throws Exception {
        when(watchService.findAll()).thenReturn(List.of(
                ImageWatchEntity.builder()
                        .image("ghcr.io/org/app")
                        .ghcrToken("ghp_abcdefgh12345678")
                        .build()
        ));

        mockMvc.perform(get("/api/image-watches"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].ghcrToken").value("ghp_****"));
    }

    @Test
    @DisplayName("POST에서 ghcrToken을 저장할 수 있다")
    void create_WithGhcrToken() throws Exception {
        ImageWatchEntity saved = ImageWatchEntity.builder()
                .image("ghcr.io/org/app")
                .ghcrToken("ghp_realtoken123")
                .build();
        when(watchService.save(any())).thenReturn(saved);

        mockMvc.perform(post("/api/image-watches")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "image", "ghcr.io/org/app",
                                "ghcrToken", "ghp_realtoken123"
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ghcrToken").value("ghp_****"));

        verify(watchService).save(argThat(e -> "ghp_realtoken123".equals(e.getGhcrToken())));
    }

    @Test
    @DisplayName("PUT에서 마스킹된 토큰이 오면 기존 토큰을 유지한다")
    void update_WithMaskedToken_KeepsExisting() throws Exception {
        ImageWatchEntity entity = ImageWatchEntity.builder()
                .image("ghcr.io/org/app")
                .ghcrToken("ghp_original_secret")
                .build();
        when(watchService.findById(entity.getId())).thenReturn(Optional.of(entity));
        when(watchService.save(any())).thenReturn(entity);

        mockMvc.perform(put("/api/image-watches/" + entity.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "ghcrToken", "ghp_****"
                        ))))
                .andExpect(status().isOk());

        // 마스킹된 값이 왔으므로 기존 토큰 유지
        verify(watchService).save(argThat(e -> "ghp_original_secret".equals(e.getGhcrToken())));
    }

    @Test
    @DisplayName("PUT에서 새 토큰이 오면 업데이트한다")
    void update_WithNewToken_UpdatesToken() throws Exception {
        ImageWatchEntity entity = ImageWatchEntity.builder()
                .image("ghcr.io/org/app")
                .ghcrToken("ghp_old_token")
                .build();
        when(watchService.findById(entity.getId())).thenReturn(Optional.of(entity));
        when(watchService.save(any())).thenReturn(entity);

        mockMvc.perform(put("/api/image-watches/" + entity.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "ghcrToken", "ghp_brand_new_token"
                        ))))
                .andExpect(status().isOk());

        verify(watchService).save(argThat(e -> "ghp_brand_new_token".equals(e.getGhcrToken())));
    }
}
