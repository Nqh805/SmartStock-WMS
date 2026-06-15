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

        String searchKeyword = (keyword != null && !keyword.trim().isEmpty()) ? keyword.trim() : null;

        return importBatchRepository.searchBatches(searchKeyword, pageable);
    }

    public Map<Long, Long> getScannedCountsForBatches(List<ImportBatch> batches) {
        Map<Long, Long> counts = new java.util.HashMap<>();
        if (batches == null || batches.isEmpty()) {
            return counts;
        }
        List<Long> batchIds = batches.stream()
                .map(ImportBatch::getId)
                .collect(java.util.stream.Collectors.toList());

        List<Object[]> results = productItemRepository.countItemsByBatchIds(batchIds);

        for (ImportBatch batch : batches) {
            counts.put(batch.getId(), 0L);
        }

        for (Object[] result : results) {
            Long batchId = (Long) result[0];
            Long count = (Long) result[1];
            counts.put(batchId, count);
        }

        return counts;
    }

    @Transactional
    public void putawayBatch(Long batchId, String locationCode) {
        ImportBatch batch = importBatchRepository.findById(batchId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy lô hàng tương ứng!"));

        WareHouseLocation location = wareHouseLocationRepository.findByLocationCode(locationCode.trim())
                .orElseThrow(() -> new IllegalArgumentException("Mã vị trí kệ không hợp lệ hoặc không tồn tại!"));

        if (!location.getWareHouse().getId().equals(batch.getWareHouse().getId())) {
            throw new IllegalArgumentException("Lỗi vị trí: Kệ này nằm ở Kho '" + location.getWareHouse().getName()
                    + "', trong khi lô hàng này đang ở Kho '" + batch.getWareHouse().getName() + "'!");
        }

        batch.setLocation(location);
        importBatchRepository.save(batch);

        // set số lượng đã cất = số lượng thực nhận
        if (batch.getPurchaseOrder() != null) {
            orderDetailRepository.findByImportBatchId(batch.getId()).ifPresent(detail -> {
                if (batch.getLocation() != null) {
                    detail.setPutawayQuantity(detail.getActualQuantity());
                } else {
                    detail.setPutawayQuantity(0);
                }
                orderDetailRepository.save(detail);
            });
        }
    }

    @Transactional
    public void saveScannedSerials(Long batchId, List<String> serials) {
        ImportBatch batch = importBatchRepository.findById(batchId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy lô hàng này trên hệ thống!"));

        // đếm tổng số lượng đã quét
        long alreadyScannedCount = productItemRepository.countByImportBatchId(batchId);

        int maxAllowed = batch.getMaxAllowed();

        // không cho nạp thêm nếu lớn hơn số lượng hàng nhập về
        if (alreadyScannedCount + serials.size() > maxAllowed) {
            throw new RuntimeException("Lỗi: Tổng số mã Serial nạp vào (" + (alreadyScannedCount + serials.size())
                    + ") vượt quá số lượng thực nhận từ đơn mua hàng gốc (" + maxAllowed + ")!");
        }

        // duyệt qua các mã serial và lưu vào chi tiết sản phẩm
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

        // update lại số lượng đã quét
        // tăng tồn lô theo sốf lượng serial mới nạp
        int currentQuantity = batch.getQuantity() != null ? batch.getQuantity() : 0;
        batch.setQuantity(currentQuantity + serials.size());
        importBatchRepository.save(batch);
    }
}