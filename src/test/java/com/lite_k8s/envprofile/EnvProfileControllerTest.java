package com.lite_k8s.envprofile;

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
class EnvProfileControllerTest {

    @Mock private EnvProfileService service;
    @InjectMocks private EnvProfileController controller;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    @DisplayName("POST /api/env-profiles — 프로파일 + 엔트리 동시 생성")
    void create() throws Exception {
        EnvProfile saved = EnvProfile.builder()
                .name("db-operia-postgres")
                .type(EnvProfile.ProfileType.DATABASE)
                .description("Operia DB")
                .build();
        when(service.saveProfile(any())).thenReturn(saved);
        when(service.saveEntry(any())).thenAnswer(inv -> inv.getArgument(0));

        mockMvc.perform(post("/api/env-profiles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "db-operia-postgres",
                                "type", "DATABASE",
                                "description", "Operia DB",
                                "entries", List.of(
                                        Map.of("key", "DB_HOST", "value", "112.187.198.214", "secret", false),
                                        Map.of("key", "DB_PASSWORD", "value", "qwer1234!", "secret", true)
                                )
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("db-operia-postgres"))
                .andExpect(jsonPath("$.type").value("DATABASE"));

        verify(service).saveProfile(any());
        verify(service, times(2)).saveEntry(any());
    }

    @Test
    @DisplayName("POST /api/env-profiles — name 없으면 400")
    void create_WithoutName_Returns400() throws Exception {
        mockMvc.perform(post("/api/env-profiles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "description", "no name"
                        ))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /api/env-profiles — 목록 조회")
    void list() throws Exception {
        when(service.findAll()).thenReturn(List.of(
                EnvProfile.builder().name("db-operia").build(),
                EnvProfile.builder().name("db-oracle").build()
        ));

        mockMvc.perform(get("/api/env-profiles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    @DisplayName("GET /api/env-profiles/{id} — 상세 조회 (엔트리 포함, secret 마스킹)")
    void getById() throws Exception {
        EnvProfile profile = EnvProfile.builder().name("db-operia").build();
        when(service.findById(profile.getId())).thenReturn(Optional.of(profile));
        when(service.getEntries(profile.getId())).thenReturn(List.of(
                EnvProfileEntry.builder().key("DB_HOST").value("10.0.0.1").secret(false).build(),
                EnvProfileEntry.builder().key("DB_PASSWORD").value("***").secret(true).build()
        ));

        mockMvc.perform(get("/api/env-profiles/" + profile.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("db-operia"))
                .andExpect(jsonPath("$.entries.length()").value(2))
                .andExpect(jsonPath("$.entries[1].value").value("***"));
    }

    @Test
    @DisplayName("PUT /api/env-profiles/{id} — 메타 수정")
    void update() throws Exception {
        EnvProfile profile = EnvProfile.builder().name("db-operia").description("old").build();
        when(service.findById(profile.getId())).thenReturn(Optional.of(profile));
        when(service.saveProfile(any())).thenReturn(profile);

        mockMvc.perform(put("/api/env-profiles/" + profile.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "description", "updated"
                        ))))
                .andExpect(status().isOk());

        verify(service).saveProfile(any());
    }

    @Test
    @DisplayName("PUT /api/env-profiles/{id}/entries/{key} — 엔트리 값 수정")
    void updateEntry() throws Exception {
        EnvProfileEntry entry = EnvProfileEntry.builder()
                .profileId("p1").key("DB_HOST").value("old-host").secret(false).build();
        when(service.findEntryByKey("p1", "DB_HOST")).thenReturn(Optional.of(entry));
        when(service.saveEntry(any())).thenAnswer(inv -> inv.getArgument(0));

        mockMvc.perform(put("/api/env-profiles/p1/entries/DB_HOST")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "value", "new-host"
                        ))))
                .andExpect(status().isOk());

        verify(service).saveEntry(argThat(e -> "new-host".equals(e.getValue())));
    }

    @Test
    @DisplayName("DELETE /api/env-profiles/{id} — soft delete")
    void deleteProfile() throws Exception {
        mockMvc.perform(delete("/api/env-profiles/some-id"))
                .andExpect(status().isNoContent());

        verify(service).softDelete("some-id");
    }
}
