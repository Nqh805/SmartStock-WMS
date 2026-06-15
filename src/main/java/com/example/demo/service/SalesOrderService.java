package com.example.demo.service;

import com.example.demo.entity.Order.SalesOrder;
import com.example.demo.entity.Order.OrderDetail;
import com.example.demo.entity.Order.PaymentStatus;
import com.example.demo.entity.Partner.Customer;
import com.example.demo.entity.Warehouse.WareHouse;
import com.example.demo.exception.DuplicateOrderException;
import com.example.demo.exception.InvalidDataException;
import com.example.demo.entity.Product.Product;
import com.example.demo.entity.Product.ProductItem;
import com.example.demo.entity.Product.ItemStatus;
import com.example.demo.entity.Product.ImportBatch;
import com.example.demo.entity.User.Employee;
import com.example.demo.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SalesOrderService {

    // --- CONSTANTS ---
    private static final String ORDER_CODE_PREFIX = "SO-";
    private static final int RANDOM_UPPER_BOUND = 90000000;
    private static final int RANDOM_OFFSET = 10000000;

    // --- REPOSITORIES & SERVICES ---
    private final SalesOrderRepository salesOrderRepository;
    private final CustomerRepository customerRepository;
    private final WareHouseRepository wareHouseRepository;
    private final ImportBatchRepository importBatchRepository;
    private final ProductItemRepository productItemRepository;
    private final EmployeeRepository employeeRepository;
    private final ProductService productService;

    public Page<SalesOrder> getSalesOrder(String keyword, LocalDateTime startDate,
            LocalDateTime endDate, int page, int size) {
        Pageable pageable = PageRequest.of(page - 1, size, Sort.by("id").descending());
        return salesOrderRepository.searchWithFilters(keyword, startDate, endDate, pageable);
    }

    public List<Customer> getAllCustomers() {
        return customerRepository.findAll();
    }

    public List<WareHouse> getAllWarehouses() {
        return wareHouseRepository.findAll();
    }

    public List<Product> getAllProducts() {
        return productService.getAllProductsWithInventory();
    }

    // Truy xuất một đơn bán hàng cụ thể theo ID, ném ngoại lệ nếu không tồn tại
    public SalesOrder getSalesOrderById(Long id) {
        return salesOrderRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn hàng mã " + id));
    }

    // CHỐT ĐƠN 1 CHẠM POS: Xử lý toàn bộ quy trình tạo đơn, tính tiền, trừ kho
    // Serial/Lô và xác nhận hoàn thành
    @Transactional
    public void createNewSalesOrder(SalesOrder salesOrder, List<String> scannedSerials) {
        // 1. Kiểm tra tính hợp lệ cơ bản của giỏ hàng
        validateCartNotEmpty(salesOrder, scannedSerials);
        cleanAndValidateInitialCartItems(salesOrder);

        // 2. Khởi tạo và Xác thực Mã đơn hàng (Ném ngoại lệ nếu trùng)
        ensureOrderCode(salesOrder);
        checkIfOrderCodeAlreadyExists(salesOrder.getCode()); // <-- LOGIC KIỂM TRA TRÙNG LẶP ĐƯỢC CHÈN VÀO ĐÂY

        // 3. Chuẩn bị bối cảnh (Context) cho đơn hàng
        setupInitialOrderContext(salesOrder);

        // 4. Tính toán dòng tiền của giỏ hàng, đồng thời trích xuất số lượng thực tế
        // để đối chiếu với hệ thống chống gian lận (Anti-hack)
        CartProcessingResult cartResult = processCartItemsAndCalculateTotals(salesOrder);

        // 5. Đối chiếu số lượng và trạng thái sản phẩm
        verifyQuantitiesMatchSerials(cartResult.getTotalRequestedQty(), scannedSerials.size());
        validateFinalProductStatuses(cartResult.getFinalOrderDetails());

        // 6. Hoàn thiện giá và phương thức thanh toán
        finalizeOrderPricing(salesOrder, cartResult);
        evaluatePaymentAndCod(salesOrder);

        // 7. Xử lý xuất kho vật lý và Lưu vào Cơ sở dữ liệu
        processInventoryDeduction(salesOrder, scannedSerials);
    }

    // Cập nhật số tiền thanh toán của đơn hàng và tự động đánh giá lại trạng thái
    // công nợ/COD
    @Transactional
    public void processPayment(Long orderId, BigDecimal amountToPay) {
        SalesOrder order = salesOrderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn hàng mã " + orderId));

        if (order.getPaymentStatus() == PaymentStatus.PAID) {
            throw new IllegalStateException("Đơn hàng này đã được tất toán đầy đủ trước đó!");
        }

        BigDecimal currentPaid = order.getPaidAmount() != null ? order.getPaidAmount() : BigDecimal.ZERO;
        order.setPaidAmount(currentPaid.add(amountToPay));

        evaluatePaymentAndCod(order);

        salesOrderRepository.save(order);
    }

    // Xác nhận đơn hàng giao vận đã tới tay khách hàng thành công
    @Transactional
    public void confirmShippingDelivery(Long orderId) {
        SalesOrder order = salesOrderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn hàng mã " + orderId));

        if (order.getDeliveryMethod() != SalesOrder.DeliveryMethod.SHIPPING) {
            throw new IllegalStateException("Chỉ có đơn giao hàng mới được xác nhận giao hàng.");
        }

        if (order.getStatus() == SalesOrder.SalesStatus.COMPLETED) {
            throw new IllegalStateException("Đơn hàng đã được xác nhận giao hàng trước đó.");
        }

        order.setStatus(SalesOrder.SalesStatus.COMPLETED);
        order.setCompletedAt(LocalDateTime.now());
        salesOrderRepository.save(order);
    }

    // ======= PRIVATE HELPER METHODS ========

    // Xác thực giỏ hàng và danh sách serial đầu vào không được trống
    private void validateCartNotEmpty(SalesOrder salesOrder, List<String> scannedSerials) {
        if (salesOrder.getOrderDetails() == null || salesOrder.getOrderDetails().isEmpty()) {
            throw new IllegalArgumentException("Không thể chốt đơn! Giỏ hàng đang trống rỗng.");
        }
        if (scannedSerials == null || scannedSerials.isEmpty()) {
            throw new IllegalArgumentException("Vui lòng quét ít nhất 1 mã Serial để xuất kho chốt đơn!");
        }
    }

    // Dọn dẹp rác giao diện (HTML parse lỗi) và chặn bán sản phẩm Inactive ở cấp độ
    // khởi tạo
    private void cleanAndValidateInitialCartItems(SalesOrder salesOrder) {
        salesOrder.getOrderDetails()
                .removeIf(detail -> detail.getProduct() == null || detail.getProduct().getId() == null);

        for (OrderDetail detail : salesOrder.getOrderDetails()) {
            Product product = detail.getProduct();
            if (product.getStatus() == com.example.demo.entity.Product.ProductStatus.INACTIVE) {
                String productName = product.getName() != null ? product.getName() : "Không xác định";
                throw new IllegalArgumentException("Lỗi nghiệp vụ: Sản phẩm '" + productName
                        + "' đã bị ngừng bán (INACTIVE). Vui lòng loại bỏ khỏi giỏ hàng!");
            }
        }
    }

    // Sinh mã hóa đơn ngẫu nhiên đúng định dạng nếu client chưa cung cấp
    private void ensureOrderCode(SalesOrder salesOrder) {
        if (salesOrder.getCode() == null || salesOrder.getCode().trim().isEmpty()) {
            int randomNum = new Random().nextInt(RANDOM_UPPER_BOUND) + RANDOM_OFFSET;
            salesOrder.setCode(ORDER_CODE_PREFIX + randomNum);
        }
    }

    // Thiết lập các trạng thái mặc định và gán nhân viên phụ trách cho đơn mới
    private void setupInitialOrderContext(SalesOrder salesOrder) {
        salesOrder.setConfirmed(true);
        if (salesOrder.getDeliveryMethod() == SalesOrder.DeliveryMethod.SHIPPING) {
            salesOrder.setStatus(SalesOrder.SalesStatus.DELIVERING);
        } else {
            salesOrder.setStatus(SalesOrder.SalesStatus.COMPLETED);
            salesOrder.setCompletedAt(LocalDateTime.now());
        }

        String currentUsername = SecurityContextHolder.getContext().getAuthentication().getName();
        Employee currentEmployee = employeeRepository.findByUser_Username(currentUsername)
                .orElseThrow(() -> new IllegalStateException(
                        "Lỗi bảo mật: Không tìm thấy hồ sơ Nhân viên của tài khoản này!"));
        salesOrder.setEmployee(currentEmployee);
    }

    // Duyệt qua từng sản phẩm trong giỏ, cập nhật giá thực tế từ Database và tính
    // tổng tiền
    private CartProcessingResult processCartItemsAndCalculateTotals(SalesOrder salesOrder) {
        BigDecimal grandTotalSales = BigDecimal.ZERO;
        List<OrderDetail> finalOrderDetails = new ArrayList<>();
        int totalRequestedQty = 0;

        for (OrderDetail detail : salesOrder.getOrderDetails()) {
            BigDecimal unitPrice = detail.getUnitPrice() != null ? detail.getUnitPrice() : BigDecimal.ZERO;
            int requestedQty = detail.getQuantity() != null ? detail.getQuantity() : 0;
            BigDecimal totalDiscountForLine = detail.getDiscountAmount() != null ? detail.getDiscountAmount()
                    : BigDecimal.ZERO;

            if (requestedQty <= 0) {
                throw new IllegalArgumentException("Số lượng xuất bán của sản phẩm phải lớn hơn 0!");
            }
            totalRequestedQty += requestedQty;

            Product refreshedProduct = productService.getById(detail.getProduct().getId());
            if (refreshedProduct.getStatus() == com.example.demo.entity.Product.ProductStatus.INACTIVE) {
                String productName = refreshedProduct.getName() != null ? refreshedProduct.getName() : "Không xác định";
                throw new IllegalArgumentException("Lỗi nghiệp vụ: Sản phẩm '" + productName
                        + "' đã bị ngừng bán (INACTIVE) khi xử lý. Vui lòng loại bỏ khỏi giỏ hàng!");
            }

            // Map dữ liệu an toàn vào Object mới để tránh các thuộc tính thừa từ client gửi
            // lên
            OrderDetail singleDetail = new OrderDetail();
            singleDetail.setProduct(refreshedProduct);
            singleDetail.setUnitPrice(unitPrice);
            singleDetail.setQuantity(requestedQty);
            singleDetail.setDiscountAmount(totalDiscountForLine);
            singleDetail.setOrderHeader(salesOrder);
            singleDetail.setActualQuantity(requestedQty);

            BigDecimal lineSubtotal = unitPrice.multiply(BigDecimal.valueOf(requestedQty));
            BigDecimal lineTotal = lineSubtotal.subtract(totalDiscountForLine);

            grandTotalSales = grandTotalSales
                    .add(lineTotal.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : lineTotal);
            finalOrderDetails.add(singleDetail);
        }

        return new CartProcessingResult(grandTotalSales, totalRequestedQty, finalOrderDetails);
    }

    // So khớp số lượng trên đơn và số lượng vật lý quét được

    private void verifyQuantitiesMatchSerials(int totalRequestedQty, int scannedCount) {
        if (totalRequestedQty != scannedCount) {
            throw new IllegalArgumentException("Lỗi nghiêm trọng: Tổng số lượng hàng trong giỏ (" + totalRequestedQty
                    + ") đang không khớp với số mã Serial thực tế được bắn từ súng quét (" + scannedCount + ")!");
        }
    }

    // Xác minh chốt chặn cuối cùng: Cấm tuyệt đối lưu sản phẩm Inactive vàoDatabase
    private void validateFinalProductStatuses(List<OrderDetail> finalOrderDetails) {
        for (OrderDetail detail : finalOrderDetails) {
            if (detail.getProduct().getStatus() == com.example.demo.entity.Product.ProductStatus.INACTIVE) {
                String productName = detail.getProduct().getName() != null ? detail.getProduct().getName()
                        : "Không xác định";
                throw new IllegalArgumentException("[CRITICAL] Lỗi hệ thống: Sản phẩm '" + productName
                        + "' INACTIVE được phát hiện trước khi lưu đơn hàng. Vui lòng liên hệ admin!");
            }
        }
    }

    // Cập nhật danh sách chi tiết đơn hàng cuối cùng và cộng phí vận chuyển
    private void finalizeOrderPricing(SalesOrder salesOrder, CartProcessingResult cartResult) {
        BigDecimal shippingFee = salesOrder.getShippingFee() != null ? salesOrder.getShippingFee() : BigDecimal.ZERO;
        salesOrder.setTotalSalesAmount(cartResult.getGrandTotalSales().add(shippingFee));
        salesOrder.setOrderDetails(cartResult.getFinalOrderDetails());
    }

    // Logic trung tâm: Đánh giá tiền khách đưa để chốt trạng
    // thái(PAID/PARTIAL/UNPAID) và tính tiền thu hộ COD
    private void evaluatePaymentAndCod(SalesOrder order) {
        BigDecimal totalAmount = order.getTotalSalesAmount() != null ? order.getTotalSalesAmount() : BigDecimal.ZERO;
        BigDecimal paidAmt = order.getPaidAmount() != null ? order.getPaidAmount() : BigDecimal.ZERO;

        // Ép giới hạn tiền đã trả không được vượt quá tổng hóa đơn
        if (paidAmt.compareTo(totalAmount) > 0) {
            paidAmt = totalAmount;
        }
        order.setPaidAmount(paidAmt);

        // Định danh trạng thái công nợ dựa trên tỉ lệ tiền trả / tổng tiền
        if (paidAmt.compareTo(totalAmount) >= 0 && totalAmount.compareTo(BigDecimal.ZERO) > 0) {
            order.setPaymentStatus(PaymentStatus.PAID);
        } else if (paidAmt.compareTo(BigDecimal.ZERO) > 0) {
            order.setPaymentStatus(PaymentStatus.PARTIAL);
        } else {
            order.setPaymentStatus(PaymentStatus.UNPAID);
        }

        // Tự động ủy quyền nhờ thu (COD) cho các đơn qua đối tác vận chuyển
        if (order.getDeliveryMethod() == SalesOrder.DeliveryMethod.SHIPPING) {
            BigDecimal cod = totalAmount.subtract(paidAmt);
            order.setCod(cod.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : cod);
        } else {
            order.setCod(BigDecimal.ZERO);
        }
    }

    // Xử lý trừ kho vật lý: Quét thiết bị thực tế, đánh dấu đã bán và trừ số lượng
    // của lô hàng tương ứng
    private void processInventoryDeduction(SalesOrder salesOrder, List<String> scannedSerials) {
        List<ProductItem> itemsToUpdate = new ArrayList<>();

        for (String serial : scannedSerials) {
            ProductItem item = productItemRepository.findBySerialNumber(serial.trim())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Mã Serial/IMEI '" + serial + "' không tồn tại dưới cơ sở dữ liệu!"));

            if (item.getStatus() != ItemStatus.IN_STOCK) {
                throw new IllegalStateException("Mã Serial/IMEI '" + serial
                        + "' đang không ở trạng thái Sẵn sàng bán (Có thể đã xuất kho từ trước)!");
            }

            item.setStatus(ItemStatus.SOLD);
            itemsToUpdate.add(item);

            // Truy vết đích danh (Specific Identification) từ thiết bị vật lý để
            // trừ tồn kho của Lô hàng nhập vào
            ImportBatch batch = item.getImportBatch();
            if (batch.getQuantity() == null || batch.getQuantity() <= 0) {
                throw new IllegalStateException(
                        "Lỗi logic kho: Lô hàng mang mã " + batch.getBatchCode() + " đã cạn kiệt tồn kho thực tế!");
            }
            // trừ số lượng tồn tại lô tương ứng
            batch.setQuantity(batch.getQuantity() - 1);
            importBatchRepository.save(batch);
        }

        SalesOrder savedOrder = salesOrderRepository.save(salesOrder);

        for (ProductItem item : itemsToUpdate) {
            item.setSalesOrder(savedOrder);
        }
        productItemRepository.saveAll(itemsToUpdate);
    }

    private void checkIfOrderCodeAlreadyExists(String orderCode) {
        if (orderCode == null || orderCode.trim().isEmpty()) {
            return;
        }

        // Gọi hàm existsByCode vừa tạo trong Repository
        boolean isExist = salesOrderRepository.existsByCode(orderCode);

        if (isExist) {
            throw new DuplicateOrderException("Mã đơn hàng đã tồn tại trong hệ thống!");
        }
    }

    private void validateSalesOrderData(SalesOrder salesOrder) {
        if (salesOrder.getCustomer() == null) {
            // Ném ra lỗi dữ liệu không hợp lệ
            throw new InvalidDataException("Đơn hàng bắt buộc phải có thông tin Khách hàng!");
        }
    }

    // Lớp Wrapper hỗ trợ gom nhóm dữ liệu trả về trong quá trình tính toán giỏ hàng
    @lombok.Getter
    @lombok.AllArgsConstructor
    private static class CartProcessingResult {
        private final BigDecimal grandTotalSales;
        private final int totalRequestedQty;
        private final List<OrderDetail> finalOrderDetails;
    }
}