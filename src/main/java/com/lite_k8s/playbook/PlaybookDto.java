package com.lite_k8s.playbook;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * YAML 파싱용 DTO
 */
public class PlaybookDto {

    private String name;
    private String description;
    private String riskLevel;
    private TriggerDto trigger;
    private List<ActionDto> actions;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getRiskLevel() { return riskLevel; }
    public void setRiskLevel(String riskLevel) { this.riskLevel = riskLevel; }
    public TriggerDto getTrigger() { return trigger; }
    public void setTrigger(TriggerDto trigger) { this.trigger = trigger; }
    public List<ActionDto> getActions() { return actions; }
    public void setActions(List<ActionDto> actions) { this.actions = actions; }

    public Playbook toPlaybook() {
        return Playbook.builder()
                .name(name)
                .description(description)
                .riskLevel(riskLevel != null ? RiskLevel.valueOf(riskLevel) : RiskLevel.LOW)
                .trigger(trigger != null ? trigger.toTrigger() : null)
                .actions(actions != null ? actions.stream()
                        .map(ActionDto::toAction)
                        .collect(Collectors.toList()) : null)
                .build();
    }

    public static class TriggerDto {
        private String event;
        private Map<String, String> conditions;

        public String getEvent() { return event; }
        public void setEvent(String event) { this.event = event; }
        public Map<String, String> getConditions() { return conditions; }
        public void setConditions(Map<String, String> conditions) { this.conditions = conditions; }

        public Trigger toTrigger() {
            return Trigger.builder()
                    .event(event)
                    .conditions(conditions)
                    .build();
        }
    }

    public static class ActionDto {
        private String name;
        private String type;
        private Map<String, String> params;
        private String when;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public Map<String, String> getParams() { return params; }
        public void setParams(Map<String, String> params) { this.params = params; }
        public String getWhen() { return when; }
        public void setWhen(String when) { this.when = when; }

        public Action toAction() {
            return Action.builder()
                    .name(name)
                    .type(type)
                    .params(params)
                    .when(when)
                    .build();
        }
    }
}
