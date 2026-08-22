package com.example.kafka;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.example.api.domain.CouponIssue;
import com.example.api.dto.CouponIssueMessage;
import com.example.api.repository.CouponIssueRepository;

@Component
public class CouponKafkaConsumer {

    private final CouponIssueRepository couponIssueRepository;

    public CouponKafkaConsumer(
            CouponIssueRepository couponIssueRepository
    ) {
        this.couponIssueRepository = couponIssueRepository;
    }

    @KafkaListener(
        topics = "coupon-issue",
        groupId = "coupon-consumer-group"
    )
    public void consume(CouponIssueMessage message) {

        System.out.println(
            "Kafka 메시지 수신"
        );

        couponIssueRepository.save(
            new CouponIssue(
                message.getCouponId(),
                message.getUserId()
            )
        );

        System.out.println("MySQL 저장 완료");
    }
}