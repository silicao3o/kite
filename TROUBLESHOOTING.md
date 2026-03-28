# Troubleshooting 기록

> lite-k8s docker-monitor 운영 중 발생한 이슈 및 해결 방법 정리

---

## 1. Thymeleaf onclick XSS 차단 오류

**발생일**: 2026-03-19
**증상**:
```
Only variable expressions returning numbers or booleans are allowed in this context
(template: "nodes" - line 124, col 48)
```

**원인**: Thymeleaf는 `onclick` 등 DOM 이벤트 핸들러 속성에 String 변수를 직접 사용하는 것을 XSS 위험으로 간주해 차단

**잘못된 코드**:
```html
<button th:onclick="'removeNode(\'' + ${node.id} + '\')'">제거</button>
```

**수정**:
```html
<button th:data-node-id="${node.id}" onclick="removeNode(this.dataset.nodeId)">제거</button>
```

---

## 2. 삭제된 컨테이너 stats 수집 시 ERROR 로그 과다

**발생일**: 2026-03-19
**증상**:
```
ERROR c.lite_k8s.service.MetricsCollector - Stats 수집 에러: <container_id>
com.github.dockerjava.api.exception.NotFoundException: Status 404
```

**원인**: 컨테이너가 삭제된 후에도 MetricsCollector가 stats 수집을 시도

**수정**: `NotFoundException`은 ERROR 대신 DEBUG로 조용히 스킵
```java
// onError 콜백
if (throwable instanceof NotFoundException) {
    log.debug("Stats 수집 스킵 — 컨테이너 없음: {}", containerId);
} else {
    log.error("Stats 수집 에러: {}", containerId, throwable);
}
```

---

## 3. 삭제된 컨테이너 상세 조회 시 ERROR 로그

**발생일**: 2026-03-19
**증상**:
```
ERROR com.lite_k8s.service.DockerService - 컨테이너 조회 실패: <container_id>
com.github.dockerjava.api.exception.NotFoundException: Status 404
```

**원인**: 대시보드에서 이미 삭제된 컨테이너 클릭 시 `getContainer()`가 404를 ERROR로 처리

**수정**: `NotFoundException` 분리 처리
```java
} catch (NotFoundException e) {
    log.debug("컨테이너 없음: {}", containerId);
    return null;
} catch (Exception e) {
    log.error("컨테이너 조회 실패: {}", containerId, e);
    return null;
}
```
컨트롤러에서 null이면 `redirect:/` 처리되므로 UX상 문제 없음

---

## 4. 원격 노드 컨테이너가 대시보드에 미표시

**발생일**: 2026-03-19
**증상**: 노드 등록 후 해당 VM의 컨테이너가 컨테이너 탭에 표시되지 않음

**원인**: `DockerService.listContainers()`가 로컬 Docker 소켓만 조회

**수정**: 로컬 + 등록된 모든 노드 순차 조회로 변경
```java
// 로컬 컨테이너
dockerClient.listContainersCmd().withShowAll(showAll).exec()
        .stream().map(c -> toContainerInfo(c, null, "local"))
        .forEach(result::add);

// 등록된 노드 컨테이너
for (Node node : nodeRegistry.findAll()) {
    try {
        DockerClient client = nodeClientFactory.createClient(node);
        client.listContainersCmd().withShowAll(showAll).exec()
                .stream().map(c -> toContainerInfo(c, node.getId(), node.getName()))
                .forEach(result::add);
    } catch (Exception e) {
        log.warn("[멀티노드] {} ({}:{}) 컨테이너 조회 실패: {}",
                node.getName(), node.getHost(), node.getPort(), e.getMessage());
    }
}
```

대시보드 컨테이너 테이블에 Node 컬럼 추가 (local=파란 배지, 원격=초록 배지)

---

## 5. 원격 노드 컨테이너 상세 조회 불가

**발생일**: 2026-03-19
**증상**: 원격 노드 컨테이너 클릭 시 상세 페이지가 열리지 않고 메인으로 redirect

**원인**: `DockerService.getContainer()`가 로컬 Docker 클라이언트로만 inspect 시도. 원격 컨테이너 ID는 로컬에 없으므로 NotFoundException → null 반환

**수정**: 로컬 없으면 등록된 노드를 순차 탐색
```java
// 로컬에서 먼저 조회
try {
    return toContainerInfo(dockerClient.inspectContainerCmd(containerId).exec());
} catch (NotFoundException ignored) { }

// 등록된 노드에서 탐색
for (Node node : nodeRegistry.findAll()) {
    try {
        DockerClient client = nodeClientFactory.createClient(node);
        return toContainerInfo(client.inspectContainerCmd(containerId).exec());
    } catch (NotFoundException ignored) { }
}
```

---

## 6. 노드 페이지 컨테이너 수 항상 0으로 표시

**발생일**: 2026-03-19
**증상**: 노드 카드의 Containers 항목이 항상 0

**원인**: `NodeHeartbeatChecker`가 Docker API 연결 확인만 하고 `runningContainers` 필드를 업데이트하지 않음

**수정**: Heartbeat 시 컨테이너 수 업데이트
```java
List<Container> running = client.listContainersCmd().withShowAll(false).exec();
node.setRunningContainers(running.size());
nodeRegistry.updateStatus(node.getId(), NodeStatus.HEALTHY);
```

---

## 7. 노드 등록 시 외부 IP 연결 타임아웃

**발생일**: 2026-03-19
**증상**:
```
WARN [멀티노드] gcp-operia 컨테이너 조회 실패:
ConnectTimeoutException: Connect to http://34.50.25.246:2375 failed: Connection timed out
```

**원인**: GCP VM의 외부 IP로 노드를 등록. Docker daemon은 기본적으로 외부 IP로 TCP를 열지 않음

**해결**: 같은 VPC 내 GCP VM은 **내부 IP**로 등록 (`10.178.x.x:2375`)
온프레미스 VM은 **SSH 터널** 방식 사용 (connectionType: SSH)

---

## 8. 환경변수로 설정한 노드가 자동 등록 안 됨

**발생일**: 2026-03-19
**원인**: `NODES_ENABLED=false`가 기본값이라 `NodeConfig.registerNodes()`가 실행 안 됨

**해결**: 환경변수 설정 방법
```bash
NODES_ENABLED=true
DOCKER_MONITOR_NODES_NODES_0_NAME=gcp-vm1
DOCKER_MONITOR_NODES_NODES_0_HOST=10.178.0.15
DOCKER_MONITOR_NODES_NODES_0_PORT=2375
```

---

## 9. 삭제된 컨테이너 로그 조회 시 ERROR 로그

**발생일**: 2026-03-19
**증상**:
```
ERROR c.g.d.a.async.ResultCallbackTemplate - Error during callback
ERROR com.lite_k8s.service.DockerService - 로그 조회 실패: <container_id>
com.github.dockerjava.api.exception.NotFoundException: Status 404
```

**원인**: `getContainerLogs()`가 삭제된 컨테이너의 로그를 조회할 때 NotFoundException을 ERROR로 처리

**수정**: NotFoundException은 DEBUG로 스킵
```java
} catch (NotFoundException e) {
    log.debug("로그 조회 스킵 — 컨테이너 없음: {}", containerId);
    return "";
}
```

---

## 10. 재시작 시 DB에 등록된 노드 전체 삭제

**발생일**: 2026-03-21
**증상**: 앱 재시작 후 REST API로 동적 추가했던 노드가 사라짐

**원인**: `NodeConfig.registerNodes()`에서 `deleteAllConfigNodes()`가 `jpa.deleteAll()`을 호출해 DB 전체를 삭제 후 설정 파일 노드만 재등록

**수정**: `deleteAllConfigNodes()` 제거, `registerIfAbsent()` 도입 — name 기준으로 DB에 없을 때만 저장
```java
// NodeJpaRepository
boolean existsByName(String name);
Optional<Node> findByName(String name);

// NodeRegistry
public boolean registerIfAbsent(Node node) {
    return jpa.findByName(node.getName()).map(existing -> {
        runtimeCache.put(existing.getId(), existing);
        return false;  // 이미 존재, 스킵
    }).orElseGet(() -> {
        register(node);
        return true;  // 새로 등록
    });
}
```

---

## 11. Desired State 컨테이너 재생성 시 이름에 -1 suffix 붙는 문제

**발생일**: 2026-03-21
**증상**: `docker-compose`로 생성된 `exercise-auth`가 죽으면 `exercise-auth-1`로 재생성됨

**원인**: `nextAvailableIndex()`가 running 컨테이너 없을 때 `orElse(0) + 1 = 1`을 반환해 index가 항상 1부터 시작

**수정**:
1. `nextAvailableIndex()` — `orElse(0)` → `orElse(-1)` (빈 상태에서 0 반환)
2. `ContainerFactory.create()` — index == 0이면 suffix 없이 prefix 그대로 사용

```java
// StateReconciler
.orElse(-1) + 1;  // 빈 상태 → 0 반환

// ContainerFactory
String containerName = (index == 0)
    ? spec.getContainerNamePrefix()
    : spec.getContainerNamePrefix() + "-" + index;
```

**결과**:
- replicas=1: `exercise-auth` (suffix 없음) ✓
- replicas=2: `exercise-auth`, `exercise-auth-1` ✓

---

## 12. SSH_PROXY 노드 연결 시 Docker TCP 2375 개방 필요

**발생일**: 2026-03-26
**증상**: 온프레미스 노드를 SSH_PROXY로 등록하려면 대상 서버에서 Docker TCP 2375를 열어야 했음. daemon.json 수정 또는 socat 서비스 등록 필요.

**원인**: 기존 JSch `session.setPortForwardingL(localPort, host, port)` 는 TCP→TCP 포워딩만 지원. `/var/run/docker.sock` (Unix 도메인 소켓) 직접 포워딩 불가.

**해결**: mwiede/jsch 0.2.18이 이미 `direct-streamlocal@openssh.com` 지원 → 라이브러리 교체 없이 `setSocketForwardingL` 사용.

`SshTunnelManager.java` 수정:

```java
// SSH 노드 (직접 연결): setPortForwardingL → setSocketForwardingL
session.setSocketForwardingL(null, localPort, "/var/run/docker.sock", null, 10_000);

// SSH_PROXY 노드: sshUser 설정 여부로 모드 분기
private boolean useSocketForwarding(Node node) {
    return node.getSshUser() != null && !node.getSshUser().isBlank();
}
```

소켓 모드 (sshUser 설정됨) — 2홉 구조:
1. CP 세션 → target SSH 포트로 TCP 포워딩
2. 터널 통해 target SSH 접속
3. target 세션 → `/var/run/docker.sock` 소켓 포워딩

TCP 모드 (sshUser 미설정) — 기존 방식 하위 호환 유지.

`nodes.html` 수정: SSH_PROXY 타입에서도 sshUser/sshPort 필드 표시하도록 `toggleSshFields()` 수정.

**부수 버그**: sshKeyPath를 UI에서 비워두면 `null`이 아닌 `""`가 저장돼 proxy keyPath fallback이 안 타는 문제.
```java
// 수정 전 — null만 체크
String targetKeyPath = node.getSshKeyPath() != null ? node.getSshKeyPath() : proxy.getKeyPath();

// 수정 후 — blank 포함 체크
String targetKeyPath = node.getSshKeyPath() != null && !node.getSshKeyPath().isBlank()
    ? node.getSshKeyPath() : proxy.getKeyPath();
```

**결과**: 온프레미스 노드 추가 시 ssh-user만 입력하면 소켓 모드 활성화. 대상 서버 설정 변경 불필요.

---

## 13. oom/kill 이벤트 중복 처리 및 종료 원인 미식별

**발생일**: 2026-03-26
**증상**:
- OOM으로 컨테이너가 죽으면 알림이 2회 발송될 수 있음
- kill로 종료된 컨테이너의 deathReason이 exit code 기반으로만 분석돼 원인 불명확

**원인**: Docker 이벤트 발생 순서가 `oom → die`, `kill → die` 인데 기존 코드에서 `oom`을 `die`와 동일하게 처리 (`DEATH_ACTIONS = Set.of("die", "oom")`). `kill`은 아예 무시.

**해결**: `recentDeathCauses` 맵으로 사전 이벤트 원인을 임시 저장, `die`에서 꺼내 사용.

```java
// 변경 전
private static final Set<String> DEATH_ACTIONS = Set.of("die", "oom");

// 변경 후
private static final Set<String> PRE_DEATH_ACTIONS = Set.of("oom", "kill");
private final Map<String, String> recentDeathCauses = new ConcurrentHashMap<>();

// oom/kill → 원인만 기록, 알림 미실행
if (PRE_DEATH_ACTIONS.contains(actionLower)) {
    recentDeathCauses.put(containerId, actionLower);
    return;
}

// die → 사전 원인 있으면 사용, 없으면 exit code 분석
if ("die".equals(actionLower)) {
    String preCause = recentDeathCauses.remove(containerId);
    String deathReason = preCause != null ? preCause : exitCodeAnalyzer.analyze(deathEvent);
    deathEvent.setDeathReason(deathReason);
    // 알림, 자가치유, AI 분석 실행
}
```

필터링/중복으로 조기 return 시에도 `recentDeathCauses.remove(containerId)` 호출해 메모리 누수 방지.

**결과**:

| 시나리오 | 변경 전 | 변경 후 |
|---------|--------|--------|
| OOM 종료 | oom + die 이중 처리 가능 | die에서만 처리, deathReason = "oom" |
| kill 종료 | kill 무시, 원인 불명 | die에서 처리, deathReason = "kill" |
| 일반 종료 | die에서 exit code 분석 | 동일 |

---

## 14. Crash Loop 감지 불가 — dedup이 SelfHealing까지 차단

**발생일**: 2026-03-29
**증상**: DB 스키마 불일치 등으로 컨테이너가 계속 재기동해도 crash loop로 감지되지 않음.

**원인**: `DockerEventListener.handleEvent()`에서 dedup 체크가 `return`으로 전체 처리 블록을 차단. `SelfHealingService`도 실행되지 않아 `RestartTracker` 카운터가 쌓이지 않음.

```java
// 변경 전 — dedup 차단 시 SelfHealing도 실행 안 됨
if (!deduplicationService.shouldAlert(containerId, action)) {
    return;  // SelfHealingService.handleContainerDeath() 도달 불가
}
```

실제 crash loop 흐름:
```
1st die → dedup 통과 → SelfHealing → count=1 → 재시작
2nd die → dedup 차단 → return  ← count 영원히 1, 임계치 도달 불가
```

**해결**: dedup을 이메일 알림 직전으로 이동. SelfHealing은 항상 실행.

```java
// 변경 후
selfHealingService.handleContainerDeath(deathEvent);  // 항상 실행

if (deduplicationService.shouldAlert(containerId, action)) {
    notificationService.sendAlert(deathEvent);  // 이메일만 dedup
}
```

crash loop 임계치(5분 내 3회) 초과 시 동작:
- `dockerService.stopContainer(containerId, nodeId)` — 컨테이너 강제 정지
- `emailNotificationService.sendCrashLoopStoppedAlert()` — `[CRASH LOOP]` 전용 알림 발송

`DockerService.stopContainer(containerId, nodeId)` 신규 추가 (멀티 노드 지원).

---

## 15. 원격 노드 컨테이너 buildDeathEvent — 로컬 클라이언트 오사용

**발생일**: 2026-03-29
**증상**: 원격 노드 컨테이너가 죽어도 자가치유 실행 안 됨. 알림에 컨테이너 이름 "Unknown", 라벨 null.

**원인**: `DockerService.buildDeathEvent(containerId, action)` 가 nodeId 파라미터 없이 항상 로컬 `dockerClient`로 inspect.

```java
// 변경 전 — nodeId 없음, 항상 로컬
InspectContainerResponse inspection = dockerClient.inspectContainerCmd(containerId).exec();
// 원격 컨테이너 → NotFoundException → containerName="Unknown", labels=null
// → SelfHealingService: "Unknown"은 아무 규칙과도 매칭 안 됨 → 자가치유 없음
```

`DockerEventListener`에서 nodeId를 이미 받고 있었으나 `buildDeathEvent` 호출 후에야 세팅:
```java
ContainerDeathEvent deathEvent = dockerService.buildDeathEvent(containerId, action); // 이미 실패
deathEvent.setNodeId(nodeId);  // 너무 늦음
```

참고: `getContainerLogs()`는 Phase 6에서 노드 fallback이 추가됐으나 `buildDeathEvent`는 누락됐었음.

**해결**: `buildDeathEvent(containerId, action, nodeId)` 3-arg 오버로드 추가.

```java
public ContainerDeathEvent buildDeathEvent(String containerId, String action, String nodeId) {
    DockerClient client = resolveClient(nodeId);
    return buildDeathEventWithClient(containerId, action, client);
}

private DockerClient resolveClient(String nodeId) {
    if (nodeId == null || nodeRegistry == null) return dockerClient;
    return nodeRegistry.findById(nodeId)
            .map(nodeClientFactory::createClient)
            .orElse(dockerClient);
}
```

`DockerEventListener`에서 `buildDeathEvent(containerId, action, nodeId)` 로 호출.

**결과**:

| 시나리오 | 변경 전 | 변경 후 |
|---------|--------|--------|
| 로컬 컨테이너 die | 정상 | 동일 |
| 원격 노드 컨테이너 die | containerName="Unknown", labels=null → 자가치유 없음 | 해당 노드 클라이언트로 inspect → 정상 이름/라벨 → 자가치유 동작 |
