package com.example.api.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "coupon")
public class Coupon {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private String title;
    @Column(name = "total_quantity", nullable = false)
    private int totalQuantity;
    @Column(name = "issued_quantity", nullable = false)
    private int issuedQuantity;

    protected Coupon() {
    }

    public Coupon(String title, int totalQuantity) {
        this.title = title;
        this.totalQuantity = totalQuantity;
        this.issuedQuantity = 0;
    }

    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public int getTotalQuantity() {
        return totalQuantity;
    }

    public int getIssuedQuantity() {
        return issuedQuantity;
    }

    public void issue() {
        this.issuedQuantity++;
    }
}