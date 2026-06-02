package com.example.demo.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import com.example.demo.entity.Warehouse.WareHouseLocation;
import java.util.Optional;

@Repository
public interface WareHouseLocationRepository extends JpaRepository<WareHouseLocation, Long> {

        // Tìm vị trí kệ bằng mã Barcode/QR quét được từ súng
        Optional<WareHouseLocation> findByLocationCode(String locationCode);

        // BỔ SUNG HÀM SEARCH CHO TRANG DANH SÁCH
        @Query(value = "SELECT l FROM WareHouseLocation l " +
                        "LEFT JOIN FETCH l.wareHouse w " +
                        "WHERE (:keyword IS NULL OR " +
                        "LOWER(l.locationCode) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
                        "LOWER(w.name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
                        "LOWER(l.shelfName) LIKE LOWER(CONCAT('%', :keyword, '%')))", countQuery = "SELECT COUNT(l) FROM WareHouseLocation l "
                                        +
                                        "LEFT JOIN l.wareHouse w " +
                                        "WHERE (:keyword IS NULL OR " +
                                        "LOWER(l.locationCode) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
                                        "LOWER(w.name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
                                        "LOWER(l.shelfName) LIKE LOWER(CONCAT('%', :keyword, '%')))")
        Page<WareHouseLocation> searchLocations(@Param("keyword") String keyword, Pageable pageable);
}