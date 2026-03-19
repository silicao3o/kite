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
