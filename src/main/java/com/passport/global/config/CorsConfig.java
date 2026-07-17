package com.passport.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * CORS 허용 origin.
 * - localhost:5173/5174 : FE(React+Vite) 로컬 개발 서버
 * - passport-likelion.duckdns.org / 15.164.84.176 : 배포 서버(nginx same-origin이지만 브라우저가 Origin 헤더를 실어 보내므로 명시 허용 필요)
 * allowCredentials(true) 사용 중이라 와일드카드("*")는 불가 → origin을 명시한다.
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/v1/**")
                .allowedOrigins(
                        "http://localhost:5173", "http://localhost:5174",
                        "https://passport-likelion.duckdns.org",
                        "http://passport-likelion.duckdns.org",
                        "http://15.164.84.176"
                )
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("Authorization", "Content-Type")
                .allowCredentials(true);
    }
}
