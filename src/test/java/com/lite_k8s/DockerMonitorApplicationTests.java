package com.lite_k8s;

import com.lite_k8s.listener.DockerEventListener;
import com.github.dockerjava.api.DockerClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mail.javamail.JavaMailSender;

@SpringBootTest
class DockerMonitorApplicationTests {

    @MockBean
    private DockerClient dockerClient;

    @MockBean
    private JavaMailSender javaMailSender;

    @MockBean
    private DockerEventListener dockerEventListener;

    @Test
    void contextLoads() {
        // 애플리케이션 컨텍스트가 정상적으로 로드되는지 확인
    }
}
