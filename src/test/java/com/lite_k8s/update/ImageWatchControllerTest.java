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
    @Mock private ImageUpdatePoller poller;
    @Mock private com.lite_k8s.envprofile.ImageRegistryRepository imageRegistryRepository;

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

        verify(watchService).delete("some-id");
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
    @DisplayName("POST에서 nodeNames 배열을 저장할 수 있다")
    void create_WithNodeNames() throws Exception {
        ImageWatchEntity saved = ImageWatchEntity.builder()
                .image("ghcr.io/org/app")
                .nodeNames(List.of("worker-1", "worker-2"))
                .build();
        when(watchService.save(any())).thenReturn(saved);

        mockMvc.perform(post("/api/image-watches")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "image", "ghcr.io/org/app",
                                "nodeNames", List.of("worker-1", "worker-2")
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nodeNames.length()").value(2))
                .andExpect(jsonPath("$.nodeNames[0]").value("worker-1"))
                .andExpect(jsonPath("$.nodeNames[1]").value("worker-2"));

        verify(watchService).save(argThat(e ->
                e.getNodeNames().size() == 2
                && e.getNodeNames().contains("worker-1")
                && e.getNodeNames().contains("worker-2")
        ));
    }

    @Test
    @DisplayName("POST에서 pollIntervalSeconds를 저장할 수 있다")
    void create_WithPollIntervalSeconds() throws Exception {
        ImageWatchEntity saved = ImageWatchEntity.builder()
                .image("ghcr.io/org/app")
                .pollIntervalSeconds(60)
                .build();
        when(watchService.save(any())).thenReturn(saved);

        mockMvc.perform(post("/api/image-watches")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "image", "ghcr.io/org/app",
                                "pollIntervalSeconds", 60
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.pollIntervalSeconds").value(60));

        verify(watchService).save(argThat(e -> Integer.valueOf(60).equals(e.getPollIntervalSeconds())));
    }

    @Test
    @DisplayName("PUT에서 nodeNames와 pollIntervalSeconds를 수정할 수 있다")
    void update_WithNodeNamesAndPollInterval() throws Exception {
        ImageWatchEntity entity = ImageWatchEntity.builder()
                .image("ghcr.io/org/app")
                .build();
        when(watchService.findById(entity.getId())).thenReturn(Optional.of(entity));
        when(watchService.save(any())).thenReturn(entity);

        mockMvc.perform(put("/api/image-watches/" + entity.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "nodeNames", List.of("node-1", "node-2"),
                                "pollIntervalSeconds", 120
                        ))))
                .andExpect(status().isOk());

        verify(watchService).save(argThat(e ->
                e.getNodeNames().contains("node-1")
                && Integer.valueOf(120).equals(e.getPollIntervalSeconds())
        ));
    }

    @Test
    @DisplayName("GET 응답에 nodeNames와 pollIntervalSeconds가 포함된다")
    void list_IncludesNodeNamesAndPollInterval() throws Exception {
        when(watchService.findAll()).thenReturn(List.of(
                ImageWatchEntity.builder()
                        .image("ghcr.io/org/app")
                        .nodeNames(List.of("worker-1"))
                        .pollIntervalSeconds(120)
                        .build()
        ));

        mockMvc.perform(get("/api/image-watches"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].nodeNames[0]").value("worker-1"))
                .andExpect(jsonPath("$[0].pollIntervalSeconds").value(120));
    }

    @Test
    @DisplayName("POST /{id}/trigger 로 특정 와치를 즉시 트리거할 수 있다")
    void trigger() throws Exception {
        ImageWatchEntity entity = ImageWatchEntity.builder()
                .image("ghcr.io/org/app")
                .build();
        when(watchService.findById(entity.getId())).thenReturn(Optional.of(entity));

        mockMvc.perform(post("/api/image-watches/" + entity.getId() + "/trigger"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("triggered"));

        verify(poller).checkWatch(entity);
    }

    @Test
    @DisplayName("POST /{id}/trigger 존재하지 않는 와치면 404")
    void trigger_NotFound() throws Exception {
        when(watchService.findById("unknown")).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/image-watches/unknown/trigger"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("POST /trigger-all 로 전체 와치를 트리거할 수 있다")
    void triggerAll() throws Exception {
        mockMvc.perform(post("/api/image-watches/trigger-all"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("triggered"));

        verify(poller).triggerAll();
    }

    @Test
    @DisplayName("DELETE 시 스케줄도 해제된다")
    void delete_CancelsSchedule() throws Exception {
        mockMvc.perform(delete("/api/image-watches/some-id"))
                .andExpect(status().isNoContent());

        verify(watchService).delete("some-id");
        verify(poller).cancelSchedule("some-id");
    }

    @Test
    @DisplayName("POST에서 pollIntervalSeconds 미지정 시 기본 300이 적용된다")
    void create_DefaultPollInterval() throws Exception {
        ImageWatchEntity saved = ImageWatchEntity.builder()
                .image("ghcr.io/org/app")
                .build();
        when(watchService.save(any())).thenReturn(saved);

        mockMvc.perform(post("/api/image-watches")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "image", "ghcr.io/org/app"
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.pollIntervalSeconds").value(300));

        verify(watchService).save(argThat(e -> e.getPollIntervalSeconds() == 300));
    }
}
