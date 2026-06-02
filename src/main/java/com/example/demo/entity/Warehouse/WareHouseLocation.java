package com.example.demo.entity.Warehouse;

import org.hibernate.annotations.Formula;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Entity
@Table(name = "warehouse_location")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WareHouseLocation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ware_house_id", nullable = false)
    private WareHouse wareHouse;

    @Column(name = "shelf_name", nullable = false)
    private String shelfName;

    @Column(name = "tier_name", nullable = false)
    private String tierName;

    @Column(name = "bin_name", nullable = false)
    private String binName;

    @Column(name = "location_code", unique = true, nullable = false)
    private String locationCode; // Mã vạch dán trên kệ để quét (VD: K01-T02-O05)

    @Formula("(SELECT COUNT(*) FROM import_batch b WHERE b.location_id = id)")
    private Integer batchCount;
}