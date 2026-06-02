package com.example.demo.service;

import com.example.demo.entity.Order.RmaTicket;
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

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RmaService {

    private final RmaTicketRepository rmaTicketRepository;
    private final ProductItemRepository productItemRepository;

    public Page<RmaTicket> getRmaTickets(String keyword, java.time.LocalDateTime startDate,
            java.time.LocalDateTime endDate, int page, int size) {
        // Sắp xếp ID giảm dần để Phiếu mới nhất hiện lên đầu
        Pageable pageable = PageRequest.of(page - 1, size, Sort.by("id").descending());

        String searchKeyword = (keyword != null && !keyword.trim().isEmpty()) ? keyword.trim() : null;
        // Gọi hàm searchWithFilters thay vì hàm cũ
        return rmaTicketRepository.searchWithFilters(searchKeyword, startDate, endDate, pageable);
    }

    public RmaTicket getRmaTicketById(Long id) {
        return rmaTicketRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy phiếu RMA mang mã ID: " + id));
    }

    @Transactional
    public void createRmaTicket(RmaTicket rmaTicket, String serialNumber) {
        // 1. Kiểm tra thiết bị có tồn tại không
        ProductItem item = productItemRepository.findBySerialNumber(serialNumber)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy thiết bị có Serial: " + serialNumber));
        if (item.getStatus() != com.example.demo.entity.Product.ItemStatus.SOLD) {
            throw new IllegalStateException("Lỗi nghiệp vụ: Chỉ thiết bị đã xuất bán mới được lập phiếu RMA!");
        }
        if (item.isWarrantyExpired()) {
            String expDateStr = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
                    .format(item.getWarrantyExpirationDate());
            throw new IllegalStateException(
                    "Từ chối tiếp nhận: Thiết bị này đã HẾT HẠN BẢO HÀNH vào ngày " + expDateStr + "!");
        }
        // 2. Tự động sinh mã phiếu (VD: RMA-20231105...)
        if (rmaTicket.getCode() == null || rmaTicket.getCode().isEmpty()) {
            String timeCode = java.time.format.DateTimeFormatter.ofPattern("yyMMddHHmmss")
                    .format(java.time.LocalDateTime.now());
            rmaTicket.setCode("RMA-" + timeCode);
        }

        // 3. Gắn thông tin cố định từ thiết bị vào phiếu
        rmaTicket.setSerialNumber(item.getSerialNumber());
        rmaTicket.setProductName(item.getProduct() != null ? item.getProduct().getName() : "Sản phẩm ẩn");
        rmaTicket.setStatus(RmaTicket.RmaStatus.PROCESSING); // Trạng thái mặc định

        // 4. Lưu phiếu RMA
        rmaTicketRepository.save(rmaTicket);

        // 5. Cập nhật trạng thái của thiết bị (ProductItem) thành Hàng Lỗi/Đổi Trả
        item.setStatus(com.example.demo.entity.Product.ItemStatus.DEFECTIVE);
        productItemRepository.save(item);
    }

    @Transactional
    public void updateRmaStatus(Long id, RmaTicket.RmaStatus newStatus, String solution) {
        RmaTicket ticket = rmaTicketRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy phiếu RMA mang mã ID: " + id));

        ticket.setStatus(newStatus);

        // Cập nhật thêm phương án xử lý cuối cùng nếu Kỹ thuật viên có gõ vào
        if (solution != null && !solution.trim().isEmpty()) {
            ticket.setWarrantySolution(solution);
        }
        rmaTicketRepository.save(ticket);

        // Trả lại trạng thái máy cho khách (Dù sửa thành công hay từ chối thì máy cũng
        // được trả về tay khách)
        ProductItem item = productItemRepository.findBySerialNumber(ticket.getSerialNumber()).orElse(null);
        if (item != null) {
            item.setStatus(com.example.demo.entity.Product.ItemStatus.SOLD);
            productItemRepository.save(item);
        }
    }
}