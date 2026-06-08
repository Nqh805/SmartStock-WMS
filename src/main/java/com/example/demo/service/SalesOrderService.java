package com.example.demo.service;

import com.example.demo.entity.Order.SalesOrder;
import com.example.demo.entity.Order.OrderDetail;
import com.example.demo.entity.Order.PaymentStatus;
import com.example.demo.entity.Partner.Customer;
import com.example.demo.entity.Warehouse.WareHouse;
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

    private final SalesOrderRepository salesOrderRepository;
    private final CustomerRepository customerRepository;
    private final WareHouseRepository wareHouseRepository;
    private final ImportBatchRepository importBatchRepository;
    private final ProductItemRepository productItemRepository;
    private final EmployeeRepository employeeRepository;
    private final ProductService productService;

    /**
     * Lấy danh sách đơn bán hàng kèm bộ lọc từ ngày - đến ngày và phân trang
     */
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

    public SalesOrder getSalesOrderById(Long id) {
        return salesOrderRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn hàng mã " + id));
    }

    // =========================================================================
    // 🚀 CHỐT ĐƠN 1 CHẠM POS (Tạo đơn + Trừ Serial trực tiếp + Trừ Lô + Hoàn thành)
    // =========================================================================
    @Transactional
    public void createNewSalesOrder(SalesOrder salesOrder, List<String> scannedSerials) {
        // 1. Kiểm tra lớp bảo vệ đầu vào
        if (salesOrder.getOrderDetails() == null || salesOrder.getOrderDetails().isEmpty()) {
            throw new IllegalArgumentException("Không thể chốt đơn! Giỏ hàng đang trống rỗng.");
        }
        if (scannedSerials == null || scannedSerials.isEmpty()) {
            throw new IllegalArgumentException("Vui lòng quét ít nhất 1 mã Serial để xuất kho chốt đơn!");
        }

        // Dọn dẹp dòng trống do lỗi parse HTML giao diện nếu có
        salesOrder.getOrderDetails()
                .removeIf(detail -> detail.getProduct() == null || detail.getProduct().getId() == null);

        // 🚀 GIẢI PHÁP SỬA LỖI DUPLICATE ENTRY: Sinh mã SO-XXXXXXXX đúng 11 ký tự
        // Tránh thuật toán ngày giờ cũ bị Database cắt cụt đuôi gây trùng mã sập hệ
        // thống
        if (salesOrder.getCode() == null || salesOrder.getCode().trim().isEmpty()) {
            int randomNum = new Random().nextInt(90000000) + 10000000; // Đảm bảo luôn ra đúng 8 chữ số
            salesOrder.setCode("SO-" + randomNum);
        }

        salesOrder.setConfirmed(true);
        // Vì quét mã trực tiếp tại quầy thanh toán nên đơn hàng tự động chuyển sang
        // COMPLETED luôn
        salesOrder.setStatus(SalesOrder.SalesStatus.COMPLETED);

        // Lưu vết nhân viên thu ngân thực hiện giao dịch này
        String currentUsername = SecurityContextHolder.getContext().getAuthentication().getName();
        Employee currentEmployee = employeeRepository.findByUser_Username(currentUsername)
                .orElseThrow(() -> new IllegalStateException(
                        "Lỗi bảo mật: Không tìm thấy hồ sơ Nhân viên của tài khoản này!"));
        salesOrder.setEmployee(currentEmployee);

        BigDecimal grandTotalSales = BigDecimal.ZERO;
        List<OrderDetail> finalOrderDetails = new ArrayList<>();
        int totalRequestedQty = 0;

        // 2. Duyệt giỏ hàng tính toán tiền bạc và chuẩn bị cấu trúc dữ liệu lưu trữ
        for (OrderDetail detail : salesOrder.getOrderDetails()) {
            BigDecimal unitPrice = detail.getUnitPrice() != null ? detail.getUnitPrice() : BigDecimal.ZERO;
            int requestedQty = detail.getQuantity() != null ? detail.getQuantity() : 0;
            BigDecimal totalDiscountForLine = detail.getDiscountAmount() != null ? detail.getDiscountAmount()
                    : BigDecimal.ZERO;

            if (requestedQty <= 0) {
                throw new IllegalArgumentException("Số lượng xuất bán của sản phẩm phải lớn hơn 0!");
            }
            totalRequestedQty += requestedQty;

            // Thiết lập dòng chi tiết đơn hàng
            OrderDetail singleDetail = new OrderDetail();
            singleDetail.setProduct(detail.getProduct());
            singleDetail.setUnitPrice(unitPrice);
            singleDetail.setQuantity(requestedQty);
            singleDetail.setDiscountAmount(totalDiscountForLine);
            singleDetail.setOrderHeader(salesOrder);
            singleDetail.setActualQuantity(requestedQty); // Xuất kho thành công đủ 100%

            // Công thức tính toán dòng tiền
            BigDecimal lineSubtotal = unitPrice.multiply(BigDecimal.valueOf(requestedQty));
            BigDecimal lineTotal = lineSubtotal.subtract(totalDiscountForLine);
            grandTotalSales = grandTotalSales
                    .add(lineTotal.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : lineTotal);

            finalOrderDetails.add(singleDetail);
        }

        // 🚀 KHIÊN BẢO VỆ KÉP (ANTI-HACK): Chống sửa đổi mã HTML tăng/giảm ảo số lượng
        if (totalRequestedQty != scannedSerials.size()) {
            throw new IllegalArgumentException("Lỗi nghiêm trọng: Tổng số lượng hàng trong giỏ (" + totalRequestedQty
                    + ") đang không khớp với số mã Serial thực tế được bắn từ súng quét (" + scannedSerials.size()
                    + ")!");
        }

        // Tính tổng tiền cuối cùng bao gồm cả Phí Ship (nếu có)
        BigDecimal shippingFee = salesOrder.getShippingFee() != null ? salesOrder.getShippingFee() : BigDecimal.ZERO;
        salesOrder.setTotalSalesAmount(grandTotalSales.add(shippingFee));
        salesOrder.setOrderDetails(finalOrderDetails);

        // 3. Phân tích trạng thái thanh toán đơn hàng
        BigDecimal paidAmt = salesOrder.getPaidAmount() != null ? salesOrder.getPaidAmount() : BigDecimal.ZERO;
        if (paidAmt.compareTo(salesOrder.getTotalSalesAmount()) > 0) {
            paidAmt = salesOrder.getTotalSalesAmount();
        }
        salesOrder.setPaidAmount(paidAmt);

        if (paidAmt.compareTo(salesOrder.getTotalSalesAmount()) >= 0
                && salesOrder.getTotalSalesAmount().compareTo(BigDecimal.ZERO) > 0) {
            salesOrder.setPaymentStatus(PaymentStatus.PAID);
        } else if (paidAmt.compareTo(BigDecimal.ZERO) > 0) {
            salesOrder.setPaymentStatus(PaymentStatus.PARTIAL);
        } else {
            salesOrder.setPaymentStatus(PaymentStatus.UNPAID);
        }

        // Tự động tính tiền COD thu hộ dựa theo phương thức vận chuyển
        if (salesOrder.getDeliveryMethod() == SalesOrder.DeliveryMethod.SHIPPING) {
            salesOrder.setCod(salesOrder.getTotalSalesAmount().subtract(paidAmt));
        } else {
            salesOrder.setCod(BigDecimal.ZERO);
        }

        List<ProductItem> itemsToUpdate = new ArrayList<>();

        for (String serial : scannedSerials) {
            ProductItem item = productItemRepository.findBySerialNumber(serial.trim())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Mã Serial/IMEI '" + serial + "' không tồn tại dưới cơ sở dữ liệu!"));

            // Kiểm tra trạng thái máy vật lý tại thời điểm chốt đơn
            if (item.getStatus() != ItemStatus.IN_STOCK) {
                throw new IllegalStateException("Mã Serial/IMEI '" + serial
                        + "' đang không ở trạng thái Sẵn sàng bán (Có thể đã xuất kho từ trước)!");
            }

            // A. Chuyển đổi trạng thái máy sang ĐÃ BÁN.
            // Không gán salesOrder vào ProductItem trước khi đơn hàng đã được lưu,
            // để tránh Hibernate tham chiếu tới SalesOrder transient.
            item.setStatus(ItemStatus.SOLD);
            itemsToUpdate.add(item);

            // B. TRUY VẤN NGƯỢC VÀ TRỪ TỒN ĐÚNG LÔ HÀNG CỦA THIẾT BỊ ĐÓ (Specific
            // Identification)
            ImportBatch batch = item.getImportBatch();
            if (batch.getQuantity() == null || batch.getQuantity() <= 0) {
                throw new IllegalStateException(
                        "Lỗi logic kho: Lô hàng mang mã " + batch.getBatchCode() + " đã cạn kiệt tồn kho thực tế!");
            }
            batch.setQuantity(batch.getQuantity() - 1);
            importBatchRepository.save(batch); // Cập nhật số lượng lô hàng
        }

        SalesOrder savedOrder = salesOrderRepository.save(salesOrder);

        for (ProductItem item : itemsToUpdate) {
            item.setSalesOrder(savedOrder);
        }
        productItemRepository.saveAll(itemsToUpdate);
    }

    @Transactional
    public void processPayment(Long orderId, BigDecimal amountToPay) {
        SalesOrder order = salesOrderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn hàng mã " + orderId));

        if (order.getPaymentStatus() == PaymentStatus.PAID) {
            throw new IllegalStateException("Đơn hàng này đã được tất toán đầy đủ trước đó!");
        }

        BigDecimal currentPaid = order.getPaidAmount() != null ? order.getPaidAmount() : BigDecimal.ZERO;
        BigDecimal newPaidAmount = currentPaid.add(amountToPay);
        BigDecimal totalAmount = order.getTotalSalesAmount() != null ? order.getTotalSalesAmount() : BigDecimal.ZERO;

        if (newPaidAmount.compareTo(totalAmount) > 0) {
            order.setPaidAmount(totalAmount);
        } else {
            order.setPaidAmount(newPaidAmount);
        }

        // Tái đánh giá lại trạng thái dòng tiền
        if (order.getPaidAmount().compareTo(totalAmount) >= 0) {
            order.setPaymentStatus(PaymentStatus.PAID);
        } else if (order.getPaidAmount().compareTo(BigDecimal.ZERO) > 0) {
            order.setPaymentStatus(PaymentStatus.PARTIAL);
        } else {
            order.setPaymentStatus(PaymentStatus.UNPAID);
        }

        // Cập nhật lại công nợ COD cho bên giao vận nếu là đơn Ship
        if (order.getDeliveryMethod() == SalesOrder.DeliveryMethod.SHIPPING) {
            BigDecimal cod = totalAmount.subtract(order.getPaidAmount());
            order.setCod(cod.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : cod);
        }

        salesOrderRepository.save(order);
    }
}