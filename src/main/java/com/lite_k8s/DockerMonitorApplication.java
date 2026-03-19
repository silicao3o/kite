package com.lite_k8s;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.concurrent.CountDownLatch;

@SpringBootApplication
@EnableScheduling
public class DockerMonitorApplication {

    private static final CountDownLatch keepAliveLatch = new CountDownLatch(1);

    public static void main(String[] args) {
        SpringApplication.run(DockerMonitorApplication.class, args);

        // Shutdown hook 등록
        Runtime.getRuntime().addShutdownHook(new Thread(keepAliveLatch::countDown));

        try {
            // 애플리케이션을 데몬 모드로 유지
            keepAliveLatch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
