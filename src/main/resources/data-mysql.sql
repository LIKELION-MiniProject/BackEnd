-- MySQL용 데모 User 시드 (mysql 프로파일 전용).
-- H2 data.sql 의 MERGE INTO ... KEY(email) 는 MySQL 문법이 아니라, 여기서
-- INSERT ... ON DUPLICATE KEY UPDATE 로 옮겼다. 재기동 시 email unique 충돌을 피한다.
-- 평문 비밀번호는 두 계정 모두 "Passport1!" (BCrypt 해시).
INSERT INTO users (email, password, nickname, created_at) VALUES
    ('demo1@passport.ac.kr', '$2a$10$pS3tDnDC1WnJLOqoIR38deEO6Dq8B.saTilGemr62052r83srmmhS', '데모유저1', CURRENT_TIMESTAMP),
    ('demo2@passport.ac.kr', '$2a$10$pS3tDnDC1WnJLOqoIR38deEO6Dq8B.saTilGemr62052r83srmmhS', '데모유저2', CURRENT_TIMESTAMP)
    ON DUPLICATE KEY UPDATE nickname = VALUES(nickname);
