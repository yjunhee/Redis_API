package com.example.api.repository;

import java.util.List;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

@Repository
public class RedisCouponRepository {

    private final StringRedisTemplate redisTemplate;

    private final DefaultRedisScript<Long> issueScript;

    public RedisCouponRepository(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;

        String script = """
                -- 중복체크 및 무결성 검사
                local alreadyIssued = redis.call('SISMEMBER', KEYS[2], ARGV[1])
                if alreadyIssued == 1 then
                    return -1
                end

                -- 현재 재고 조회
                local stock = tonumber(redis.call('GET', KEYS[1]))

                if stock == nil or stock <= 0 then
                    return 0
                end

                -- 재고 차감
                redis.call('DECR', KEYS[1])

                -- 발급 사용자 등록
                redis.call('SADD', KEYS[2], ARGV[1])

                return 1
                """;

        this.issueScript = new DefaultRedisScript<>(script, Long.class);
    }

    public long issue(Long couponId, Long userId) {

        String stockKey = "coupon:" + couponId + ":stock";
        String userKey = "coupon:" + couponId + ":users";

        Long result = redisTemplate.execute(
                issueScript,
                List.of(stockKey, userKey),
                String.valueOf(userId)
        );

        if (result == null) {
            throw new IllegalStateException("쿠폰 발급 실패했습니다.");
        }

        return result;
    }
}