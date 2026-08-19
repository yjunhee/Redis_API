package com.example.api.dto;

public class CouponIssueMessage {

    private Long couponId;
    private Long userId;

    public CouponIssueMessage() {
    }

    public CouponIssueMessage(Long couponId, Long userId) {
        this.couponId = couponId;
        this.userId = userId;
    }

    public Long getCouponId() {
        return couponId;
    }

    public void setCouponId(Long couponId) {
        this.couponId = couponId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }
}