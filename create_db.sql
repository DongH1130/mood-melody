-- 데이터베이스 생성 (이미 존재하면 무시)
CREATE DATABASE IF NOT EXISTS mood_melody 
CHARACTER SET utf8mb4 
COLLATE utf8mb4_unicode_ci;

-- 사용할 데이터베이스 선택
USE mood_melody;

-- 필요한 테이블들은 애플리케이션 실행 시 Hibernate가 자동으로 생성할 예정
