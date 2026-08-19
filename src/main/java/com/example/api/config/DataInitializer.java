package com.example.api.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.example.api.domain.Coupon;
import com.example.api.repository.CouponRepository;

@Component
public class DataInitializer implements CommandLineRunner {

    private final CouponRepository couponRepository;
    private final StringRedisTemplate redisTemplate;

    public DataInitializer(CouponRepository couponRepository, StringRedisTemplate redisTemplate) {
        this.couponRepository = couponRepository;
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void run(String... args) throws Exception {
        Coupon coupon;
        if (couponRepository.count() == 0) {
            coupon = couponRepository.save(new Coupon("선착순 쿠폰", 500));
            System.out.println("쿠폰 생성(ID: " + coupon.getId());
        } else {
            coupon = couponRepository.findById(1L).orElse(null);
        }

        if (coupon != null) {
            String stockKey = "coupon:" + coupon.getId() + ":stock";
            String usersKey = "coupon:" + coupon.getId() + ":users";

            redisTemplate.opsForValue().set(stockKey, String.valueOf(coupon.getTotalQuantity()));
            redisTemplate.delete(usersKey);

            System.out.println("Redis 세팅: " + stockKey + " = " + coupon.getTotalQuantity());
        }
    }
}