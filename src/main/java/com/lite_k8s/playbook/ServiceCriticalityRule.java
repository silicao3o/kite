package com.lite_k8s.playbook;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 서비스 중요도 규칙
 *
 * 컨테이너 이름 패턴과 중요도 레벨 매핑
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ServiceCriticalityRule {

    /**
     * 컨테이너 이름 패턴 (와일드카드 지원: *)
     */
    private String namePattern;

    /**
     * 서비스 중요도
     */
    private ServiceCriticality criticality;
}
