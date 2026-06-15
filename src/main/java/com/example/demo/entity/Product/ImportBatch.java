package com.example.demo.entity.Product;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDate;

import com.example.demo.entity.Order.PurchaseOrder;
import com.example.demo.entity.Warehouse.WareHouse;
import com.example.demo.entity.Warehouse.WareHouseLocation;

@Entity
@Table(name = "import_batch")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ImportBatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "batch_code", unique = true)
    private String batchCode;

    @Column(name = "import_date")
    private LocalDate importDate;

    @Column(name = "price")
    private BigDecimal price;

    @Column(name = "quantity")
    private Integer quantity;

    @Column(name = "note")
    private String note;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "purchase_order_id")
    private PurchaseOrder purchaseOrder;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ware_house_id")
    private WareHouse wareHouse;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "location_id")
    private WareHouseLocation location;

    @Transient
    public Integer getMaxAllowed() { // là Actual Quantity đã nhập của sản phẩm này trong đơn hàng
        if (this.purchaseOrder != null && this.purchaseOrder.getOrderDetails() != null
                && this.product != null) {

            return this.purchaseOrder.getOrderDetails().stream()
                    .filter(detail -> detail.getProduct() != null
                            && detail.getProduct().getId().equals(this.product.getId()))
                    .mapToInt(detail -> detail.getActualQuantity() != null ? detail.getActualQuantity() : 0)
                    .sum();
        }

        return 0;
    }
}