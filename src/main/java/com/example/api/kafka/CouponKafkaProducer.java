package com.example.api.kafka;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import com.example.api.dto.CouponIssueMessage;

@Component
public class CouponKafkaProducer {

    private static final String TOPIC = "coupon-issue";

    private final KafkaTemplate<String, CouponIssueMessage> kafkaTemplate;

    public CouponKafkaProducer(
            KafkaTemplate<String, CouponIssueMessage> kafkaTemplate
    ) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void send(Long couponId, Long userId) {

        CouponIssueMessage message =
                new CouponIssueMessage(couponId, userId);

        kafkaTemplate.send(
                TOPIC,
                String.valueOf(userId),
                message
        ).whenComplete((result, ex) -> {

            if (ex != null) {
                System.err.println(
                        "Kafka 전송 실패: couponId="
                        + couponId
                        + ", userId="
                        + userId
                );

                ex.printStackTrace();

                return;
            }

            System.out.println(
                    "Kafka 전송 성공: topic="
                    + result.getRecordMetadata().topic()
                    + ", partition="
                    + result.getRecordMetadata().partition()
                    + ", offset="
                    + result.getRecordMetadata().offset()
                    + ", userId="
                    + userId
            );
        });
    }
}