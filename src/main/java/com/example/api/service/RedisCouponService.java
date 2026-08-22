package com.example.api.service;

import org.springframework.stereotype.Service;

import com.example.api.kafka.CouponKafkaProducer;
import com.example.api.repository.RedisCouponRepository;

@Service
public class RedisCouponService {

    private final RedisCouponRepository redisCouponRepository;
    private final CouponKafkaProducer couponKafkaProducer;

    public RedisCouponService(
            RedisCouponRepository redisCouponRepository,
            CouponKafkaProducer couponKafkaProducer
    ) {
        this.redisCouponRepository = redisCouponRepository;
        this.couponKafkaProducer = couponKafkaProducer;
    }

    public void issue(Long couponId, Long userId) {

        long result = redisCouponRepository.issue(couponId, userId);
        
        if (result == -1) {
            throw new IllegalArgumentException(
                    "이미 발급받은 유저입니다."
            );
        }
        if (result == 0) {
            throw new IllegalStateException(
                    "선착순 쿠폰이 마감되었습니다."
            );
        }
        if (result == 1) {
            couponKafkaProducer.send(couponId, userId);

            return;
        }

        throw new IllegalStateException(
                "오류"
        );
    }
}