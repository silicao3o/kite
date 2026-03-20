# 온프레미스 서버 Docker TCP API 설정 가이드

## 배경

kite(Docker Monitor)는 SSH_PROXY 방식으로 CP(10.178.0.3)를 점프 호스트로 경유하여 각 서버의 Docker API에 접근한다.

```
kite (gcp-lite-k8s) → SSH tunnel → CP (10.178.0.3) → 대상서버:2375
```

GCP VM들(gcp-operia, oracle-dev, claude-tracker)은 이미 Docker TCP API(2375)가 열려 있어 정상 동작 중이다.
온프레미스 서버(dev, res)는 Docker daemon이 Unix 소켓만 사용하고 있어 TCP API를 열어야 한다.

## 대상 서버

| SSH Host | IP | SSH Port | 현재 Docker TCP 상태 |
|----------|----|----------|---------------------|
| dev | 183.102.124.134 | 22 | 미개방 |
| res | 183.102.124.146 | 2222 | 미개방 |

## 왜 Docker TCP API를 열어야 하는가

- kite의 SSH_PROXY 연결은 CP를 경유하여 대상 서버의 `host:2375`로 포트포워딩함
- Docker daemon이 TCP 소켓을 listen하지 않으면 CP에서 포워딩해도 연결 불가
- dev/res에 있는 quvi, admin-quvi, engine, chat-quvi 등 컨테이너를 kite에서 모니터링/관리하려면 필수

## 보안 고려사항

Docker TCP API(2375)는 **인증 없는 root 수준 접근**이다. 공격자가 접근하면 서버 전체를 장악할 수 있다.

### 공격 벡터

1. **SSRF** — dev/res에서 돌고 있는 웹 앱(quvi 등)에 SSRF 취약점이 있으면 `http://localhost:2375`로 내부 호출 가능. **가장 현실적인 위협.**
2. **로컬 네트워크 침투** — 같은 네트워크의 다른 기기가 감염되면 직접 접근 가능
3. **공유기 포트포워딩** — 2375가 외부에 포워딩되면 Shodan 등 스캐너가 수분 내 탐지
4. **Docker API 악용** — `docker run -v /:/host --privileged`로 호스트 파일시스템 전체 마운트 가능

### 권장: 127.0.0.1만 바인딩

`0.0.0.0:2375`(모든 인터페이스) 대신 **`127.0.0.1:2375`**(localhost만)으로 열면:

- SSRF 공격 차단 불가 (localhost 접근이므로), 하지만 네트워크 공격은 차단
- CP에서 SSH 터널로 접근 시 대상 서버의 localhost로 포워딩되므로 **정상 동작**
- 외부/네트워크에서 직접 2375 접근 불가

> `0.0.0.0`으로 열 경우: 로컬 방화벽이 비활성화 상태(iptables ACCEPT, ufw inactive)이므로 네트워크 내 모든 기기에서 접근 가능. 비권장.

## 작업 절차

### 1. daemon.json 생성

각 서버에서:

```bash
sudo tee /etc/docker/daemon.json << 'EOF'
{
  "hosts": ["unix:///var/run/docker.sock", "tcp://127.0.0.1:2375"]
}
EOF
```

### 2. systemd override (충돌 방지)

기본 dockerd ExecStart에 `-H fd://`가 있어 daemon.json의 hosts와 충돌한다. override 필요:

```bash
sudo mkdir -p /etc/systemd/system/docker.service.d
sudo tee /etc/systemd/system/docker.service.d/override.conf << 'EOF'
[Service]
ExecStart=
ExecStart=/usr/bin/dockerd --containerd=/run/containerd/containerd.sock
EOF
```

### 3. Docker 재시작

```bash
sudo systemctl daemon-reload
sudo systemctl restart docker
```

### 4. 확인

```bash
ss -tlnp | grep 2375
# 예상 출력: LISTEN 127.0.0.1:2375

curl -s http://127.0.0.1:2375/version | head -1
# Docker API 응답 확인
```

### 5. 컨테이너 복구 확인

대부분의 컨테이너는 `restart: unless-stopped` 정책이므로 자동 복구된다.

**수동 시작 필요:**
- res: `docker start oracle-xe` (restart policy: no)
- dev: serene_merkle (Created 상태, 영향 없음)

## kite 설정 (.env.test)

Docker API가 열리면 `.env.test`에 노드 추가:

```env
DOCKER_MONITOR_NODES_NODES_3_NAME=dev
DOCKER_MONITOR_NODES_NODES_3_HOST=183.102.124.134
DOCKER_MONITOR_NODES_NODES_3_PORT=2375
DOCKER_MONITOR_NODES_NODES_3_CONNECTION_TYPE=SSH_PROXY

DOCKER_MONITOR_NODES_NODES_4_NAME=res
DOCKER_MONITOR_NODES_NODES_4_HOST=183.102.124.146
DOCKER_MONITOR_NODES_NODES_4_PORT=2375
DOCKER_MONITOR_NODES_NODES_4_CONNECTION_TYPE=SSH_PROXY
```

`docker-compose.test.yml`에서 NODES_3, NODES_4 환경변수 매핑 주석 해제 후 재배포.

> **주의**: dev/res는 CP(GCP 내부망)에서 SSH로 접근하므로, CP의 `~/.ssh/config`에 dev/res 접속 설정이 있어야 SSH_PROXY 터널이 정상 동작한다. 현재 CP에서 dev(:22), res(:2222) 모두 접속 가능 확인됨.
