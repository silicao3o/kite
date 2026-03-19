# Docker Monitor 에러 로그 (2026-03-19)

## 1. Stats 수집 에러 (MetricsCollector)

```
2026-03-19 09:17:22 [docker-java-stream-*] ERROR c.lite_k8s.service.MetricsCollector - Stats 수집 에러: <container_id>
com.github.dockerjava.api.exception.NotFoundException: Status 404: {"message":"No such container: <container_id>"}
```

- **원인**: 삭제된 컨테이너의 stats를 수집하려고 시도
- **영향받는 컨테이너 ID**: bf8473807259, ae32ab6ec284, 7e1dcc1bff32, 7619cac2d886, bf8d0072c8fd, aed82fa51386

## 2. 컨테이너 조회 실패 (DockerService)

```
2026-03-19 09:18:22 [http-nio-8080-exec-8] ERROR com.lite_k8s.service.DockerService - 컨테이너 조회 실패: ae32ab6ec284acdc24450763a1a1ff0cf6de79211314b172a46a28a88c04447c
com.github.dockerjava.api.exception.NotFoundException: Status 404: {"message":"No such container: ae32ab6ec284..."}
```

- **원인**: 대시보드/WebSocket에서 이미 삭제된 컨테이너 상세 조회 시도
- **호출 경로**: `DashboardController.containerDetail()` → `DockerService.getContainer()`

## 수정 방향

- `MetricsCollector`: stats 수집 전 컨테이너 존재 여부 확인 또는 NotFoundException catch 후 캐시에서 제거
- `DockerService.getContainer()`: NotFoundException을 graceful하게 처리하여 404 응답 대신 "컨테이너 없음" 안내
