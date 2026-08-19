package com.example.api.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.api.domain.Coupon;
import com.example.api.domain.CouponIssue;
import com.example.api.repository.CouponIssueRepository;
import com.example.api.repository.CouponRepository;

@Service
public class RdbmsCouponService {

    private final CouponRepository couponRepository;
    private final CouponIssueRepository couponIssueRepository;

    public RdbmsCouponService(CouponRepository couponRepository, CouponIssueRepository couponIssueRepository) {
        this.couponRepository = couponRepository;
        this.couponIssueRepository = couponIssueRepository;
    }

    @Transactional
    public void issue(Long couponId, Long userId) {
        // 중복 확인
        Coupon coupon = couponRepository.findByIdWithLock(couponId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 쿠폰입니다."));
        if (couponIssueRepository.existsByCouponIdAndUserId(couponId, userId)) {
            throw new IllegalArgumentException("이미 발급받은 유저입니다.");
        }
        // 현재 발급 수량 조회
        long currentCount = couponIssueRepository.countByCouponId(couponId);

        // 검증
        if (currentCount >= coupon.getTotalQuantity()) {
            throw new IllegalStateException("선착순 쿠폰이 마감되었습니다.");
        }

        // 발급 처리 및 저장
        coupon.issue();
        couponIssueRepository.save(new CouponIssue(couponId, userId));
    }
}