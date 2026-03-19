package com.lite_k8s.playbook;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Playbook 실행기
 *
 * Playbook의 액션들을 순서대로 실행
 */
public class PlaybookExecutor {

    private static final Pattern TEMPLATE_PATTERN = Pattern.compile("\\{\\{(\\w+)}}");

    private final Map<String, ActionHandler> handlers;

    public PlaybookExecutor(Map<String, ActionHandler> handlers) {
        this.handlers = handlers;
    }

    /**
     * Playbook 실행
     *
     * @param playbook 실행할 Playbook
     * @param context 컨텍스트 데이터
     * @return 실행 결과
     */
    public PlaybookResult execute(Playbook playbook, Map<String, String> context) {
        List<ActionResult> results = new ArrayList<>();

        if (playbook.getActions() == null || playbook.getActions().isEmpty()) {
            return PlaybookResult.success(playbook.getName(), results);
        }

        for (Action action : playbook.getActions()) {
            ActionHandler handler = handlers.get(action.getType());
            if (handler == null) {
                return PlaybookResult.failure(
                        playbook.getName(),
                        "Unknown action type: " + action.getType(),
                        results
                );
            }

            // 템플릿 변수 치환
            Action resolvedAction = resolveTemplates(action, context);

            ActionResult result = handler.execute(resolvedAction, context);
            results.add(result);

            if (!result.isSuccess()) {
                return PlaybookResult.failure(playbook.getName(), result.getError(), results);
            }
        }

        return PlaybookResult.success(playbook.getName(), results);
    }

    private Action resolveTemplates(Action action, Map<String, String> context) {
        if (action.getParams() == null) {
            return action;
        }

        Map<String, String> resolvedParams = new HashMap<>();
        for (Map.Entry<String, String> entry : action.getParams().entrySet()) {
            String value = entry.getValue();
            resolvedParams.put(entry.getKey(), substituteVariables(value, context));
        }

        return Action.builder()
                .name(action.getName())
                .type(action.getType())
                .params(resolvedParams)
                .when(action.getWhen() != null ? substituteVariables(action.getWhen(), context) : null)
                .build();
    }

    private String substituteVariables(String template, Map<String, String> context) {
        Matcher matcher = TEMPLATE_PATTERN.matcher(template);
        StringBuilder result = new StringBuilder();

        while (matcher.find()) {
            String varName = matcher.group(1);
            String replacement = context.getOrDefault(varName, matcher.group(0));
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);

        return result.toString();
    }
}
