package com.example.demo.dto;

import lombok.Data;
import java.math.BigDecimal;
import com.example.demo.entity.Order.OrderDetail;

@Data
public class OrderDetailDTO {
    private Long id;
    private BigDecimal price;
    private Integer quantity;
    private Integer actualQuantity;
    private Integer putawayQuantity;
    private BigDecimal discount;
    private String warehouse;
    private ProductDTO product;
    private Integer warrantyMonths;

    public OrderDetailDTO(OrderDetail detail) {
        this.id = detail.getId();
        this.price = detail.getUnitPrice();
        this.quantity = detail.getQuantity();
        this.putawayQuantity = detail.getPutawayQuantity() != null ? detail.getPutawayQuantity() : 0;
        this.actualQuantity = detail.getActualQuantity() != null ? detail.getActualQuantity() : 0;
        this.discount = detail.getDiscountAmount() != null ? detail.getDiscountAmount() : BigDecimal.ZERO;

        // Tự động map đối tượng con
        if (detail.getProduct() != null) {
            this.product = new ProductDTO(detail.getProduct());
        } else {
            this.product = null;
        }

        if (detail.getOrderHeader() != null && detail.getOrderHeader().getWareHouse() != null) {
            this.warehouse = detail.getOrderHeader().getWareHouse().getName();
        } else {
            this.warehouse = "N/A";
        }

        if (detail.getProduct() != null && detail.getProduct().getWarrantyMonths() != null) {
            this.warrantyMonths = detail.getProduct().getWarrantyMonths();
        } else {
            this.warrantyMonths = 0;
        }
    }
}