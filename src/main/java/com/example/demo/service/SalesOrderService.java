package com.example.demo.service;

import com.example.demo.entity.Order.SalesOrder;
import com.example.demo.entity.Order.OrderDetail;
import com.example.demo.entity.Partner.Customer;
import com.example.demo.entity.Warehouse.WareHouse;
import com.example.demo.entity.Product.Product;
import com.example.demo.repository.SalesOrderRepository;
import com.example.demo.repository.CustomerRepository;
import com.example.demo.repository.ImportBatchRepository;
import com.example.demo.repository.ProductItemRepository;
import com.example.demo.repository.WareHouseRepository;
import com.example.demo.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SalesOrderService {

    private final SalesOrderRepository salesOrderRepository;

    private final CustomerRepository customerRepository;
    private final WareHouseRepository wareHouseRepository;
    private final ProductRepository productRepository;
    private final ImportBatchRepository importBatchRepository;
    private final ProductItemRepository productItemRepository;

    public Page<SalesOrder> getSalesOrder(String keyword, java.time.LocalDateTime startDate,
            java.time.LocalDateTime endDate, int page, int size) {
        Pageable pageable = PageRequest.of(page - 1, size, Sort.by("id").descending());
        // Gọi hàm searchWithFilters thay vì hàm cũ
        return salesOrderRepository.searchWithFilters(keyword, startDate, endDate, pageable);
    }

    public List<Customer> getAllCustomers() {
        return customerRepository.findAll();
    }

    public List<WareHouse> getAllWarehouses() {
        return wareHouseRepository.findAll();
    }

    public List<Product> getAllProducts() {
        return productRepository.findAll();
    }

    public SalesOrder getSalesOrderById(Long id) {
        return salesOrderRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn hàng mã " + id));
    }

    @Transactional
    public void createNewSalesOrder(SalesOrder salesOrder) {
        if (salesOrder.getOrderDetails() == null || salesOrder.getOrderDetails().isEmpty()) {
            throw new IllegalArgumentException("Không thể chốt đơn! Giỏ hàng đang trống rỗng.");
        }

        salesOrder.getOrderDetails()
                .removeIf(detail -> detail.getProduct() == null || detail.getProduct().getId() == null);

        if (salesOrder.getOrderDetails().isEmpty()) {
            throw new IllegalArgumentException("Vui lòng quét chọn ít nhất 1 sản phẩm hợp lệ!");
        }

        if (salesOrder.getCode() == null || salesOrder.getCode().trim().isEmpty()) {
            String timestamp = java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
                    .format(java.time.LocalDateTime.now());
            salesOrder.setCode("SO-" + timestamp);
        }

        salesOrder.setConfirmed(true);
        if (salesOrder.getDeliveryMethod() == SalesOrder.DeliveryMethod.PRE_ORDER) {
            salesOrder.setStatus(SalesOrder.SalesStatus.PENDING);
        } else {
            salesOrder.setStatus(SalesOrder.SalesStatus.COMPLETED);
        }

        java.math.BigDecimal grandTotalSales = java.math.BigDecimal.ZERO;

        // KHỞI TẠO MỘT DANH SÁCH MỚI CHỨA CÁC CHI TIẾT ĐÃ ĐƯỢC "CHẺ" THEO LÔ
        List<OrderDetail> finalOrderDetails = new java.util.ArrayList<>();
        List<OrderDetail> uiDetails = salesOrder.getOrderDetails();

        List<com.example.demo.entity.Product.ProductItem> serialsToUpdate = new java.util.ArrayList<>();

        for (OrderDetail detail : uiDetails) {
            java.math.BigDecimal unitPrice = detail.getUnitPrice() != null ? detail.getUnitPrice()
                    : java.math.BigDecimal.ZERO;
            int requestedQty = detail.getQuantity() != null ? detail.getQuantity() : 0;
            java.math.BigDecimal totalDiscountForLine = detail.getDiscountAmount() != null ? detail.getDiscountAmount()
                    : java.math.BigDecimal.ZERO;

            if (requestedQty <= 0) {
                throw new IllegalArgumentException("Số lượng xuất bán của sản phẩm phải lớn hơn 0!");
            }

            // Tính chiết khấu cho mỗi 1 sản phẩm (để chia đều khi tách lô)
            java.math.BigDecimal discountPerUnit = totalDiscountForLine
                    .divide(java.math.BigDecimal.valueOf(requestedQty), 2, java.math.RoundingMode.HALF_UP);
            java.math.BigDecimal remainingDiscountToAllocate = totalDiscountForLine;

            // --- THUẬT TOÁN CHIA LÔ THEO FIFO: VỪA CHIA LÔ VỪA KIỂM TRA TỒN KHO & TRỪ TỒN
            // ---
            int remainingQuantityToFulfill = requestedQty;

            // Lấy danh sách các lô hàng của sản phẩm này (Còn hàng & Lô cũ ưu tiên trước)
            List<com.example.demo.entity.Product.ImportBatch> availableBatches = importBatchRepository
                    .findByProductIdAndQuantityAvailableGreaterThanOrderByImportDateAscIdAsc(
                            detail.getProduct().getId(), 0);

            for (com.example.demo.entity.Product.ImportBatch batch : availableBatches) {
                if (remainingQuantityToFulfill <= 0)
                    break; // Đã lấy đủ hàng thì dừng vòng lặp

                int batchAvailable = batch.getQuantityAvailable() != null ? batch.getQuantityAvailable() : 0;
                if (batchAvailable <= 0)
                    continue;

                // Xác định số lượng sẽ lấy từ lô này
                int quantityTakenFromBatch = Math.min(batchAvailable, remainingQuantityToFulfill);

                // CẬP NHẬT 2: Trừ tồn kho thông minh
                // Luôn luôn trừ số lượng Khả dụng (Khóa hàng)
                batch.setQuantityAvailable(batchAvailable - quantityTakenFromBatch);

                // Chỉ trừ số lượng Vật lý (OnHand) nếu KHÁCH LẤY HÀNG LUÔN
                if (salesOrder.getDeliveryMethod() != SalesOrder.DeliveryMethod.PRE_ORDER) {
                    batch.setQuantityOnHand(batch.getQuantityOnHand() - quantityTakenFromBatch);
                }

                importBatchRepository.save(batch);

                // lấy ngẫu nhiên serial có status là đã bán
                List<com.example.demo.entity.Product.ProductItem> itemsInBatch = productItemRepository
                        .findByImportBatchIdAndStatus(batch.getId(),
                                com.example.demo.entity.Product.ItemStatus.IN_STOCK);

                // Chốt chặn an toàn: Đảm bảo số lượng Serial thực tế khớp với số tồn báo cáo
                // của Lô
                if (itemsInBatch.size() < quantityTakenFromBatch) {
                    throw new IllegalStateException("Lỗi sai lệch dữ liệu: Lô hàng " + batch.getBatchCode()
                            + " báo còn " + batchAvailable + " cái nhưng số lượng mã Serial thực tế trong kho chỉ có "
                            + itemsInBatch.size() + " cái!");
                }

                // Cắt lấy đúng số lượng Serial cần thiết (Từ 0 đến quantityTakenFromBatch)
                List<com.example.demo.entity.Product.ProductItem> pickedSerials = itemsInBatch.subList(0,
                        quantityTakenFromBatch);

                for (com.example.demo.entity.Product.ProductItem serialItem : pickedSerials) {
                    serialItem.setStatus(com.example.demo.entity.Product.ItemStatus.SOLD);
                    // Bỏ tạm vào rổ, lát Đơn hàng lưu xong có ID rồi mình mới móc nối vào nhau
                    serialsToUpdate.add(serialItem);
                }

                // --- TẠO RA MỘT ORDER DETAIL MỚI CHO PHẦN ĐÃ LẤY TỪ LÔ NÀY ---
                OrderDetail splitDetail = new OrderDetail();
                splitDetail.setProduct(detail.getProduct());
                splitDetail.setUnitPrice(unitPrice);
                splitDetail.setQuantity(quantityTakenFromBatch);

                // Giảm số lượng cần lấy
                remainingQuantityToFulfill -= quantityTakenFromBatch;

                // Phân bổ chiết khấu (Nếu là lượt vét cuối cùng, nhồi hết số dư chiết khấu vào
                // để không lệch 1 đồng nào)
                java.math.BigDecimal splitDiscount;
                if (remainingQuantityToFulfill == 0) {
                    splitDiscount = remainingDiscountToAllocate;
                } else {
                    splitDiscount = discountPerUnit.multiply(java.math.BigDecimal.valueOf(quantityTakenFromBatch));
                }
                splitDetail.setDiscountAmount(splitDiscount);
                remainingDiscountToAllocate = remainingDiscountToAllocate.subtract(splitDiscount);

                // Ghi nhận nguồn gốc Lô hàng cho dòng này
                splitDetail.setImportBatch(batch);
                splitDetail.setOrderHeader(salesOrder);
                splitDetail.setActualQuantity(0);
                splitDetail.setPutawayQuantity(0);

                // Tính toán thành tiền của dòng chia nhỏ này
                java.math.BigDecimal lineSubtotal = unitPrice
                        .multiply(java.math.BigDecimal.valueOf(quantityTakenFromBatch));
                java.math.BigDecimal lineTotal = lineSubtotal.subtract(splitDiscount);
                if (lineTotal.compareTo(java.math.BigDecimal.ZERO) < 0) {
                    lineTotal = java.math.BigDecimal.ZERO;
                }

                grandTotalSales = grandTotalSales.add(lineTotal);

                // Đưa vào danh sách cuối cùng để lưu
                finalOrderDetails.add(splitDetail);
            }

            // bán vượt
            if (remainingQuantityToFulfill > 0) {
                throw new IllegalArgumentException("Lỗi tồn kho: Sản phẩm '"
                        + detail.getProduct().getName()
                        + "' không đủ hàng trong kho! Còn thiếu " + remainingQuantityToFulfill + " cái.");
            }
        }

        // Ghi đè lại bằng danh sách đã được chẻ nhỏ theo lô
        salesOrder.setOrderDetails(finalOrderDetails);
        salesOrder.setTotalSalesAmount(grandTotalSales);

        // Xử lý thanh toán
        java.math.BigDecimal paidAmt = salesOrder.getPaidAmount() != null ? salesOrder.getPaidAmount()
                : java.math.BigDecimal.ZERO;
        if (paidAmt.compareTo(grandTotalSales) > 0) {
            salesOrder.setPaidAmount(grandTotalSales);
            paidAmt = grandTotalSales;
        }

        if (paidAmt.compareTo(grandTotalSales) >= 0 && grandTotalSales.compareTo(java.math.BigDecimal.ZERO) > 0) {
            salesOrder.setPaymentStatus(com.example.demo.entity.Order.PaymentStatus.PAID);
        } else if (paidAmt.compareTo(java.math.BigDecimal.ZERO) > 0) {
            salesOrder.setPaymentStatus(com.example.demo.entity.Order.PaymentStatus.PARTIAL);
        } else {
            salesOrder.setPaymentStatus(com.example.demo.entity.Order.PaymentStatus.UNPAID);
        }

        salesOrderRepository.save(salesOrder);

        for (com.example.demo.entity.Product.ProductItem item : serialsToUpdate) {
            item.setSalesOrder(salesOrder);
        }
        productItemRepository.saveAll(serialsToUpdate);
    }

    @Transactional
    public void processPayment(Long orderId, java.math.BigDecimal amountToPay) {
        // 1. Tìm đơn hàng
        SalesOrder order = salesOrderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn hàng mã " + orderId));

        // 2. Kiểm tra nếu đơn đã thanh toán đủ thì không cho đóng tiền thêm
        if (order.getPaymentStatus() == com.example.demo.entity.Order.PaymentStatus.PAID) {
            throw new IllegalStateException("Đơn hàng này đã được thanh toán đầy đủ trước đó!");
        }

        // 3. Cộng dồn số tiền khách vừa đưa vào tổng tiền đã trả trước đó
        java.math.BigDecimal currentPaid = order.getPaidAmount() != null ? order.getPaidAmount()
                : java.math.BigDecimal.ZERO;
        java.math.BigDecimal newPaidAmount = currentPaid.add(amountToPay);

        // 4. Lấy tổng tiền của đơn hàng
        java.math.BigDecimal totalAmount = order.getTotalSalesAmount() != null ? order.getTotalSalesAmount()
                : java.math.BigDecimal.ZERO;

        // 5. Cập nhật số tiền đã trả (Nếu khách đưa dư tiền, chỉ ghi nhận tối đa bằng
        // giá trị đơn hàng)
        if (newPaidAmount.compareTo(totalAmount) > 0) {
            order.setPaidAmount(totalAmount);
            // Ở bước này thực tế sẽ sinh ra biến "Tiền thối lại (Change)" cho khách
        } else {
            order.setPaidAmount(newPaidAmount);
        }

        // 6. Đánh giá và cập nhật lại trạng thái thanh toán
        if (order.getPaidAmount().compareTo(totalAmount) >= 0) {
            order.setPaymentStatus(com.example.demo.entity.Order.PaymentStatus.PAID);
        } else if (order.getPaidAmount().compareTo(java.math.BigDecimal.ZERO) > 0) {
            order.setPaymentStatus(com.example.demo.entity.Order.PaymentStatus.PARTIAL);
        } else {
            order.setPaymentStatus(com.example.demo.entity.Order.PaymentStatus.UNPAID);
        }

        salesOrderRepository.save(order);
    }

    public void createSalesOrder(SalesOrder salesOrder) {
        throw new UnsupportedOperationException("Unimplemented method 'createSalesOrder'");
    }
}