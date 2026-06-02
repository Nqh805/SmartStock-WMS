package com.example.demo.service;

import com.example.demo.dto.ReceiptFormDTO;
import com.example.demo.entity.Order.DeliveryResult;
import com.example.demo.entity.Order.OrderDetail;
import com.example.demo.entity.Order.PaymentStatus;
import com.example.demo.entity.Order.PurchaseOrder;
import com.example.demo.entity.Product.ImportBatch;
import com.example.demo.entity.Product.Product;
import com.example.demo.repository.ImportBatchRepository;
import com.example.demo.repository.OrderDetailRepository;
import com.example.demo.repository.ProductRepository;
import com.example.demo.repository.PurchaseOrderRepository;
import com.example.demo.repository.SupplierRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PurchaseOrderService {

    private final PurchaseOrderRepository purchaseOrderRepository;
    private final OrderDetailRepository orderDetailRepository;
    private final ImportBatchRepository importBatchRepository;
    private final ProductRepository productRepository;
    private final SupplierRepository supplierRepository;

    public PurchaseOrder getPurchaseOrderById(Long id) {
        return purchaseOrderRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn nhập hàng mã " + id));
    }

    @Transactional
    public void receiveOrderAndGenerateBatches(Long poId, ReceiptFormDTO formDTO, MultipartFile deliveryDocumentFile) {
        // 1. Tìm đơn hàng và danh sách chi tiết
        PurchaseOrder po = purchaseOrderRepository.findById(poId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn hàng mã: " + poId));
        List<OrderDetail> details = orderDetailRepository.findByOrderHeaderId(poId);

        if (formDTO.getActualQuantities() == null || formDTO.getActualQuantities().isEmpty()) {
            throw new IllegalArgumentException(
                    "Lỗi nghiệp vụ: Dữ liệu kiểm đếm trống! Vui lòng kiểm tra lại danh sách sản phẩm.");
        }
        // ngày nhận thực tế >= ngày tạo đơn
        if (formDTO.getActualArrival() != null && po.getCreatedAt() != null) {
            LocalDate dateCreated = po.getCreatedAt().toLocalDate();
            if (formDTO.getActualArrival().isBefore(dateCreated)) {
                throw new IllegalArgumentException(
                        "Lỗi nghiệp vụ: Ngày tới thực tế không được nhỏ hơn ngày tạo đơn hàng (" +
                                dateCreated.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) + ")");
            }
        }

        // tổng số lượng thực nhận toàn đơn hàng > 0
        int totalActualReceived = details.stream()
                .mapToInt(detail -> formDTO.getActualQuantities().getOrDefault(detail.getId(), 0))
                .sum();
        if (totalActualReceived <= 0) {
            throw new IllegalArgumentException(
                    "Lỗi nghiệp vụ: Tổng số lượng thực nhận của toàn bộ đơn hàng phải lớn hơn 0!");
        }
        // validate end

        LocalDate actualArrival = formDTO.getActualArrival();
        po.setActualArrival(actualArrival);

        if (po.getExpectedArrival() != null && actualArrival != null) {
            com.example.demo.entity.Partner.Supplier supplier = po.getSupplier();

            if (supplier.getReliabilityScore() == null)
                supplier.setReliabilityScore(100);
            if (supplier.getLateDeliveryCount() == null)
                supplier.setLateDeliveryCount(0);

            if (actualArrival.isEqual(po.getExpectedArrival())) {
                po.setDeliveryResult(DeliveryResult.ON_TIME);
                supplier.setReliabilityScore(supplier.getReliabilityScore() + 5);

            } else if (actualArrival.isBefore(po.getExpectedArrival())) {
                po.setDeliveryResult(DeliveryResult.EARLY);
                supplier.setReliabilityScore(supplier.getReliabilityScore() + 15);

            } else {
                po.setDeliveryResult(DeliveryResult.LATE);
                supplier.setReliabilityScore(supplier.getReliabilityScore() - 10);
                supplier.setLateDeliveryCount(supplier.getLateDeliveryCount() + 1);
            }

            if (supplier.getReliabilityScore() > 100) {
                supplier.setReliabilityScore(100);
            }

            supplierRepository.save(supplier); // Lưu lại điểm uy tín mới
        }

        // 3. Xử lý chi tiết đơn hàng: Cập nhật số lượng, tính tiền và sinh lô nhập
        BigDecimal newTotalAmount = BigDecimal.ZERO;
        String datePart = LocalDate.now().format(DateTimeFormatter.ofPattern("yyMMdd"));

        for (OrderDetail detail : details) {
            Integer actualQty = formDTO.getActualQuantities().getOrDefault(detail.getId(), 0);
            detail.setActualQuantity(actualQty);

            BigDecimal lineTotal = detail.getUnitPrice()
                    .multiply(BigDecimal.valueOf(actualQty))
                    .subtract(detail.getDiscountAmount() != null ? detail.getDiscountAmount() : BigDecimal.ZERO);
            newTotalAmount = newTotalAmount.add(lineTotal);

            // chỉ tính toán giá thành khi có nhập hàng vào lô
            if (actualQty > 0) {
                // Tạo lô nhập (ImportBatch)
                ImportBatch batch = new ImportBatch();
                batch.setBatchCode("BAT-" + datePart + "-" + detail.getId());
                batch.setImportDate(LocalDate.now());
                batch.setProduct(detail.getProduct());
                batch.setPurchaseOrder(po);
                batch.setWareHouse(po.getWareHouse());

                // Tính giá nhập tịnh (của lô)
                BigDecimal netUnitPrice = lineTotal.divide(BigDecimal.valueOf(actualQty), 2,
                        java.math.RoundingMode.HALF_UP);
                batch.setPrice(netUnitPrice);
                batch.setQuantityOnHand(actualQty);
                batch.setQuantityAvailable(actualQty);

                // Tính giá MAC (Moving Average Cost) và cập nhật vào Product
                Product product = detail.getProduct();
                Integer oldTotalQty = importBatchRepository.sumQuantityByProductId(product.getId());
                if (oldTotalQty == null)
                    oldTotalQty = 0;

                BigDecimal oldMac = product.getCostPrice() != null ? product.getCostPrice() : BigDecimal.ZERO;
                BigDecimal totalOldValue = oldMac.multiply(BigDecimal.valueOf(oldTotalQty));

                BigDecimal newMac = totalOldValue.add(lineTotal)
                        .divide(BigDecimal.valueOf(oldTotalQty + actualQty), 2, java.math.RoundingMode.HALF_UP);

                product.setCostPrice(newMac);
                product.setStandardCost(detail.getUnitPrice());
                productRepository.save(product);

                importBatchRepository.save(batch);
                detail.setImportBatch(batch);
            }
        }

        // 4. Lưu chứng từ (nếu có)
        if (deliveryDocumentFile != null && !deliveryDocumentFile.isEmpty()) {
            String documentUrl = saveDeliveryDocument(deliveryDocumentFile, po.getCode());
            po.setDeliveryDocument(documentUrl);
        }

        // 5. Cập nhật tổng tiền và lưu dữ liệu
        po.setTotalPurchaseAmount(newTotalAmount);
        po.setStatus(PurchaseOrder.PurchaseStatus.COMPLETED);

        orderDetailRepository.saveAll(details);
        purchaseOrderRepository.save(po);
    }

    @Transactional
    public void createNewPurchaseOrder(PurchaseOrder po) {
        List<OrderDetail> orderDetails = po.getOrderDetails();

        // validate start
        // check trùng mã đơn (nếu client có truyền lên)
        if (po.getCode() != null && !po.getCode().trim().isEmpty()) {
            if (purchaseOrderRepository.existsByCode(po.getCode().trim())) {
                throw new IllegalArgumentException(
                        "Lỗi nghiệp vụ: Mã đơn '" + po.getCode().trim() + "' đã tồn tại, vui lòng chọn mã khác!");
            }
        }

        // danh sách sản phẩm không được trống
        if (orderDetails == null || orderDetails.isEmpty()) {
            throw new IllegalArgumentException("Lỗi nghiệp vụ: Đơn mua hàng bắt buộc phải có ít nhất một mặt hàng!");
        }
        // validate end

        // 1. Sinh mã đơn (nếu chưa có)
        if (po.getCode() == null || po.getCode().trim().isEmpty()) {
            String timestamp = DateTimeFormatter.ofPattern("yyyyMMddHHmmss").format(LocalDateTime.now());
            po.setCode("PO-" + timestamp);
        }

        // 2. Set trạng thái mặc định cho đơn tạo mới
        po.setCreatedAt(LocalDateTime.now());
        po.setStatus(PurchaseOrder.PurchaseStatus.PENDING);
        po.setPaymentStatus(PaymentStatus.UNPAID);
        po.setPaidAmount(BigDecimal.ZERO);

        // 3. Tính tạm tổng tiền dự kiến
        BigDecimal totalOrderAmount = BigDecimal.ZERO;
        for (OrderDetail detail : orderDetails) {
            detail.setOrderHeader(po); // Quan trọng: Phải map khóa ngoại về Order header

            BigDecimal lineTotal = detail.getUnitPrice()
                    .multiply(BigDecimal.valueOf(detail.getQuantity()))
                    .subtract(detail.getDiscountAmount() != null ? detail.getDiscountAmount() : BigDecimal.ZERO);
            totalOrderAmount = totalOrderAmount.add(lineTotal);
        }
        po.setTotalPurchaseAmount(totalOrderAmount);

        // 4. Lưu thông tin
        purchaseOrderRepository.save(po);
        orderDetailRepository.saveAll(orderDetails);
    }

    @Transactional
    public void approveOrder(Long id) {
        PurchaseOrder po = purchaseOrderRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy đơn hàng"));

        // trạng thái hiện tại phải là PENDING mới được phép duyệt
        if (po.getStatus() != PurchaseOrder.PurchaseStatus.PENDING) {
            throw new RuntimeException("Đơn hàng này không ở trạng thái chờ duyệt!");
        }

        // 1. Chuyển trạng thái sang Đang giao hàng
        po.setStatus(PurchaseOrder.PurchaseStatus.IN_TRANSIT);
        purchaseOrderRepository.save(po);
    }

    private String saveDeliveryDocument(MultipartFile documentFile, String poCode) {
        if (documentFile == null || documentFile.isEmpty()) {
            return null;
        }

        try {
            String projectPath = "src/main/resources/static/uploads/documents/";
            String buildPath = "target/classes/static/uploads/documents/";

            String fileName = "PO_DOC_" + poCode + "_" + System.currentTimeMillis() + "_"
                    + documentFile.getOriginalFilename();

            // Tạo thư mục nếu chưa tồn tại
            new java.io.File(projectPath).mkdirs();
            new java.io.File(buildPath).mkdirs();

            java.nio.file.Path pathInProject = java.nio.file.Paths.get(projectPath + fileName);
            java.nio.file.Path pathInBuild = java.nio.file.Paths.get(buildPath + fileName);

            // Copy file vào cả project và build folder
            java.nio.file.Files.copy(documentFile.getInputStream(), pathInProject,
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            java.nio.file.Files.copy(documentFile.getInputStream(), pathInBuild,
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);

            return "/uploads/documents/" + fileName;
        } catch (java.io.IOException e) {
            throw new RuntimeException(
                    "Lỗi hệ thống: Không thể lưu tệp tin chứng từ giao hàng! Chi tiết: " + e.getMessage());
        }
    }

    @Transactional
    public void reportFailedDelivery(Long poId, String failReason) {
        PurchaseOrder po = purchaseOrderRepository.findById(poId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy đơn hàng"));

        // Chuyển trạng thái đơn thành Hủy / Giao thất bại
        po.setStatus(PurchaseOrder.PurchaseStatus.CANCELLED);
        po.setReceivedNote(failReason); // Lưu lý do thất bại

        // Giao hỏng - 15
        com.example.demo.entity.Partner.Supplier supplier = po.getSupplier();
        if (supplier != null) {
            if (supplier.getReliabilityScore() == null)
                supplier.setReliabilityScore(100);
            if (supplier.getFailedDeliveryCount() == null)
                supplier.setFailedDeliveryCount(0);

            supplier.setReliabilityScore(supplier.getReliabilityScore() - 15);
            supplier.setFailedDeliveryCount(supplier.getFailedDeliveryCount() + 1);

            supplierRepository.save(supplier);
        }

        purchaseOrderRepository.save(po);
    }

    // 1. HÀM XÓA HẲN (Dành cho trường hợp nhân viên nhập nhầm)
    @Transactional
    public void deletePurchaseOrder(Long id) {
        PurchaseOrder po = purchaseOrderRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn hàng này!"));

        if (po.getStatus() != PurchaseOrder.PurchaseStatus.PENDING) {
            throw new IllegalStateException("Chỉ được phép xóa đơn hàng đang chờ xử lý!");
        }
        purchaseOrderRepository.delete(po);
    }

    // 2. HÀM TỪ CHỐI DUYỆT (Chỉ đổi trạng thái thành CANCELLED)
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
}