package com.example.api.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.example.api.domain.CouponIssue;

public interface CouponIssueRepository extends JpaRepository<CouponIssue, Long> {
    // 중복검사
    boolean existsByCouponIdAndUserId(Long couponId, Long userId);

    // 잔여쿠폰 개수 세기
    long countByCouponId(Long couponId);
}