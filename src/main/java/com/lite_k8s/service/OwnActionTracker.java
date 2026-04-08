package com.lite_k8s.service;

import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 우리 자신이 호출한 Docker API 액션(restart, stop, kill, recreate 등)을 단기 마킹한다.
 * Docker 이벤트 스트림은 "누가 API를 호출했는지" 알려주지 않기 때문에,
 * 우리가 방금 restart한 컨테이너의 kill/die 이벤트를 "사용자의 docker stop"으로 오인할 수 있다.
 * 이 클래스는 그런 자체 호출을 TTL 기반으로 구분하는 역할을 한다.
 */
@Service
public class OwnActionTracker {

    private static final Duration DEFAULT_TTL = Duration.ofSeconds(10);

    private final Clock clock;
    private final Duration ttl;
    private final ConcurrentMap<String, Instant> marks = new ConcurrentHashMap<>();

    public OwnActionTracker() {
        this(Clock.systemUTC(), DEFAULT_TTL);
    }

    public OwnActionTracker(Clock clock, Duration ttl) {
        this.clock = clock;
        this.ttl = ttl;
    }

    public void markOwnAction(String containerId) {
        if (containerId == null) return;
        marks.put(containerId, clock.instant());
    }

    public boolean isOwnAction(String containerId) {
        if (containerId == null) return false;
        Instant marked = marks.get(containerId);
        if (marked == null) return false;
        Instant now = clock.instant();
        if (Duration.between(marked, now).compareTo(ttl) > 0) {
            marks.remove(containerId, marked);
            return false;
        }
        return true;
    }
}
