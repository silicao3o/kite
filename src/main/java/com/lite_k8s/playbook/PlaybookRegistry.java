package com.lite_k8s.playbook;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Playbook 저장소
 *
 * Playbook을 등록하고 이벤트 타입/조건에 따라 조회
 */
public class PlaybookRegistry {

    private final Map<String, Playbook> playbooksByName = new ConcurrentHashMap<>();
    private final Map<String, List<Playbook>> playbooksByEvent = new ConcurrentHashMap<>();

    /**
     * Playbook 등록
     */
    public void register(Playbook playbook) {
        playbooksByName.put(playbook.getName(), playbook);

        if (playbook.getTrigger() != null && playbook.getTrigger().getEvent() != null) {
            String event = playbook.getTrigger().getEvent();
            playbooksByEvent.computeIfAbsent(event, k -> new ArrayList<>()).add(playbook);
        }
    }

    /**
     * 이름으로 Playbook 조회
     */
    public Optional<Playbook> findByName(String name) {
        return Optional.ofNullable(playbooksByName.get(name));
    }

    /**
     * 이벤트 타입으로 Playbook 목록 조회
     */
    public List<Playbook> findByEvent(String eventType) {
        return playbooksByEvent.getOrDefault(eventType, Collections.emptyList());
    }

    /**
     * 이벤트 타입과 조건에 매칭되는 Playbook 목록 조회
     */
    public List<Playbook> findMatchingPlaybooks(String eventType, Map<String, String> eventData) {
        return findByEvent(eventType).stream()
                .filter(playbook -> matchesConditions(playbook, eventData))
                .collect(Collectors.toList());
    }

    private boolean matchesConditions(Playbook playbook, Map<String, String> eventData) {
        Trigger trigger = playbook.getTrigger();
        if (trigger == null || trigger.getConditions() == null || trigger.getConditions().isEmpty()) {
            // 조건이 없으면 항상 매칭
            return true;
        }

        // 모든 조건이 매칭되어야 함
        return trigger.getConditions().entrySet().stream()
                .allMatch(entry -> {
                    String expectedValue = entry.getValue();
                    String actualValue = eventData.get(entry.getKey());
                    return expectedValue.equals(actualValue);
                });
    }
}
