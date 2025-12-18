package com.sarinah.product_bundling.configuration;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Configuration
@EnableCaching
public class CacheConfig {
    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager("all-specs");
        cacheManager.setCaffeine(Caffeine.newBuilder()
                .maximumSize(100)           // Max 100 entries
                .expireAfterWrite(2, TimeUnit.MINUTES)  // TTL 2 menit
                .recordStats());            // Enable stats (optional)

        return cacheManager;
    }
}
