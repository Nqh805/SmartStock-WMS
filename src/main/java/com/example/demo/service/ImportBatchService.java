package com.example.demo.service;

import com.example.demo.entity.Product.ImportBatch;
import com.example.demo.entity.Product.ItemStatus;
import com.example.demo.entity.Product.ProductItem;
import com.example.demo.entity.Warehouse.WareHouseLocation;
import com.example.demo.repository.ImportBatchRepository;
import com.example.demo.repository.OrderDetailRepository;
import com.example.demo.repository.ProductItemRepository;
import com.example.demo.repository.WareHouseLocationRepository;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ImportBatchService {

    private final ImportBatchRepository importBatchRepository;
    private final ProductItemRepository productItemRepository;
    private final WareHouseLocationRepository wareHouseLocationRepository;
    private final OrderDetailRepository orderDetailRepository;

    public Page<ImportBatch> getBatchesWithPagination(String keyword, int pageNo, int pageSize) {
        Sort sort = Sort.by("importDate").descending().and(Sort.by("id").descending());
        Pageable pageable = PageRequest.of(pageNo - 1, pageSize, sort);

        // Chuẩn hóa keyword
        String searchKeyword = (keyword != null && !keyword.trim().isEmpty()) ? keyword.trim() : null;

        return importBatchRepository.searchBatches(searchKeyword, pageable);
    }

    public Map<Long, Long> getScannedCountsForBatches(List<ImportBatch> batches) {
        Map<Long, Long> counts = new java.util.HashMap<>();
        for (ImportBatch batch : batches) {
            long count = productItemRepository.countByImportBatchId(batch.getId());
            counts.put(batch.getId(), count);
        }
        return counts;
    }

    // putaway
    @Transactional
    public void putawayBatch(Long batchId, String locationCode) {
        // 1. Tìm lô hàng cần xếp
        ImportBatch batch = importBatchRepository.findById(batchId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy lô hàng tương ứng!"));

        // 2. Tìm vị trí kệ từ mã quét được
        WareHouseLocation location = wareHouseLocationRepository.findByLocationCode(locationCode.trim())
                .orElseThrow(() -> new IllegalArgumentException("Mã vị trí kệ không hợp lệ hoặc không tồn tại!"));

        // 3. VALIDATE: Kệ quét được phải thuộc về đúng Kho mà lô hàng đang đứng
        if (!location.getWareHouse().getId().equals(batch.getWareHouse().getId())) {
            throw new IllegalArgumentException("Lỗi vị trí: Kệ này nằm ở Kho '" + location.getWareHouse().getName()
                    + "', trong khi lô hàng này đang ở Kho '" + batch.getWareHouse().getName() + "'!");
        }

        // 4. Cập nhật vị trí kệ cho lô hàng
        batch.setLocation(location);
        importBatchRepository.save(batch);

        if (batch.getPurchaseOrder() != null) {
            orderDetailRepository.findByImportBatchId(batch.getId()).ifPresent(detail -> {
                if (batch.getLocation() != null) {
                    // Nếu lô hàng đã có vị trí -> Cập nhật số lượng đã cất = số lượng thực nhận
                    detail.setPutawayQuantity(detail.getActualQuantity());
                } else {
                    // Nếu rút lô hàng khỏi kệ (Location = null) -> Trả số lượng đã cất về 0
                    detail.setPutawayQuantity(0);
                }
                orderDetailRepository.save(detail); // Lưu lại vào DB
            });
        }
    }

    @Transactional
    public void saveScannedSerials(Long batchId, List<String> serials) {
        ImportBatch batch = importBatchRepository.findById(batchId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy lô hàng này trên hệ thống!"));

        long alreadyScannedCount = productItemRepository.countByImportBatchId(batchId);

        if (alreadyScannedCount + serials.size() > batch.getQuantityOnHand()) {
            throw new RuntimeException("Lỗi: Tổng số mã Serial nạp vào vượt quá số lượng tồn kho của lô hàng!");
        }

        List<ProductItem> itemsToSave = new ArrayList<>();

        for (String serial : serials) {
            if (productItemRepository.existsBySerialNumber(serial)) {
                throw new RuntimeException("Mã Serial/IMEI '" + serial + "' đã tồn tại trên hệ thống!");
            }
            ProductItem item = new ProductItem();
            item.setSerialNumber(serial);
            item.setImportBatch(batch);
            item.setProduct(batch.getProduct());
            item.setStatus(ItemStatus.IN_STOCK);
            itemsToSave.add(item);
        }
        productItemRepository.saveAll(itemsToSave);
    }
}