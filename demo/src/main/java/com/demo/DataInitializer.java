package com.demo;

import com.demo.model.Product;
import com.demo.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class DataInitializer {

    private final ProductRepository productRepository;

    @Bean
    ApplicationRunner init() {
        return args -> {
            if (productRepository.count() > 0) return;

            log.info("📦 초기 데이터 로드 중...");
            save("MacBook Pro 14\"", "Apple M3 Pro, 18GB RAM", 2_990_000, 10);
            save("iPhone 15 Pro",    "A17 Pro, 256GB",         1_550_000, 25);
            save("AirPods Pro 2",    "액티브 노이즈 캔슬링",     359_000, 50);
            save("iPad Air 11\"",    "M2 칩, Wi-Fi, 256GB",     979_000, 15);
            save("Apple Watch S9",   "41mm GPS 알루미늄",        559_000, 30);
            log.info("✅ 초기 상품 {}개 등록 완료", productRepository.count());
        };
    }

    private void save(String name, String desc, double price, int stock) {
        Product p = new Product();
        p.setName(name);
        p.setDescription(desc);
        p.setPrice(price);
        p.setStock(stock);
        productRepository.save(p);
    }
}
