
function openSalesDetailModal(row) {
    const soId = row.getAttribute('data-id');

    // 1. Đổ dữ liệu các trường thông tin chung lên Modal
    document.getElementById('detailSoCode').textContent = row.getAttribute('data-code');
    document.getElementById('detailSoCustomer').textContent = row.getAttribute('data-customer');
    document.getElementById('detailSoWarehouse').textContent = row.getAttribute('data-warehouse');
    document.getElementById('detailSoCreated').textContent = row.getAttribute('data-created');
    document.getElementById('detailSoNote').textContent = row.getAttribute('data-note') || 'Không có ghi chú';

    // dia chi giao hang
    document.getElementById('detailSoDestination').textContent = row.getAttribute('data-destination');

    // Xử lý tiền tệ
    let amount = parseFloat(row.getAttribute('data-amount') || 0);
    let paid = parseFloat(row.getAttribute('data-paid') || 0);
    
    // Tính toán số tiền còn nợ
    let debt = amount - paid;
    if (debt < 0) debt = 0; // Đảm bảo nợ không bị âm nếu khách trả dư
    
    document.getElementById('detailSoAmount').textContent = amount.toLocaleString('vi-VN') + ' đ';
    document.getElementById('detailSoPaidAmount').textContent = paid.toLocaleString('vi-VN') + ' đ';
    document.getElementById('detailSoDebt').textContent = debt.toLocaleString('vi-VN') + ' đ';

    // Xử lý Badge Thanh toán
    let paymentStatus = row.getAttribute('data-payment');
    let paymentHtml = '';
    if (paymentStatus === 'PAID') paymentHtml = '<span class="badge bg-success-subtle text-success border border-success">Đã thanh toán đủ</span>';
    else if (paymentStatus === 'PARTIAL') paymentHtml = '<span class="badge bg-warning-subtle text-warning border border-warning">Thanh toán 1 phần</span>';
    else paymentHtml = '<span class="badge bg-danger-subtle text-danger border border-danger">Chưa thanh toán</span>';
    document.getElementById('detailSoPayment').innerHTML = paymentHtml;

    // Xử lý Badge Giao hàng
    let salesStatus = row.getAttribute('data-status');
    let statusHtml = '';
    switch (salesStatus) {
        case 'COMPLETED': statusHtml = '<span class="badge bg-success">Hoàn tất</span>'; break;
        case 'CANCELLED': statusHtml = '<span class="badge bg-danger">Đã hủy</span>'; break;
        case 'REFUNDED':  statusHtml = '<span class="badge bg-warning text-dark">Hoàn tiền</span>'; break;
        default: statusHtml = `<span class="badge bg-secondary">${salesStatus || 'N/A'}</span>`;
    }
    document.getElementById('detailSoStatus').innerHTML = statusHtml;

    // 2. Hiệu ứng Loading cho bảng chi tiết sản phẩm
    let tbody = document.getElementById('detailSoProductsBody');
    tbody.innerHTML = `
        <tr>
            <td colspan="8" class="text-center py-4">
                <div class="spinner-border text-primary" role="status"></div>
                <div class="mt-2 text-muted">Đang tải danh sách sản phẩm...</div>
            </td>
        </tr>`;

    // Mở Modal
    var myModal = new bootstrap.Modal(document.getElementById('salesDetailModal'));
    myModal.show();

    // 3. Gửi request GET tới Controller để lấy chi tiết
    fetch(`/sales/get-order-details?soId=${soId}`, {
        method: 'GET'
    })
    .then(response => {
        if (!response.ok) throw new Error("Lỗi kết nối từ server");
        return response.json();
    })
    .then(data => {
        tbody.innerHTML = '';

        if (data.length === 0) {
            tbody.innerHTML = '<tr><td colspan="8" class="text-center text-muted py-4">Không có sản phẩm nào.</td></tr>';
            return;
        }

        // Render dữ liệu
        data.forEach((item, index) => {
            let total = (item.price * item.quantity) - item.discount;
            
            let pName = item.productName || (item.product ? item.product.name : 'Sản phẩm không xác định');
            
            let pSku = item.skuCode || item.sku || (item.product ? (item.product.skuCode || item.product.sku) : 'N/A');
            
            let pUnit = item.unit || (item.product ? item.product.unit : '');
            
            let warrantyText = (item.warrantyMonths && item.warrantyMonths > 0) 
                            ? `<span class="badge bg-info text-dark">${item.warrantyMonths} tháng</span>` 
                            : `<span class="text-muted small">Không BH</span>`;

            let tr = `
                <tr class="text-center">
                    <td>${index + 1}</td>
                    <td class="text-start fw-semibold text-primary">${pName}</td>
                    <td><code>${pSku}</code></td>
                    <td>${pUnit}</td>
                    <td>${warrantyText}</td>
                    <td class="text-end">${item.price.toLocaleString('vi-VN')} đ</td>
                    <td class="fw-bold">${item.quantity}</td>
                    <td class="text-end text-danger">-${item.discount.toLocaleString('vi-VN')} đ</td>
                    <td class="text-end fw-bold text-success">${total.toLocaleString('vi-VN')} đ</td>
                </tr>
            `;
            tbody.innerHTML += tr;
        });
    })
    .catch(error => {
        console.error("Lỗi GET Fetch:", error);
        tbody.innerHTML = `
            <tr>
                <td colspan="8" class="text-center text-danger py-4">
                    <i class="bi bi-exclamation-triangle-fill me-2"></i> Không thể tải dữ liệu chi tiết sản phẩm.
                </td>
            </tr>`;
    });
}

function openPaymentModal(btn) {
    const id = btn.getAttribute('data-id');
    const code = btn.getAttribute('data-code');
    const missingAmount = parseFloat(btn.getAttribute('data-missing'));

    document.getElementById('payOrderId').value = id;
    document.getElementById('paySoCode').textContent = code;
    document.getElementById('payMissingAmount').textContent = missingAmount.toLocaleString('vi-VN') + ' đ';
    
    // Gợi ý luôn số tiền còn thiếu vào ô input
    document.getElementById('payInputAmount').value = missingAmount;
    document.getElementById('payError').classList.add('d-none');

    var payModal = new bootstrap.Modal(document.getElementById('paymentModal'));
    payModal.show();
}

function submitPayment() {
    const orderId = document.getElementById('payOrderId').value;
    const amount = document.getElementById('payInputAmount').value;
    const errorDiv = document.getElementById('payError');

    if (!amount || amount <= 0) {
        errorDiv.textContent = "Vui lòng nhập số tiền hợp lệ (> 0)!";
        errorDiv.classList.remove('d-none');
        return;
    }

    // Gọi API thu tiền bằng phương thức POST
    fetch('/sales/api/pay', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ orderId: orderId, amount: amount })
    })
    .then(async response => {
        if (!response.ok) {
            const err = await response.text();
            throw new Error(err);
        }
        // Thu thành công thì tải lại trang để cập nhật Badge trạng thái
        window.location.reload(); 
    })
    .catch(error => {
        errorDiv.innerHTML = `<i class="bi bi-exclamation-triangle-fill me-1"></i> ${error.message}`;
        errorDiv.classList.remove('d-none');
    });
}