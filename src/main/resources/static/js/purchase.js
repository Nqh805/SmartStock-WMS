// Hàm mở Modal Chi tiết Đơn hàng (Đã loại bỏ edit link, thêm trường hiển thị kho)
function openPurchaseDetailModal(row) {
    const poId = row.getAttribute('data-id');

    // 1. Đổ dữ liệu các trường thông tin chung lên Header Modal
    document.getElementById('detailPoCode').textContent = row.getAttribute('data-code');
    document.getElementById('detailPoName').textContent = row.getAttribute('data-name');
    document.getElementById('detailPoCreator').textContent = row.getAttribute('data-creator') || 'Chưa cập nhật';
    document.getElementById('detailPoSupplier').textContent = row.getAttribute('data-supplier');
    document.getElementById('detailPoWarehouse').textContent = row.getAttribute('data-warehouse');
    document.getElementById('detailPoWarehouseAddress').textContent = row.getAttribute('data-warehouse-address') || 'Chưa cập nhật';
    document.getElementById('detailPoCreated').textContent = row.getAttribute('data-created');
    document.getElementById('detailPoExpected').textContent = row.getAttribute('data-expected');
    document.getElementById('detailPoNote').textContent = row.getAttribute('data-note') || 'Không có ghi chú';
    document.getElementById('detailPoReceivedNote').textContent = row.getAttribute('data-received-note') || 'Chưa có ghi chú nhận hàng';
    document.getElementById('detailPoActualArrival').textContent = row.getAttribute('data-actual') || 'Chưa nhận';
    
    let result = row.getAttribute('data-result');
    let resultHtml = '<span class="text-muted">Chưa kiểm đếm</span>';
    
    // Bổ sung thêm điều kiện ON_TIME
    if (result === 'ON_TIME') {
        resultHtml = '<span class="badge bg-success">Giao đúng hạn</span>';
    } else if (result === 'EARLY') {
        resultHtml = '<span class="badge bg-info">Giao sớm</span>';
    } else if (result === 'LATE') {
        resultHtml = '<span class="badge bg-warning text-dark">Giao trễ</span>';
    }
    
    document.getElementById('detailPoResult').innerHTML = resultHtml;
    let amount = parseFloat(row.getAttribute('data-amount') || 0);
    document.getElementById('detailPoAmount').textContent = amount.toLocaleString('vi-VN') + ' đ';

    let paid = parseFloat(row.getAttribute('data-paid') || 0);
    let remaining = parseFloat(row.getAttribute('data-remaining') || 0);
    const paidAmountEl = document.getElementById('detailPoPaidAmount');
    const remainingAmountEl = document.getElementById('detailPoRemainingAmount');
    if (paidAmountEl) {
        paidAmountEl.textContent = paid.toLocaleString('vi-VN') + ' đ';
    }
    if (remainingAmountEl) {
        remainingAmountEl.textContent = remaining.toLocaleString('vi-VN') + ' đ';
    }

    // ĐỌC VÀ XỬ LÝ ĐƯỜNG DẪN ẢNH CHỨNG TỪ GIAO HÀNG
    const documentUrl = row.getAttribute('data-document');
    const docLink = document.getElementById('detailPoDocumentLink');
    const noDocSpan = document.getElementById('detailPoNoDocument');

    if (docLink && noDocSpan) {
        // Kiểm tra xem đơn hàng đã được up ảnh chứng từ hay chưa
        if (documentUrl && documentUrl !== 'null' && documentUrl.trim() !== '') {
            docLink.href = documentUrl; 
            docLink.style.display = 'inline-block';
            noDocSpan.style.display = 'none'
        } else {
            docLink.style.display = 'none'; 
            noDocSpan.style.display = 'inline';   
        }
    }

    let paymentStatus = row.getAttribute('data-payment');
    document.getElementById('detailPoPayment').innerHTML = paymentStatus === 'PAID' 
        ? '<span class="badge bg-success-subtle text-success border border-success">Đã thanh toán</span>' 
        : paymentStatus === 'PARTIAL'
        ? '<span class="badge bg-warning-subtle text-warning border border-warning">Thanh toán một phần</span>'
        : '<span class="badge bg-danger-subtle text-danger border border-danger">Chưa thanh toán</span>';

    let deliveryStatus = row.getAttribute('data-delivery');
    let deliveryHtml = '';
    switch (deliveryStatus) {
        case 'PENDING': deliveryHtml = '<span class="badge bg-secondary">Chờ xử lý</span>'; break;
        case 'IN_TRANSIT': deliveryHtml = '<span class="badge bg-primary">Đang giao</span>'; break;
        case 'COMPLETED': deliveryHtml = '<span class="badge bg-success">Đã nhận hàng</span>'; break;
        case 'CANCELLED': deliveryHtml = '<span class="badge bg-danger">Đã hủy</span>'; break; 
    }
    document.getElementById('detailPoDelivery').innerHTML = deliveryHtml;

    const paymentBtn = document.getElementById('detailPaymentBtn');
    if (paymentBtn) {
        // Gắn thêm dữ liệu vào nút thanh toán
        paymentBtn.setAttribute('data-id', poId);
        paymentBtn.setAttribute('data-code', row.getAttribute('data-code'));
        paymentBtn.setAttribute('data-delivery', deliveryStatus);
        paymentBtn.setAttribute('data-total', row.getAttribute('data-amount'));
        paymentBtn.setAttribute('data-paid', row.getAttribute('data-paid'));
        paymentBtn.setAttribute('data-remaining', row.getAttribute('data-remaining'));

        if (paymentStatus === 'PAID') {
            paymentBtn.disabled = true;
            paymentBtn.classList.replace('btn-success', 'btn-outline-success');
            paymentBtn.innerHTML = '<i class="bi bi-check2-circle me-1"></i>Đã thanh toán đủ';
        } else {
            paymentBtn.disabled = false;
            paymentBtn.classList.replace('btn-outline-success', 'btn-success');
            paymentBtn.innerHTML = '<i class="bi bi-cash-coin me-1"></i>Thanh toán';
        }
    }

    // hiệu ứng Loading trong bảng khi đợi nạp dữ liệu
    let tbody = document.getElementById('detailPoProductsBody');
    tbody.innerHTML = `
        <tr>
            <td colspan="12" class="text-center py-4">
                <div class="spinner-border text-primary" role="status"></div>
                <div class="mt-2 text-muted">Đang tải danh sách sản phẩm thực tế...</div>
            </td>
        </tr>`;

    // kích hoạt modal
    var myModal = new bootstrap.Modal(document.getElementById('purchaseDetailModal'));
    myModal.show();

    // gửi request lấy chi tiết danh sách sản phẩm
    const params = new URLSearchParams();
    params.append('poId', poId);

    fetch(`/purchases/get-order-details?poId=${poId}`, {
        method: 'GET'
    })
    .then(response => {
        if (!response.ok) throw new Error("Lỗi kết nối từ server");
        return response.json();
    })
    .then(data => {
        tbody.innerHTML = '';

        const putawayBadge = document.getElementById('detailPoPutawayProgress');

        if (data.length === 0) {
            tbody.innerHTML = '<tr><td colspan="12" class="text-center text-muted py-4">Không có sản phẩm nào thuộc đơn hàng này.</td></tr>';
            if (putawayBadge) putawayBadge.innerHTML = '<i class="bi bi-layers me-1"></i>Tiến độ xếp kệ: 0/0 lô';
            return;
        }

        let totalBatches = 0;
        let putawayBatches = 0;

        // Duyệt danh sách DTO lồng nhau (Nested DTO) nhận được từ Java và render
        data.forEach((item, index) => {
            // THỐNG KÊ LÔ HÀNG ĐÃ CẤT
            // Chỉ những sản phẩm có nhận hàng (actualQuantity > 0) mới sinh ra Lô
            if (item.actualQuantity > 0) {
                totalBatches++;
                // Nếu số lượng cất vào kệ = số lượng thực nhận -> Đã cất xong lô này
                if (item.putawayQuantity === item.actualQuantity) {
                    putawayBatches++;
                }
            }

            let qtyToCalculate = item.actualQuantity > 0 ? item.actualQuantity : item.quantity;
            let total = (item.price * qtyToCalculate) - item.discount;
            let tooltipText = item.actualQuantity > 0 ? 'Thành tiền (Thực tế)' : 'Tạm tính (Dựa trên SL đặt)';

            let tr = `
                <tr class="text-center">
                    <td>${index + 1}</td>
                    <td class="text-start fw-semibold text-primary">${item.product.name || 'N/A'}</td>
                    <td>${item.product.brand || ''}</td>
                    <td><code>${item.product.sku || ''}</code></td>
                    <td>${item.product.barcode || ''}</td>
                    <td class="fw-medium text-secondary">${item.warehouse || ''}</td>
                    <td>${item.product.unit || ''}</td>
                    <td class="text-end">${item.price.toLocaleString('vi-VN')} đ</td>
                    <td>${item.quantity}</td>
                    <td class="text-primary fw-bold">${item.actualQuantity}</td>
                    <td class="text-end text-danger">-${item.discount.toLocaleString('vi-VN')} đ</td>
                    <td class="text-end fw-bold text-success" title="${tooltipText}">
                        ${total.toLocaleString('vi-VN')} đ
                    </td>
                </tr>
            `;
            tbody.innerHTML += tr;
        });

        // CẬP NHẬT BADGE TIẾN ĐỘ LÊN GIAO DIỆN
        if (putawayBadge) {
            putawayBadge.innerHTML = `<i class="bi bi-layers me-1"></i>Đã xếp kệ: ${putawayBatches} / ${totalBatches} lô`;
            if (totalBatches === 0 || putawayBatches === 0) {
                 putawayBadge.className = 'badge bg-secondary border border-secondary';
            } else if (putawayBatches < totalBatches) {
                 putawayBadge.className = 'badge bg-warning text-dark border border-warning';
            } else {
                 putawayBadge.className = 'badge bg-success border border-success';
            }
        }
    })
    .catch(error => {
        console.error("Lỗi Ajax Fetch:", error);
        tbody.innerHTML = `
            <tr>
                <td colspan="12" class="text-center text-danger py-4">
                    <i class="bi bi-exclamation-triangle-fill me-2"></i> Không thể tải dữ liệu chi tiết sản phẩm. Vui lòng thử lại.
                </td>
            </tr>`;
    });
    
    // payment history start
    let historyBody = document.getElementById('detailPoPaymentHistoryBody');
    
    // Hiển thị loading mờ mờ cho bảng lịch sử
    historyBody.innerHTML = '<tr><td colspan="6" class="text-center text-muted py-3"><div class="spinner-border spinner-border-sm text-secondary me-2"></div>Đang nạp lịch sử...</td></tr>';

    // Gọi API bằng phương thức GET
    fetch(`/purchases/get-payment-history?poId=${poId}`)
    .then(response => {
        if (!response.ok) throw new Error("Lỗi tải lịch sử");
        return response.json();
    })
    .then(data => {
        historyBody.innerHTML = ''; // Xóa loading

        if (data.length === 0) {
            historyBody.innerHTML = '<tr><td colspan="6" class="text-muted py-3 text-center">Chưa có giao dịch thanh toán nào.</td></tr>';
            return;
        }

        // bảng lịch sử giao dịch
        data.forEach(item => {
            // 1. Format lại ngày tháng từ chuỗi Java (VD: 2026-05-18T10:30:00)
            let dateObj = new Date(item.paymentDate);
            let dateStr = dateObj.toLocaleDateString('vi-VN') + ' ' + dateObj.toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit', second: '2-digit' });

            // 2. Format Badge Phương thức
            let methodBadge = '';
            if (item.paymentMethod === 'CASH') methodBadge = '<span class="badge bg-secondary">Tiền mặt</span>';
            else if (item.paymentMethod === 'TRANSFER') methodBadge = '<span class="badge bg-primary">Chuyển khoản</span>';
            else if (item.paymentMethod === 'CREDIT_CARD') methodBadge = '<span class="badge bg-info text-dark">Thẻ tín dụng</span>';

            // 3. Format Nút xem ảnh chứng từ (Nếu có ảnh thì hiện nút, không thì gạch ngang)
            let receiptHtml = item.receiptImageUrl 
                ? `<a href="${item.receiptImageUrl}" target="_blank" class="btn btn-sm btn-outline-primary" title="Xem chứng từ"><i class="bi bi-eye"></i></a>`
                : '<span class="text-muted">-</span>';

            let tr = `
                <tr class="align-middle text-center">
                    <td class="text-muted">${dateStr}</td>
                    <td class=" fw-bold text-success">${item.amount.toLocaleString('vi-VN')} đ</td>
                    <td>${methodBadge}</td>
                    <td><code>${item.referenceCode || '-'}</code></td>
                    <td class="text-start text-wrap" style="max-width: 200px;">${item.note || '-'}</td>
                    <td>${receiptHtml}</td>
                </tr>
            `;
            historyBody.innerHTML += tr;
        });
    })
    .catch(error => {
        console.error("Lỗi Fetch Lịch sử:", error);
        historyBody.innerHTML = '<tr><td colspan="6" class="text-danger py-3 text-center"><i class="bi bi-exclamation-triangle me-1"></i> Không thể tải dữ liệu.</td></tr>';
    });
    // payment history end
}
// Popup Modal nhan hang
function openReceiptModal(row) {
    const poId = row.getAttribute('data-id');
    const poCode = row.getAttribute('data-code');
    const poSupplier = row.getAttribute('data-supplier');
    const poWarehouse = row.getAttribute('data-warehouse');

    //làm mờ các ngày dã tạo và ngày dự kiến
    const createdDateStr = row.getAttribute('data-created'); // Ví dụ: "15/05/2026 16:48:43"
    
    if (createdDateStr) {
        // Chuyển đổi định dạng "dd/MM/yyyy HH:mm:ss" sang "yyyy-MM-dd" để gán vào thuộc tính 'min' của thẻ input date
        const parts = createdDateStr.split(' ')[0].split('/');
        const yyyyMmDd = `${parts[2]}-${parts[1]}-${parts[0]}`; // Convert thành "2026-05-15"
        
        // Tìm ô input date trong Modal nhận hàng và thiết lập thuộc tính min
        const arrivalInput = document.querySelector('#receiptModal input[name="actualArrival"]');
        if (arrivalInput) {
            arrivalInput.min = yyyyMmDd;
        }
    }

    // 1. Đổ thông tin chung của đơn vào Header Modal nhận hàng
    document.getElementById('receiptPoCode').textContent = poCode;
    document.getElementById('receiptPoSupplier').textContent = poSupplier;
    document.getElementById('receiptPoWarehouse').textContent = poWarehouse;

    // Cấu hình Action Form để submit đúng ID đơn hàng về Controller xử lý nhận hàng
    document.getElementById('receiptForm').action = '/purchases/receive/' + poId;

    // 2. Tạo hiệu ứng xoay Loading trong bảng khi đợi nạp dữ liệu
    let tbody = document.getElementById('receiptProductsBody');
    tbody.innerHTML = `
        <tr>
            <td colspan="6" class="text-center py-4">
                <div class="spinner-border text-warning" role="status"></div>
                <div class="mt-2 text-muted">Đang tải danh sách sản phẩm để nhận hàng...</div>
            </td>
        </tr>`;

    // 3. Mở Modal Nhận hàng lên trước để tăng trải nghiệm UX
    var rModal = new bootstrap.Modal(document.getElementById('receiptModal'));
    rModal.show();

    // 4. Gọi API lấy chi tiết đơn hiển thị danh sách sản phẩm
    fetch(`/purchases/get-order-details?poId=${poId}`, {
        method: 'GET'
    })
    .then(response => {
        if (!response.ok) throw new Error("Lỗi kết nối từ server");
        return response.json();
    })
    .then(data => {
        tbody.innerHTML = ''; // Clear dòng loading đi

        if (data.length === 0) {
            tbody.innerHTML = '<tr><td colspan="6" class="text-center text-muted py-4">Không có sản phẩm nào thuộc đơn hàng này.</td></tr>';
            return;
        }

        // 5. Duyệt danh sách render Input để thủ kho điền số lượng
        data.forEach((item, index) => {
            // Lưu ý: name="actualQuantities[${item.id}]" dùng item.id (ID của chi tiết đơn) để Spring Boot map vào Map/List ở Controller
            let tr = `
                <tr class="text-center">
                    <td>${index + 1}</td>
                    <td class="text-start fw-semibold text-dark">${item.product.name || 'N/A'}</td>
                    <td><code>${item.product.sku || ''}</code></td>
                    <td>${item.product.unit || ''}</td>
                    <td class="fw-bold fs-6 text-secondary">${item.quantity}</td>
                    <td class="bg-warning-subtle">
                        <input type="number" 
                               name="actualQuantities[${item.id}]" 
                               class="form-control form-control-sm text-center fw-semibold border-warning" 
                               min="1" 
                               max="${item.quantity}" 
                               value="${item.quantity}" 
                               required />
                    </td>
                </tr>
            `;
            tbody.innerHTML += tr;
        });
    })
    .catch(error => {
        console.error("Lỗi Ajax Fetch (Receipt):", error);
        tbody.innerHTML = `
            <tr>
                <td colspan="6" class="text-center text-danger py-4">
                    <i class="bi bi-exclamation-triangle-fill me-2"></i> Không thể tải dữ liệu sản phẩm. Vui lòng thử lại.
                </td>
            </tr>`;
    });
}
// Hàm xử lý nút Thanh toán
function handlePaymentClick(btn) {
    const deliveryStatus = btn.getAttribute('data-delivery');

    // Vẫn giữ logic chặn nếu chưa nhận hàng
    if (deliveryStatus === 'PENDING' || deliveryStatus === 'IN_TRANSIT') {
        alert('CẢNH BÁO: Không thể thanh toán!\n\nĐơn hàng này chưa được nhận thực tế tại kho.');
        return; 
    }
    else if (deliveryStatus === 'CANCELLED') {
        alert('CẢNH BÁO: Không thể thanh toán!\n\nĐơn hàng này đã bị hủy. Vui lòng kiểm tra lại thông tin đơn hàng.');
        return; 
    }

    // 1. Ẩn modal chi tiết đi
    var detailModal = bootstrap.Modal.getInstance(document.getElementById('purchaseDetailModal'));
    if (detailModal) detailModal.hide();

    // 2. Lấy dữ liệu công nợ
    const poId = btn.getAttribute('data-id');
    const total = parseFloat(btn.getAttribute('data-total') || 0);
    const paid = parseFloat(btn.getAttribute('data-paid') || 0);
    const remaining = parseFloat(btn.getAttribute('data-remaining') || 0);

    // 3. Đổ dữ liệu lên Modal Thanh toán
    document.getElementById('payPoCode').textContent = btn.getAttribute('data-code');
    document.getElementById('payTotalAmount').textContent = total.toLocaleString('vi-VN') + ' đ';
    document.getElementById('payPaidAmount').textContent = paid.toLocaleString('vi-VN') + ' đ';
    document.getElementById('payRemainingAmount').textContent = remaining.toLocaleString('vi-VN') + ' đ';
    
    // Mặc định điền số tiền cần thanh toán là số tiền còn nợ
    let inputAmount = document.getElementById('payInputAmount');
    inputAmount.value = remaining;
    inputAmount.max = remaining; // Không cho phép trả dư tiền

    // Cấu hình Form Submit
    document.getElementById('paymentForm').action = '/purchases/pay/' + poId;

    // 4. Hiển thị Modal Thanh toán
    var payModal = new bootstrap.Modal(document.getElementById('paymentModal'));
    payModal.show();
}

// Hàm xử lý nút Xóa
function handleDeleteClick(btn) {
    const deliveryStatus = btn.getAttribute('data-delivery');
    const poId = btn.getAttribute('data-id');

    // Chặn nếu đơn đã bắt đầu quá trình giao nhận
    if (deliveryStatus !== 'PENDING') {
        alert('CẢNH BÁO: Không thể xóa đơn hàng!\n\nĐơn hàng này đang đi đường hoặc đã nhập kho. Chỉ được phép xóa những đơn hàng MỚI TẠO (Đang chờ xử lý).');
        return; // Dừng thực thi
    }

    // Nếu hợp lệ (PENDING) thì mới mở Modal Xác nhận Xóa
    const form = document.getElementById("deleteForm");
    if (form) {
        form.action = "/purchases/delete/" + poId;
        var myModal = new bootstrap.Modal(document.getElementById('deleteModal'));
        myModal.show();
    }
}
// báo fail đơn hàng
function openFailModal(row) {
    // Lấy thông tin từ dòng được click tải vào modal
    const id = row.getAttribute('data-id');
    const code = row.getAttribute('data-code');
    
    document.getElementById('failPoCode').textContent = code;
    
    document.getElementById('failForm').action = '/purchases/fail/' + id;
    
    document.querySelector('#failForm textarea[name="failReason"]').value = '';
    
    const modal = new bootstrap.Modal(document.getElementById('failModal'));
    modal.show();
}
// Biến cờ đánh dấu xem có đơn nào được duyệt chưa để load lại bảng chính
let hasApprovedAnyOrder = false;

// Hàm AJAX duyệt đơn hàng
function approveOrderAjax(poId) {
    if (!confirm("Bạn có chắc chắn muốn duyệt đơn hàng này? Hàng sẽ được chuyển sang trạng thái Đang giao tới kho.")) {
        return;
    }

    fetch(`/purchases/approve/${poId}`, {
        method: 'POST'
    })
    .then(response => {
        if (!response.ok) throw new Error("Duyệt đơn thất bại!");
        return response.text();
    })
    .then(msg => {
        // 1. Xóa dòng chứa đơn hàng vừa duyệt khỏi Modal bằng hiệu ứng mờ dần
        const row = document.getElementById(`pending-row-${poId}`);
        if (row) {
            row.style.transition = "opacity 0.3s ease";
            row.style.opacity = 0;
            setTimeout(() => row.remove(), 300);
        }

        // 2. Cập nhật lại số đếm trên Badge (nút vàng ngoài màn hình)
        const badge = document.getElementById('pendingBadge');
        if (badge) {
            let currentCount = parseInt(badge.textContent);
            if (currentCount > 1) {
                badge.textContent = currentCount - 1;
            } else {
                // Nếu = 0 thì ẩn badge đi và show chữ "Không còn đơn" trong bảng
                badge.remove();
                document.getElementById('pendingOrdersBody').innerHTML = `
                  <tr id="noPendingRow">
                    <td colspan="4" class="text-muted py-5">
                      <i class="bi bi-emoji-smile fs-2 d-block mb-2"></i>Tuyệt vời! Không còn đơn hàng nào chờ duyệt.
                    </td>
                  </tr>`;
            }
        }

        // Đánh dấu cờ là đã có thay đổi dữ liệu
        hasApprovedAnyOrder = true;
    })
    .catch(error => {
        alert(error);
    });
}

// Hàm AJAX từ chối duyệt đơn hàng
function rejectOrderAjax(poId) {
    if (!confirm("Bạn có chắc chắn muốn TỪ CHỐI đơn hàng này? Hệ thống sẽ chuyển trạng thái đơn thành ĐÃ HỦY.")) {
        return;
    }

    fetch(`/purchases/reject/${poId}`, {
        method: 'POST'
    })
    .then(response => {
        if (!response.ok) throw new Error("Từ chối đơn thất bại!");
        return response.text();
    })
    .then(msg => {
        // 1. Xóa dòng chứa đơn hàng vừa từ chối khỏi Modal
        const row = document.getElementById(`pending-row-${poId}`);
        if (row) {
            row.style.transition = "opacity 0.3s ease";
            row.style.opacity = 0;
            setTimeout(() => row.remove(), 300);
        }

        // 2. Cập nhật lại số đếm trên Badge (nút màu vàng ngoài màn hình)
        const badge = document.getElementById('pendingBadge');
        if (badge) {
            let currentCount = parseInt(badge.textContent);
            if (currentCount > 1) {
                badge.textContent = currentCount - 1;
            } else {
                badge.remove();
                document.getElementById('pendingOrdersBody').innerHTML = `
                  <tr id="noPendingRow">
                    <td colspan="4" class="text-muted py-5">
                      <i class="bi bi-emoji-smile fs-2 d-block mb-2"></i>Tuyệt vời! Không còn đơn hàng nào chờ duyệt.
                    </td>
                  </tr>`;
            }
        }

        // 3. Đánh dấu cờ để load lại bảng dữ liệu chính khi đóng modal
        hasApprovedAnyOrder = true; 
    })
    .catch(error => {
        alert(error);
    });
}
// Khởi tạo tất cả các thành phần DOM khi load trang hoàn tất
document.addEventListener("DOMContentLoaded", function () {
    
    const tooltipTriggerList = document.querySelectorAll('[data-bs-toggle="tooltip"]');
    [...tooltipTriggerList].map((tooltipTriggerEl) => new bootstrap.Tooltip(tooltipTriggerEl));

    // 2. Lắng nghe sự kiện mở Modal Xóa -> Truyền ID vào form xác nhận xóa
    const deleteModal = document.getElementById("deleteModal");
    if (deleteModal) {
        deleteModal.addEventListener("show.bs.modal", (event) => {
            const button = event.relatedTarget;
            const id = button.getAttribute("data-id");
            const form = document.getElementById("deleteForm");
            if (form && id) {
                form.action = "/purchases/delete/" + id;
            }
        });
    }

    // 3. Lắng nghe sự kiện khi đóng Modal Duyệt Đơn
    const approvalModalEl = document.getElementById('approvalModal');
    if (approvalModalEl) {
        approvalModalEl.addEventListener('hidden.bs.modal', function () {
            if (hasApprovedAnyOrder) {
                location.reload();
            }
        });
    }
    
});