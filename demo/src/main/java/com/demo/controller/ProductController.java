package com.demo.controller;

import com.demo.model.Product;
import com.demo.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductRepository productRepository;

    @GetMapping
    public List<Product> list() {
        log.info("GET /api/products - {} items", productRepository.count());
        return productRepository.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Product> get(@PathVariable Long id) {
        return productRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public Product create(@RequestBody Product product) {
        Product saved = productRepository.save(product);
        log.info("상품 등록: {} (id={})", saved.getName(), saved.getId());
        return saved;
    }

    @PutMapping("/{id}")
    public ResponseEntity<Product> update(@PathVariable Long id, @RequestBody Product body) {
        return productRepository.findById(id).map(p -> {
            p.setName(body.getName());
            p.setDescription(body.getDescription());
            p.setPrice(body.getPrice());
            p.setStock(body.getStock());
            return ResponseEntity.ok(productRepository.save(p));
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        if (!productRepository.existsById(id)) return ResponseEntity.notFound().build();
        productRepository.deleteById(id);
        log.info("상품 삭제: id={}", id);
        return ResponseEntity.noContent().build();
    }

    /** 자가치유 테스트용 - 앱을 강제 종료 */
    @PostMapping("/crash")
    public Map<String, String> crash(@RequestParam(defaultValue = "1") int exitCode) {
        log.warn("⚠️  /crash 호출 - {}초 후 exit({})", 2, exitCode);
        new Thread(() -> {
            try { Thread.sleep(2000); } catch (InterruptedException ignored) {}
            log.error("💥 강제 종료! exit code={}", exitCode);
            System.exit(exitCode);
        }).start();
        return Map.of("message", "2초 후 앱이 종료됩니다 (exit code=" + exitCode + ")");
    }

    /** OOM 테스트용 - 메모리를 빠르게 소진 */
    @PostMapping("/oom")
    public Map<String, String> oom() {
        log.warn("⚠️  /oom 호출 - 메모리 소진 시작");
        new Thread(() -> {
            try {
                java.util.ArrayList<byte[]> sink = new java.util.ArrayList<>();
                while (true) {
                    sink.add(new byte[10 * 1024 * 1024]); // 10MB씩 할당
                    Thread.sleep(200);
                }
            } catch (OutOfMemoryError | InterruptedException e) {
                log.error("OOM 발생", e);
                System.exit(137);
            }
        }).start();
        return Map.of("message", "메모리 소진 중... OOM Killer가 프로세스를 종료합니다");
    }

    /** 헬스 체크 */
    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of(
                "status", "UP",
                "db", "connected",
                "products", productRepository.count()
        );
    }
}
