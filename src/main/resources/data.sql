-- Postman 테스트용 데모 User 시드. 평문 비밀번호는 두 계정 모두 "Passport1!" (BCryptPasswordEncoder로 해시함)
-- ⚠️ spring.sql.init.mode=always 라 매 기동 시 실행됨. DB 파일 모드(영속)에서 재기동 시 이메일 unique 충돌을
--    피하기 위해 INSERT 대신 MERGE(있으면 갱신, 없으면 삽입)를 사용한다. H2 문법.
MERGE INTO users (email, password, nickname, created_at) KEY(email) VALUES
    ('demo1@passport.ac.kr', '$2a$10$pS3tDnDC1WnJLOqoIR38deEO6Dq8B.saTilGemr62052r83srmmhS', '데모유저1', CURRENT_TIMESTAMP),
    ('demo2@passport.ac.kr', '$2a$10$pS3tDnDC1WnJLOqoIR38deEO6Dq8B.saTilGemr62052r83srmmhS', '데모유저2', CURRENT_TIMESTAMP);
