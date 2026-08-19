package com.example.api.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.api.service.RdbmsCouponService;
import com.example.api.service.RedisCouponService;

@RestController
@RequestMapping("/coupons")
public class CouponController {

    private final RdbmsCouponService rdbmsCouponService;
    private final RedisCouponService redisCouponService;

    public CouponController(
            RdbmsCouponService rdbmsCouponService,
            RedisCouponService redisCouponService
    ) {
        this.rdbmsCouponService = rdbmsCouponService;
        this.redisCouponService = redisCouponService;
    }

    // RDBMS 
    @PostMapping("/{couponId}/issue")
    public ResponseEntity<String> issue(
            @PathVariable Long couponId,
            @RequestParam Long userId
    ) {
        try {
            rdbmsCouponService.issue(couponId, userId);

            return ResponseEntity.ok("쿠폰 발급 성공");

        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest()
                    .body(e.getMessage());

        } catch (Exception e) {
            return ResponseEntity.status(500)
                    .body("서버 오류가 발생했습니다.");
        }
    }

    // Redis 
    @PostMapping("/{couponId}/issue/redis")
    public ResponseEntity<String> issueWithRedis(
            @PathVariable Long couponId,
            @RequestParam Long userId
    ) {
        try {
            redisCouponService.issue(couponId, userId);

            return ResponseEntity.ok("쿠폰 발급 성공");

        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest()
                    .body(e.getMessage());

        } catch (Exception e) {
            return ResponseEntity.status(500)
                    .body("서버 오류가 발생했습니다.");
        }
    }
}