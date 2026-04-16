package com.lite_k8s.controller;

import com.lite_k8s.config.SelfHealingProperties;
import com.lite_k8s.model.ContainerInfo;
import com.lite_k8s.model.HealingEvent;
import com.lite_k8s.repository.HealingEventRepository;
import com.lite_k8s.service.ContainerLabelReader;
import com.lite_k8s.service.DockerService;
import com.lite_k8s.service.HealingRuleMatcher;
import com.lite_k8s.service.LogSearchService;
import com.lite_k8s.service.MetricsScheduler;
import com.lite_k8s.service.RestartTracker;
import com.lite_k8s.model.LogSearchResult;
import com.lite_k8s.node.NodeRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.List;

@Controller
@RequiredArgsConstructor
public class DashboardController {

    private final DockerService dockerService;
    private final SelfHealingProperties selfHealingProperties;
    private final ContainerLabelReader labelReader;
    private final HealingRuleMatcher ruleMatcher;
    private final RestartTracker restartTracker;
    private final HealingEventRepository healingEventRepository;
    private final MetricsScheduler metricsScheduler;
    private final LogSearchService logSearchService;
    private final MultiLogsProperties multiLogsProperties;
    private final NodeRegistry nodeRegistry;

    @GetMapping("/containers")
    public String dashboard(Model model,
                           @RequestParam(defaultValue = "true") boolean showAll,
                           @RequestParam(required = false) String nodeId) {
        List<ContainerInfo> containers = metricsScheduler.getCachedContainers();
        if (!showAll) {
            containers = containers.stream()
                    .filter(c -> "running".equalsIgnoreCase(c.getState()))
                    .toList();
        }

        if (nodeId != null) {
            if ("local".equals(nodeId)) {
                containers = containers.stream()
                        .filter(c -> c.getNodeId() == null)
                        .toList();
            } else {
                containers = containers.stream()
                        .filter(c -> nodeId.equals(c.getNodeId()))
                        .toList();
            }
        }

        // 자가치유 상태 및 메트릭 설정
        containers.forEach(this::setContainerInfo);

        long runningCount = containers.stream()
                .filter(c -> "running".equalsIgnoreCase(c.getState()))
                .count();
        long stoppedCount = containers.stream()
                .filter(c -> "exited".equalsIgnoreCase(c.getState()))
                .count();

        model.addAttribute("containers", containers);
        model.addAttribute("totalCount", containers.size());
        model.addAttribute("runningCount", runningCount);
        model.addAttribute("stoppedCount", stoppedCount);
        model.addAttribute("showAll", showAll);
        model.addAttribute("healingEnabled", selfHealingProperties.isEnabled());
        model.addAttribute("nodes", nodeRegistry.findAll());
        model.addAttribute("selectedNodeId", nodeId);

        return "dashboard";
    }

    private void setContainerInfo(ContainerInfo container) {
        setHealingInfo(container);
        setMetrics(container);
    }

    private void setMetrics(ContainerInfo container) {
        if (!"running".equalsIgnoreCase(container.getState())) {
            return;
        }
        metricsScheduler.getLatestMetrics(container.getId())
                .ifPresent(metrics -> {
                    container.setCpuPercent(metrics.getCpuPercent());
                    container.setMemoryUsage(metrics.getMemoryUsage());
                    container.setMemoryLimit(metrics.getMemoryLimit());
                    container.setMemoryPercent(metrics.getMemoryPercent());
                    container.setNetworkRxBytes(metrics.getNetworkRxBytes());
                    container.setNetworkTxBytes(metrics.getNetworkTxBytes());
                });
    }

    private void setHealingInfo(ContainerInfo container) {
        // 라벨에서 설정 확인
        var labelConfig = labelReader.readHealingConfig(container.getLabels());
        if (labelConfig.isPresent()) {
            container.setHealingEnabled(true);
            container.setMaxRestarts(labelConfig.get().getMaxRestarts());
        } else {
            // yml 규칙에서 설정 확인
            var ruleConfig = ruleMatcher.findMatchingRule(container.getName());
            if (ruleConfig.isPresent()) {
                container.setHealingEnabled(true);
                container.setMaxRestarts(ruleConfig.get().getMaxRestarts());
            } else {
                container.setHealingEnabled(false);
            }
        }

        // 재시작 횟수 설정
        container.setRestartCount(restartTracker.getRestartCount(container.getId()));
    }

    @GetMapping("/containers/{id}")
    public String containerDetail(@PathVariable String id, Model model) {
        ContainerInfo container = dockerService.getContainer(id);
        if (container == null) {
            return "redirect:/containers";
        }

        setHealingInfo(container);
        container.setEnvVars(dockerService.getContainerEnvVars(id, container.getNodeId()));
        String logs = dockerService.getContainerLogs(id, null, null); // 페이지 로드 시 — name 없이 ID로 조회
        List<HealingEvent> healingEvents = healingEventRepository.findByContainerId(id);

        model.addAttribute("container", container);
        model.addAttribute("logs", logs);
        model.addAttribute("healingEvents", healingEvents);

        return "container-detail";
    }

    @GetMapping("/api/containers/{id}/logs")
    @ResponseBody
    public String getContainerLogs(@PathVariable String id,
                                   @RequestParam(required = false) String name,
                                   @RequestParam(required = false) String nodeId) { // nodeId로 특정 노드에서만 이름 검색
        return dockerService.getContainerLogs(id, name, nodeId);
    }

    @GetMapping("/api/containers/{id}/logs/search")
    @ResponseBody
    public LogSearchResult searchContainerLogs(
            @PathVariable String id,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) List<String> levels,
            @RequestParam(required = false) String threadName,
            @RequestParam(required = false) String traceId,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String nodeId) {

        java.time.LocalDateTime fromTime = null;
        java.time.LocalDateTime toTime = null;

        if (from != null && !from.isEmpty()) {
            fromTime = java.time.LocalDateTime.parse(from);
        }
        if (to != null && !to.isEmpty()) {
            toTime = java.time.LocalDateTime.parse(to);
        }

        return logSearchService.search(id, keyword, fromTime, toTime, levels, threadName, traceId, name, nodeId);
    }

    @GetMapping("/healing-logs")
    public String healingLogs(Model model,
                              @RequestParam(required = false) Boolean success) {
        List<HealingEvent> events;
        if (success == null) {
            events = healingEventRepository.findAll();
        } else {
            events = healingEventRepository.findBySuccess(success);
        }
        model.addAttribute("events", events);
        model.addAttribute("healingEnabled", selfHealingProperties.isEnabled());
        model.addAttribute("successFilter", success);
        return "healing-logs";
    }

    @GetMapping("/api/healing-logs")
    @ResponseBody
    public List<HealingEvent> getHealingLogs() {
        return healingEventRepository.findAll();
    }

    // 메인 화면 — 로그인 후 기본 진입점. /multi-logs는 backward compat 용도로 유지
    @GetMapping({"/", "/multi-logs"})
    public String multiLogs(Model model) {
        model.addAttribute("containers", metricsScheduler.getCachedContainers());
        model.addAttribute("presets", multiLogsProperties.getPresets());
        return "multi-logs";
    }

    @GetMapping("/api/containers")
    @ResponseBody
    public List<ContainerInfo> getContainers() {
        return metricsScheduler.getCachedContainers();
    }
}
