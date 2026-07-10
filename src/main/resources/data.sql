-- Postman 테스트용 데모 User 시드. 평문 비밀번호는 두 계정 모두 "Passport1!" (BCryptPasswordEncoder로 해시함)
INSERT INTO users (email, password, nickname, created_at) VALUES
    ('demo1@passport.ac.kr', '$2a$10$pS3tDnDC1WnJLOqoIR38deEO6Dq8B.saTilGemr62052r83srmmhS', '데모유저1', CURRENT_TIMESTAMP),
    ('demo2@passport.ac.kr', '$2a$10$pS3tDnDC1WnJLOqoIR38deEO6Dq8B.saTilGemr62052r83srmmhS', '데모유저2', CURRENT_TIMESTAMP);
