package com.example.demo.service;

import com.example.demo.entity.Order.RmaTicket;
import com.example.demo.entity.Product.ItemStatus;
import com.example.demo.entity.Product.ProductItem;
import com.example.demo.repository.ProductItemRepository;
import com.example.demo.repository.RmaTicketRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RmaService {

    // --- CONSTANTS ---
    private static final String RMA_CODE_PREFIX = "RMA-";
    private static final String DEFAULT_HIDDEN_PRODUCT_NAME = "Sản phẩm ẩn";
    private static final DateTimeFormatter RMA_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyMMddHHmmss");
    private static final DateTimeFormatter EXPIRY_DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    // --- REPOSITORIES ---
    private final RmaTicketRepository rmaTicketRepository;
    private final ProductItemRepository productItemRepository;

    public Page<RmaTicket> getRmaTickets(String keyword, LocalDateTime startDate,
            LocalDateTime endDate, int page, int size) {
        Pageable pageable = PageRequest.of(page - 1, size, Sort.by("id").descending());

        String searchKeyword = (keyword != null && !keyword.trim().isEmpty()) ? keyword.trim() : null;
        return rmaTicketRepository.searchWithFilters(searchKeyword, startDate, endDate, pageable);
    }

    public RmaTicket getRmaTicketById(Long id) {
        return rmaTicketRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy phiếu RMA mang mã ID: " + id));
    }

    // Xử lý tạo mới một phiếu tiếp nhận bảo hành từ mã Serial của thiết bị.
    @Transactional
    public void createRmaTicket(RmaTicket rmaTicket, String serialNumber) {
        ProductItem item = productItemRepository.findBySerialNumber(serialNumber)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy thiết bị có Serial: " + serialNumber));

        // validate điều kiện bảo hành
        validateItemForRma(item);

        ensureRmaCode(rmaTicket);

        rmaTicket.setSerialNumber(item.getSerialNumber());
        rmaTicket.setProductName(item.getProduct() != null ? item.getProduct().getName() : DEFAULT_HIDDEN_PRODUCT_NAME);
        rmaTicket.setStatus(RmaTicket.RmaStatus.PROCESSING);

        rmaTicketRepository.save(rmaTicket);

        // chuyển thiết bị sang lỗi
        item.setStatus(ItemStatus.DEFECTIVE);
        productItemRepository.save(item);
    }

    @Transactional
    public void updateRmaStatus(Long id, RmaTicket.RmaStatus newStatus, String solution) {
        RmaTicket ticket = getRmaTicketById(id);
        ticket.setStatus(newStatus);

        if (solution != null && !solution.trim().isEmpty()) {
            ticket.setWarrantySolution(solution);
        }
        rmaTicketRepository.save(ticket);

        productItemRepository.findBySerialNumber(ticket.getSerialNumber()).ifPresent(item -> {
            item.setStatus(ItemStatus.SOLD);
            productItemRepository.save(item);
        });
    }

    @Transactional
    public void updateRmaStatusWithSwap(Long id, RmaTicket.RmaStatus newStatus, LocalDate actualReturnDate,
            String solution, String newSerial) {

        RmaTicket ticket = getRmaTicketById(id);

        // Cập nhật thông tin nghiệm thu của phiếu bảo hành
        ticket.setStatus(newStatus);
        ticket.setActualReturnDate(actualReturnDate);
        if (solution != null && !solution.trim().isEmpty()) {
            ticket.setWarrantySolution(solution.trim());
        }

        if (newSerial != null && !newSerial.trim().isEmpty()) {
            processReplacementDevice(ticket, newSerial.trim());
        }

        rmaTicketRepository.save(ticket);
    }

    // ======== PRIVATE HELPER METHODS ========

    private void validateItemForRma(ProductItem item) {
        if (item.getStatus() != ItemStatus.SOLD) {
            throw new IllegalStateException("Lỗi nghiệp vụ: Chỉ thiết bị đã xuất bán mới được lập phiếu RMA!");
        }
        if (item.isWarrantyExpired()) {
            String expDateStr = EXPIRY_DATE_FORMATTER.format(item.getWarrantyExpirationDate());
            throw new IllegalStateException(
                    "Từ chối tiếp nhận: Thiết bị này đã HẾT HẠN BẢO HÀNH vào ngày " + expDateStr + "!");
        }
    }

    // sinh mã phiếu RMA tự động
    private void ensureRmaCode(RmaTicket rmaTicket) {
        if (rmaTicket.getCode() == null || rmaTicket.getCode().isEmpty()) {
            String timeCode = RMA_TIME_FORMATTER.format(LocalDateTime.now());
            rmaTicket.setCode(RMA_CODE_PREFIX + timeCode);
        }
    }

    private void processReplacementDevice(RmaTicket ticket, String cleanNewSerial) {
        // Lớp chặn an toàn: Ngăn chặn việc nhập trùng mã Serial đã có trên hệ thống
        if (productItemRepository.existsBySerialNumber(cleanNewSerial)) {
            throw new IllegalArgumentException(
                    "Lỗi: Mã Serial mới '" + cleanNewSerial + "' đã tồn tại trên hệ thống từ trước!");
        }

        ticket.setReplacementSerialNumber(cleanNewSerial);

        // Trích xuất thiết bị lỗi để lấy thông tin kế thừa cho máy đổi bảo hành
        ProductItem oldItem = productItemRepository.findBySerialNumber(ticket.getSerialNumber())
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy thiết bị lỗi gốc trong DB!"));

        ProductItem newItem = new ProductItem();
        newItem.setSerialNumber(cleanNewSerial);
        newItem.setProduct(oldItem.getProduct());
        newItem.setStatus(ItemStatus.SOLD);

        newItem.setSalesOrder(oldItem.getSalesOrder());

        productItemRepository.save(newItem);
    }
}