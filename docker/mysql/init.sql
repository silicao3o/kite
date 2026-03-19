-- MySQL 초기화 스크립트
-- analytics 데이터베이스 설정

USE analytics;

-- 페이지뷰 로그 테이블
CREATE TABLE IF NOT EXISTS page_views (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    path        VARCHAR(255) NOT NULL,
    user_agent  VARCHAR(512),
    ip_address  VARCHAR(50),
    viewed_at   DATETIME     NOT NULL DEFAULT NOW(),
    INDEX idx_path (path),
    INDEX idx_viewed_at (viewed_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 주문 테이블
CREATE TABLE IF NOT EXISTS orders (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    product_id   BIGINT       NOT NULL,
    product_name VARCHAR(255) NOT NULL,
    quantity     INT          NOT NULL DEFAULT 1,
    total_price  DECIMAL(12, 2),
    status       VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    ordered_at   DATETIME     NOT NULL DEFAULT NOW(),
    INDEX idx_status (status),
    INDEX idx_ordered_at (ordered_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 샘플 데이터
INSERT INTO page_views (path, user_agent, ip_address) VALUES
    ('/', 'Mozilla/5.0 Chrome/120', '192.168.1.10'),
    ('/api/products', 'Mozilla/5.0 Safari/17', '192.168.1.11'),
    ('/api/products/1', 'curl/8.0', '192.168.1.12');

INSERT INTO orders (product_id, product_name, quantity, total_price, status) VALUES
    (1, 'MacBook Pro 14"', 1, 2990000.00, 'COMPLETED'),
    (2, 'iPhone 15 Pro',   2, 3100000.00, 'PENDING'),
    (3, 'AirPods Pro 2',   1,  359000.00, 'SHIPPED');
