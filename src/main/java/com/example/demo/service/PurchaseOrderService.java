package com.example.demo.service;

import com.example.demo.dto.ReceiptFormDTO;
import com.example.demo.entity.Order.DeliveryResult;
import com.example.demo.entity.Order.OrderDetail;
import com.example.demo.entity.Order.PaymentStatus;
import com.example.demo.entity.Order.PurchaseOrder;
import com.example.demo.entity.Product.ImportBatch;
import com.example.demo.entity.Product.Product;
import com.example.demo.entity.User.Employee;
import com.example.demo.repository.EmployeeRepository;
import com.example.demo.repository.ImportBatchRepository;
import com.example.demo.repository.OrderDetailRepository;
import com.example.demo.repository.ProductRepository;
import com.example.demo.repository.PurchaseOrderRepository;
import com.example.demo.repository.SupplierRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PurchaseOrderService {

    // --- CONSTANTS ---
    private static final int MAX_RELIABILITY_SCORE = 100;
    private static final int INITIAL_LATE_COUNT = 0;
    private static final int INITIAL_FAILED_COUNT = 0;

    private static final int SCORE_BONUS_ON_TIME = 5;
    private static final int SCORE_BONUS_EARLY = 15;
    private static final int SCORE_PENALTY_LATE = 10;
    private static final int SCORE_PENALTY_FAILED = 15;

    private static final String PROJECT_DOC_PATH = "src/main/resources/static/uploads/documents/";
    private static final String BUILD_DOC_PATH = "target/classes/static/uploads/documents/";
    private static final String WEB_DOC_PATH = "/uploads/documents/";

    private static final DateTimeFormatter DATE_PART_FORMATTER = DateTimeFormatter.ofPattern("yyMMdd");
    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final DateTimeFormatter DISPLAY_DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    // --- REPOSITORIES ---
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final OrderDetailRepository orderDetailRepository;
    private final ImportBatchRepository importBatchRepository;
    private final ProductRepository productRepository;
    private final SupplierRepository supplierRepository;
    private final EmployeeRepository employeeRepository;

    public PurchaseOrder getPurchaseOrderById(Long id) {
        return purchaseOrderRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn nhập hàng mã " + id));
    }

    // Xử lý nhận hàng và tạo lô nhập tương ứng
    @Transactional
    public void receiveOrderAndGenerateBatches(Long poId, ReceiptFormDTO formDTO, MultipartFile deliveryDocumentFile) {
        PurchaseOrder po = purchaseOrderRepository.findById(poId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn hàng mã: " + poId));
        List<OrderDetail> details = orderDetailRepository.findByOrderHeaderId(poId);

        validateReceiptForm(po, formDTO, details);

        LocalDate actualArrival = formDTO.getActualArrival();
        po.setActualArrival(actualArrival);

        updateSupplierReliability(po, actualArrival);

        BigDecimal newTotalAmount = processOrderDetailsAndGenerateBatches(po, details, formDTO);

        if (deliveryDocumentFile != null && !deliveryDocumentFile.isEmpty()) {
            String documentUrl = saveDeliveryDocument(deliveryDocumentFile, po.getCode());
            po.setDeliveryDocument(documentUrl);
        }

        po.setTotalPurchaseAmount(newTotalAmount);
        po.setStatus(PurchaseOrder.PurchaseStatus.COMPLETED);

        orderDetailRepository.saveAll(details);
        purchaseOrderRepository.save(po);
    }

    // Tạo đơn hàng mới và lưu chi tiết đơn hàng
    @Transactional
    public void createNewPurchaseOrder(PurchaseOrder po) {
        List<OrderDetail> orderDetails = po.getOrderDetails();

        validateNewOrder(po, orderDetails);
        ensureOrderCode(po);
        setDefaultOrderFields(po);

        BigDecimal totalOrderAmount = calculateTotalOrderAmount(po, orderDetails);
        po.setTotalPurchaseAmount(totalOrderAmount);

        po.setEmployee(fetchCurrentEmployee());

        validateFinalProductStatuses(orderDetails);

        purchaseOrderRepository.save(po);
        orderDetailRepository.saveAll(orderDetails);
    }

    // duyệt đơn hàng (sang vận chuyển)
    @Transactional
    public void approveOrder(Long id) {
        PurchaseOrder po = purchaseOrderRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy đơn hàng"));

        if (po.getStatus() != PurchaseOrder.PurchaseStatus.PENDING) {
            throw new RuntimeException("Đơn hàng này không ở trạng thái chờ duyệt!");
        }

        // chuyển trạng thái
        po.setStatus(PurchaseOrder.PurchaseStatus.IN_TRANSIT);
        purchaseOrderRepository.save(po);
    }

    // báo cáo giao hàng thất bại (hủy đơn và trừ điểm uy tín nhà cung cấp)
    @Transactional
    public void reportFailedDelivery(Long poId, String failReason) {
        PurchaseOrder po = purchaseOrderRepository.findById(poId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy đơn hàng"));

        po.setStatus(PurchaseOrder.PurchaseStatus.CANCELLED);
        po.setReceivedNote(failReason);

        com.example.demo.entity.Partner.Supplier supplier = po.getSupplier();
        if (supplier != null) {
            initializeSupplierMetrics(supplier);
            supplier.setReliabilityScore(supplier.getReliabilityScore() - SCORE_PENALTY_FAILED);
            supplier.setFailedDeliveryCount(supplier.getFailedDeliveryCount() + 1);
            supplierRepository.save(supplier);
        }

        purchaseOrderRepository.save(po);
    }

    // hủy đơn hàng đang chờ xử lý
    @Transactional
    public void deletePurchaseOrder(Long id) {
        PurchaseOrder po = purchaseOrderRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn hàng này!"));

        if (po.getStatus() != PurchaseOrder.PurchaseStatus.PENDING) {
            throw new IllegalStateException("Chỉ được phép xóa đơn hàng đang chờ xử lý!");
        }
        purchaseOrderRepository.delete(po);
    }

    // từ chối đơn hàng đang chờ xử lý (chuyển trạng thái thành HỦY)
    @Transactional
    public void rejectPurchaseOrder(Long id) {
        PurchaseOrder po = purchaseOrderRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn hàng này!"));

        if (po.getStatus() != PurchaseOrder.PurchaseStatus.PENDING) {
            throw new IllegalStateException("Chỉ được phép từ chối đơn hàng đang chờ xử lý!");
        }
        po.setStatus(PurchaseOrder.PurchaseStatus.CANCELLED);
        purchaseOrderRepository.save(po);
    }

    // --- PRIVATE HELPER METHODS ---

    // Validate dữ liệu kiểm kê nhận hàng
    private void validateReceiptForm(PurchaseOrder po, ReceiptFormDTO formDTO, List<OrderDetail> details) {
        if (formDTO.getActualQuantities() == null || formDTO.getActualQuantities().isEmpty()) {
            throw new IllegalArgumentException(
                    "Lỗi nghiệp vụ: Dữ liệu kiểm đếm trống! Vui lòng kiểm tra lại danh sách sản phẩm.");
        }

        if (formDTO.getActualArrival() != null && po.getCreatedAt() != null) {
            LocalDate dateCreated = po.getCreatedAt().toLocalDate();
            if (formDTO.getActualArrival().isBefore(dateCreated)) {
                throw new IllegalArgumentException(
                        "Lỗi nghiệp vụ: Ngày tới thực tế không được nhỏ hơn ngày tạo đơn hàng (" +
                                dateCreated.format(DISPLAY_DATE_FORMATTER) + ")");
            }
        }

        int totalActualReceived = details.stream()
                .mapToInt(detail -> formDTO.getActualQuantities().getOrDefault(detail.getId(), 0))
                .sum();
        if (totalActualReceived <= 0) {
            throw new IllegalArgumentException(
                    "Lỗi nghiệp vụ: Tổng số lượng thực nhận của toàn bộ đơn hàng phải lớn hơn 0!");
        }
    }

    // Cập nhật điểm uy tín của nhà cung cấp dựa trên kết quả giao hàng
    private void updateSupplierReliability(PurchaseOrder po, LocalDate actualArrival) {
        if (po.getExpectedArrival() == null || actualArrival == null) {
            return;
        }

        com.example.demo.entity.Partner.Supplier supplier = po.getSupplier();
        initializeSupplierMetrics(supplier);

        if (actualArrival.isEqual(po.getExpectedArrival())) {
            po.setDeliveryResult(DeliveryResult.ON_TIME);
            supplier.setReliabilityScore(supplier.getReliabilityScore() + SCORE_BONUS_ON_TIME);
        } else if (actualArrival.isBefore(po.getExpectedArrival())) {
            po.setDeliveryResult(DeliveryResult.EARLY);
            supplier.setReliabilityScore(supplier.getReliabilityScore() + SCORE_BONUS_EARLY);
        } else {
            po.setDeliveryResult(DeliveryResult.LATE);
            supplier.setReliabilityScore(supplier.getReliabilityScore() - SCORE_PENALTY_LATE);
            supplier.setLateDeliveryCount(supplier.getLateDeliveryCount() + 1);
        }

        if (supplier.getReliabilityScore() > MAX_RELIABILITY_SCORE) {
            supplier.setReliabilityScore(MAX_RELIABILITY_SCORE);
        }

        supplierRepository.save(supplier);
    }

    // Khởi tạo các chỉ số đánh giá ncc nếu chưa có
    private void initializeSupplierMetrics(com.example.demo.entity.Partner.Supplier supplier) {
        if (supplier.getReliabilityScore() == null) {
            supplier.setReliabilityScore(MAX_RELIABILITY_SCORE);
        }
        if (supplier.getLateDeliveryCount() == null) {
            supplier.setLateDeliveryCount(INITIAL_LATE_COUNT);
        }
        if (supplier.getFailedDeliveryCount() == null) {
            supplier.setFailedDeliveryCount(INITIAL_FAILED_COUNT);
        }
    }

    // Xử lý chi tiết đơn hàng, sinh lô nhập
    private BigDecimal processOrderDetailsAndGenerateBatches(PurchaseOrder po, List<OrderDetail> details,
            ReceiptFormDTO formDTO) {
        BigDecimal newTotalAmount = BigDecimal.ZERO;
        String datePart = LocalDate.now().format(DATE_PART_FORMATTER);

        for (OrderDetail detail : details) {
            Integer actualQty = formDTO.getActualQuantities().getOrDefault(detail.getId(), 0);
            detail.setActualQuantity(actualQty);

            BigDecimal discount = detail.getDiscountAmount() != null ? detail.getDiscountAmount() : BigDecimal.ZERO;
            BigDecimal lineTotal = detail.getUnitPrice()
                    .multiply(BigDecimal.valueOf(actualQty))
                    .subtract(discount);
            newTotalAmount = newTotalAmount.add(lineTotal);

            if (actualQty > 0) {
                ImportBatch batch = createImportBatch(po, detail, datePart, lineTotal, actualQty);
                updateProductCostPrice(detail.getProduct(), lineTotal, actualQty);

                importBatchRepository.save(batch);
                detail.setImportBatch(batch);
            }
        }
        return newTotalAmount;
    }

    // sinh lô tự động sau khi xác nhận đơn hàng
    private ImportBatch createImportBatch(PurchaseOrder po, OrderDetail detail, String datePart, BigDecimal lineTotal,
            Integer actualQty) {
        ImportBatch batch = new ImportBatch();
        batch.setBatchCode("BAT-" + datePart + "-" + detail.getId());
        batch.setImportDate(LocalDate.now());
        batch.setProduct(detail.getProduct());
        batch.setPurchaseOrder(po);
        batch.setWareHouse(po.getWareHouse());

        // giá nhập tịnh = tổng tiền đã trừ chiết khấu / thực nhận
        BigDecimal netUnitPrice = lineTotal.divide(BigDecimal.valueOf(actualQty), 2, RoundingMode.HALF_UP);
        batch.setPrice(netUnitPrice);
        batch.setQuantity(0);

        return batch;
    }

    // tính toán giá vốn MAC: là giá thành trung bình phải bỏ ra để có được sản phẩm
    // trong kho
    private void updateProductCostPrice(Product product, BigDecimal lineTotal, Integer actualQty) {
        Integer oldTotalQty = importBatchRepository.sumQuantityByProductId(product.getId());
        if (oldTotalQty == null) {
            oldTotalQty = 0;
        }

        // giá vốn hiện tại của sản phẩm
        BigDecimal oldMac = product.getCostPrice() != null ? product.getCostPrice() : BigDecimal.ZERO;
        BigDecimal totalOldValue = oldMac.multiply(BigDecimal.valueOf(oldTotalQty));

        // giá vốn mới
        BigDecimal newMac = totalOldValue.add(lineTotal)
                .divide(BigDecimal.valueOf(oldTotalQty + actualQty), 2, RoundingMode.HALF_UP);

        product.setCostPrice(newMac);
        productRepository.save(product);
    }

    private void validateNewOrder(PurchaseOrder po, List<OrderDetail> orderDetails) {
        if (po.getCode() != null && !po.getCode().trim().isEmpty()) {
            if (purchaseOrderRepository.existsByCode(po.getCode().trim())) {
                throw new IllegalArgumentException(
                        "Lỗi nghiệp vụ: Mã đơn '" + po.getCode().trim() + "' đã tồn tại, vui lòng chọn mã khác!");
            }
        }
        if (orderDetails == null || orderDetails.isEmpty()) {
            throw new IllegalArgumentException("Lỗi nghiệp vụ: Đơn mua hàng bắt buộc phải có ít nhất một mặt hàng!");
        }
    }

    // sinh mã đơn tự động
    private void ensureOrderCode(PurchaseOrder po) {
        if (po.getCode() == null || po.getCode().trim().isEmpty()) {
            String timestamp = TIMESTAMP_FORMATTER.format(LocalDateTime.now());
            po.setCode("PO-" + timestamp);
        }
    }

    private void setDefaultOrderFields(PurchaseOrder po) {
        po.setCreatedAt(LocalDateTime.now());
        po.setStatus(PurchaseOrder.PurchaseStatus.PENDING);
        po.setPaymentStatus(PaymentStatus.UNPAID);
        po.setPaidAmount(BigDecimal.ZERO);
    }

    private BigDecimal calculateTotalOrderAmount(PurchaseOrder po, List<OrderDetail> orderDetails) {
        BigDecimal totalOrderAmount = BigDecimal.ZERO;

        for (OrderDetail detail : orderDetails) {
            detail.setOrderHeader(po);

            Product refreshedProduct = productRepository.findById(detail.getProduct().getId())
                    .orElse(detail.getProduct());

            if (refreshedProduct.getStatus() == com.example.demo.entity.Product.ProductStatus.INACTIVE) {
                String productName = refreshedProduct.getName() != null ? refreshedProduct.getName() : "Không xác định";
                throw new IllegalArgumentException("Lỗi nghiệp vụ: Sản phẩm '" + productName
                        + "' đã chuyển sang trạng thái ngừng kinh doanh (INACTIVE) khi xử lý. Không thể nhập hàng!");
            }
            detail.setProduct(refreshedProduct);

            BigDecimal discount = detail.getDiscountAmount() != null ? detail.getDiscountAmount() : BigDecimal.ZERO;
            // tính tổng tiền nhập cho dòng sản phẩm sau khi trừ chiết khấu
            BigDecimal lineTotal = detail.getUnitPrice()
                    .multiply(BigDecimal.valueOf(detail.getQuantity()))
                    .subtract(discount);
            totalOrderAmount = totalOrderAmount.add(lineTotal);
        }
        return totalOrderAmount;
    }

    private void validateFinalProductStatuses(List<OrderDetail> orderDetails) {
        for (OrderDetail detail : orderDetails) {
            if (detail.getProduct().getStatus() == com.example.demo.entity.Product.ProductStatus.INACTIVE) {
                String productName = detail.getProduct().getName() != null ? detail.getProduct().getName()
                        : "Không xác định";
                throw new IllegalArgumentException("[CRITICAL] Lỗi hệ thống: Sản phẩm '" + productName
                        + "' INACTIVE được phát hiện trước khi lưu đơn hàng. Vui lòng liên hệ admin!");
            }
        }
    }

    private Employee fetchCurrentEmployee() {
        String currentUsername = SecurityContextHolder.getContext().getAuthentication().getName();
        return employeeRepository.findByUser_Username(currentUsername)
                .orElseThrow(() -> new IllegalStateException(
                        "Lỗi bảo mật: Không tìm thấy hồ sơ Nhân viên của tài khoản này!"));
    }

    private String saveDeliveryDocument(MultipartFile documentFile, String poCode) {
        try {
            String fileName = "PO_DOC_" + poCode + "_" + System.currentTimeMillis() + "_"
                    + documentFile.getOriginalFilename();

            new java.io.File(PROJECT_DOC_PATH).mkdirs();
            new java.io.File(BUILD_DOC_PATH).mkdirs();

            java.nio.file.Path pathInProject = java.nio.file.Paths.get(PROJECT_DOC_PATH + fileName);
            java.nio.file.Path pathInBuild = java.nio.file.Paths.get(BUILD_DOC_PATH + fileName);

            java.nio.file.Files.copy(documentFile.getInputStream(), pathInProject,
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            java.nio.file.Files.copy(documentFile.getInputStream(), pathInBuild,
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);

            return WEB_DOC_PATH + fileName;
        } catch (java.io.IOException e) {
            throw new RuntimeException(
                    "Lỗi hệ thống: Không thể lưu tệp tin chứng từ giao hàng! Chi tiết: " + e.getMessage());
        }
    }
}