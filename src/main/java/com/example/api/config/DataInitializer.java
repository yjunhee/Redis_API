package com.example.api.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.example.api.domain.Coupon;
import com.example.api.repository.CouponIssueRepository;
import com.example.api.repository.CouponRepository;

@Component
public class DataInitializer implements CommandLineRunner {

    private final CouponRepository couponRepository;
    private final CouponIssueRepository couponIssueRepository;
    private final StringRedisTemplate redisTemplate;

    public DataInitializer(
            CouponRepository couponRepository,
            CouponIssueRepository couponIssueRepository,
            StringRedisTemplate redisTemplate) {

        this.couponRepository = couponRepository;
        this.couponIssueRepository = couponIssueRepository;
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void run(String... args) {
        // 1. 기존 발급 데이터 전체 삭제
        couponIssueRepository.deleteAll();
        // 2. 쿠폰 확인 / 생성
        Coupon coupon = couponRepository.findById(1L)
                .orElseGet(() ->
                        couponRepository.save(
                                new Coupon("선착순 쿠폰", 500)
                        )
                );

        // 3. Redis 키 생성
        String stockKey = "coupon:" + coupon.getId() + ":stock";
        String usersKey = "coupon:" + coupon.getId() + ":users";

        // 4. Redis 기존 데이터 삭제
        redisTemplate.delete(stockKey);
        redisTemplate.delete(usersKey);

        // 5. Redis 재고 500개로 초기화
        redisTemplate.opsForValue()
                .set(stockKey, String.valueOf(coupon.getTotalQuantity()));
    }
}