package com.example.api.kafka;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import com.example.api.dto.CouponIssueMessage;

@Component
public class CouponKafkaProducer {

    private static final Logger log=LoggerFactory.getLogger(CouponKafkaProducer.class);
    private static final String TOPIC="coupon-issue";
    private final KafkaTemplate<String, CouponIssueMessage> kafkaTemplate;

    public CouponKafkaProducer(KafkaTemplate<String, CouponIssueMessage> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void send(Long couponId, Long userId) {
        CouponIssueMessage message =
                new CouponIssueMessage(couponId, userId);
        try {
            kafkaTemplate.send(
                    TOPIC,
                    String.valueOf(userId),
                    message
            ).get(35, TimeUnit.SECONDS);

            log.info(
                    "Kafka 발행 확인: couponId={}, userId={}",
                    couponId,
                    userId
            );

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();

            throw new KafkaPublishException(
                    "Kafka 발행 확인 중 요청 처리가 중단되었습니다.",
                    e
            );

        } catch (ExecutionException | TimeoutException e) {
            log.error(
                    "Kafka 발행 결과 미확인: couponId={}, userId={}",
                    couponId,
                    userId,
                    e
            );

            throw new KafkaPublishException(
                    "Kafka 발행 결과를 확인하지 못했습니다.",
                    e
            );

        } catch (RuntimeException e) {
            log.error(
                    "Kafka 전송 요청 오류: couponId={}, userId={}",
                    couponId,
                    userId,
                    e
            );

            throw new KafkaPublishException(
                    "Kafka 전송 요청을 처리하지 못했습니다.",
                    e
            );
        }
    }
}