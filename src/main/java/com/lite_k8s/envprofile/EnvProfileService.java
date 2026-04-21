package com.lite_k8s.envprofile;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class EnvProfileService {

    private final EnvProfileRepository profileRepository;
    private final EnvProfileEntryRepository entryRepository;
    private final CryptoService cryptoService;

    public EnvProfile saveProfile(EnvProfile profile) {
        profile.validate();
        return profileRepository.save(profile);
    }

    public List<EnvProfile> findAll() {
        return profileRepository.findByEnabled(true);
    }

    public Optional<EnvProfile> findById(String id) {
        return profileRepository.findById(id);
    }

    public void softDelete(String id) {
        profileRepository.findById(id).ifPresent(p -> {
            p.setEnabled(false);
            profileRepository.save(p);
        });
    }

    public EnvProfileEntry saveEntry(EnvProfileEntry entry) {
        entry.validate();
        if (entry.isSecret() && entry.getValue() != null) {
            entry.setValue(cryptoService.encrypt(entry.getValue()));
        }
        return entryRepository.save(entry);
    }

    /** 엔트리 목록 조회 — secret 값은 마스킹 */
    public List<EnvProfileEntry> getEntries(String profileId) {
        List<EnvProfileEntry> entries = entryRepository.findByProfileId(profileId);
        return entries.stream().map(e -> {
            if (e.isSecret()) {
                EnvProfileEntry masked = EnvProfileEntry.builder()
                        .id(e.getId())
                        .profileId(e.getProfileId())
                        .key(e.getKey())
                        .value("***")
                        .secret(true)
                        .build();
                return masked;
            }
            return e;
        }).toList();
    }

    public Optional<EnvProfileEntry> findEntryByKey(String profileId, String key) {
        return entryRepository.findByProfileIdAndKey(profileId, key);
    }

    /** 엔트리 복호화된 값 조회 (컨테이너 주입용) */
    public List<EnvProfileEntry> getDecryptedEntries(String profileId) {
        List<EnvProfileEntry> entries = entryRepository.findByProfileId(profileId);
        return entries.stream().map(e -> {
            if (e.isSecret() && e.getValue() != null) {
                EnvProfileEntry decrypted = EnvProfileEntry.builder()
                        .id(e.getId())
                        .profileId(e.getProfileId())
                        .key(e.getKey())
                        .value(cryptoService.decrypt(e.getValue()))
                        .secret(true)
                        .build();
                return decrypted;
            }
            return e;
        }).toList();
    }
}
