let currentBatchId = null;
let maxAllowed = 0;
let alreadyScanned = 0;
let tempSerials = []; // Mảng chứa mã quét tạm thời

function openBatchDetailModal(row) {
    const batchId = row.getAttribute('data-id');
    
    // Gán thông tin text cơ bản
    document.getElementById('detailBatchCode').textContent = row.getAttribute('data-code');
    document.getElementById('detailBatchProduct').textContent = row.getAttribute('data-product') || 'N/A';
    document.getElementById('detailBatchLocation').textContent = row.getAttribute('data-warehouse') + ' -> ' + row.getAttribute('data-location');
    document.getElementById('detailBatchDate').textContent = row.getAttribute('data-date');
    document.getElementById('detailBatchPrice').textContent = row.getAttribute('data-price');
    
    // Xử lý số lượng tổng và tiến độ quét
    const maxQuantity = parseInt(row.getAttribute('data-maxallowed')) || parseInt(row.getAttribute('data-quantity')) || 0;
    const scanned = parseInt(row.getAttribute('data-scanned')) || 0;
    const unscanned = maxQuantity - scanned;

    // Lấy số lượng TỒN KHO THỰC TẾ hiện tại và đẩy vào Modal
    const currentStock = parseInt(row.getAttribute('data-quantity')) || 0;
    const detailStock = document.getElementById('detailBatchStock');
    if (detailStock) {
        detailStock.innerHTML = currentStock + ' sản phẩm';
    }

    // Đổ định dạng Badge cho mã đơn PO
    const poCode = row.getAttribute('data-po');
    document.getElementById('detailBatchPo').innerHTML = poCode === 'Nhập Lẻ' 
        ? '<span class="badge bg-secondary">Nhập Kho Lẻ</span>' 
        : `<span class="badge bg-info text-dark fw-bold">${poCode}</span>`;
        
    // Hiển thị tiến độ Quét mã
    const detailQty = document.getElementById('detailBatchQty');
    if (detailQty) {
        detailQty.innerHTML = `
            <span class="text-primary fw-bold fs-5">${scanned}</span> / 
            <span class="text-secondary fw-bold fs-5">${maxQuantity}</span> 
            <span class="text-muted fw-normal">(Đã quét)</span>
            <br><small class="text-danger fw-normal">Còn lại: ${unscanned} sản phẩm chưa nạp mã</small>
        `;
    }
    // Thiết lập trạng thái xoay vòng Loading lúc đang gọi API
    const tbody = document.getElementById('detailSerialsBody');
    tbody.innerHTML = `
        <tr>
            <td colspan="3" class="text-center py-4">
                <div class="spinner-border text-primary spinner-border-sm" role="status"></div>
                <span class="ms-2 text-muted">Đang truy vấn danh sách mã...</span>
            </td>
        </tr>`;

    // Mở Popup hiển thị lên màn hình
    const detailModal = new bootstrap.Modal(document.getElementById('batchDetailModal'));
    detailModal.show();

    // Gọi API lấy danh sách Serial
    fetch(`/batches/get-serials?batchId=${batchId}`)
    .then(response => {
        if (!response.ok) throw new Error("Không thể kết nối máy chủ");
        return response.json();
    })
    .then(data => {
        if (!data || data.length === 0) {
            tbody.innerHTML = '<tr><td colspan="3" class="text-center text-muted py-4"><i class="bi bi-info-circle me-1"></i> Lô hàng này hiện tại chưa được nạp mã Serial nào.</td></tr>';
            return;
        }

        let rowsHTML = '';
        data.forEach((item, index) => {
            let statusBadge = '';
            switch (item.status) {
                case 'IN_STOCK': statusBadge = '<span class="badge bg-success-subtle text-success border border-success">Sẵn sàng trong kho</span>'; break;
                case 'SOLD': statusBadge = '<span class="badge bg-info-subtle text-info-emphasis border border-info">Đã bán</span>'; break;
                case 'DEFECTIVE': statusBadge = '<span class="badge bg-danger-subtle text-danger border border-danger">Lỗi / Hỏng</span>'; break;
                case 'RETURNED': statusBadge = '<span class="badge bg-warning-subtle text-warning-emphasis border border-warning">Đổi trả (RMA)</span>'; break;
                default: statusBadge = '<span class="badge bg-success-subtle text-success border border-success">Trong kho</span>'; break;
            }

            rowsHTML += `
                <tr class="align-middle">
                    <td class="text-center text-muted">${index + 1}</td>
                    <td class="text-start fw-bold text-primary font-monospace" style="font-size: 0.95rem;">${item.serialNumber}</td>
                    <td class="text-center">${statusBadge}</td>
                </tr>
            `;
        });
        tbody.innerHTML = rowsHTML; 
    })
    .catch(error => {
        console.error("Lỗi AJAX:", error);
        tbody.innerHTML = '<tr><td colspan="3" class="text-center text-danger py-4"><i class="bi bi-exclamation-triangle-fill me-1"></i> Lỗi nạp danh sách Serial! Vui lòng thử lại.</td></tr>';
    });
}


function openScanModal(btn) {
    currentBatchId = btn.getAttribute("data-id");
    
    // Gán dữ liệu số (Có phòng hờ null = 0)
    maxAllowed = parseInt(btn.getAttribute("data-quantity") || 0); 
    alreadyScanned = parseInt(btn.getAttribute("data-scanned") || 0);
    
    // Reset lại mảng tạm & giao diện
    tempSerials = [];
    renderScannedTable();
    document.getElementById("scanErrorMsg").classList.add("d-none");

    // Đổ Text lên giao diện
    document.getElementById("scanBatchCode").textContent = btn.getAttribute("data-code");
    document.getElementById("scanProductName").textContent = btn.getAttribute("data-product");
    updateProgressUI();

    // Mở Modal
    const modal = new bootstrap.Modal(document.getElementById('scanModal'));
    modal.show();

    // Focus vào ô input để súng quét bắn luôn
    const scanModalEl = document.getElementById('scanModal');
    scanModalEl.addEventListener('shown.bs.modal', () => {
        document.getElementById('serialInput').focus();
    }, { once: true });
}

// Xử lý thêm Serial vào mảng tạm
function addSerialManual() {
    const inputEl = document.getElementById('serialInput');
    const errorEl = document.getElementById('scanErrorMsg');
    let serial = inputEl.value.trim();

    if (!serial) return;

    // Kiểm tra giới hạn số lượng
    if (alreadyScanned + tempSerials.length >= maxAllowed) {
        showError("Lô hàng này đã nạp đủ số lượng Serial!");
        return;
    }

    // Kiểm tra trùng lặp trong đợt quét hiện tại
    if (tempSerials.includes(serial)) {
        showError("Mã này vừa mới được quét xong!");
        inputEl.select(); // Bôi đen để gõ đè
        return;
    }

    // Thêm thành công
    errorEl.classList.add("d-none");
    tempSerials.unshift(serial); // Đẩy lên đầu mảng
    inputEl.value = ""; // Xóa trắng để quét mã tiếp theo
    inputEl.focus(); // Giữ con trỏ chuột
    
    updateProgressUI();
    renderScannedTable();
}

// Báo lỗi trên UI
function showError(msg) {
    const errorEl = document.getElementById('scanErrorMsg');
    errorEl.innerHTML = `<i class="bi bi-exclamation-triangle-fill me-1"></i> ${msg}`;
    errorEl.classList.remove("d-none");
    
    // Rung ô input để cảnh báo
    const inputEl = document.getElementById('serialInput');
    inputEl.classList.add("is-invalid");
    setTimeout(() => inputEl.classList.remove("is-invalid"), 1000);
}

// Cập nhật thanh hiển thị số lượng
function updateProgressUI() {
    const totalScanned = alreadyScanned + tempSerials.length;
    document.getElementById("scanCurrentCount").textContent = totalScanned;
    document.getElementById("scanTotalCount").textContent = maxAllowed;
    document.getElementById("tempCount").textContent = tempSerials.length;
}

// Vẽ lại bảng Serial
function renderScannedTable() {
    const tbody = document.getElementById("scannedSerialsBody");
    if (tempSerials.length === 0) {
        tbody.innerHTML = '<tr id="emptyScanRow"><td colspan="3" class="text-muted py-4">Chưa có mã nào được quét đợt này.</td></tr>';
        return;
    }

    tbody.innerHTML = "";
    tempSerials.forEach((serial, index) => {
        tbody.innerHTML += `
            <tr class="align-middle">
                <td><span class="badge bg-light text-dark border">${tempSerials.length - index}</span></td>
                <td class="text-start fw-bold text-primary font-monospace">${serial}</td>
                <td>
                    <button class="btn btn-sm btn-outline-danger border-0" onclick="removeSerial(${index})">
                        <i class="bi bi-x-lg"></i>
                    </button>
                </td>
            </tr>
        `;
    });
}

// Xóa 1 mã nếu lỡ quét nhầm
function removeSerial(index) {
    tempSerials.splice(index, 1);
    updateProgressUI();
    renderScannedTable();
    document.getElementById('serialInput').focus();
}

// Gửi danh sách lên Server để Lưu (AJAX)
function submitSerials() {
    if (tempSerials.length === 0) {
        alert("Bạn chưa quét mã nào để lưu!");
        return;
    }

    const btnSave = document.getElementById("btnSaveSerials");
    btnSave.innerHTML = '<span class="spinner-border spinner-border-sm me-2"></span>Đang lưu...';
    btnSave.disabled = true;

    // khi nhấn lưu
    // gửi request AJAX POST tới controller để lưu danh sách Serial cho lô và update lại số lượng đã quét
    fetch('/batches/scan-serials', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json'
        },
        body: JSON.stringify({
            batchId: currentBatchId,
            serials: tempSerials
        })
    })
    .then(response => {
        if (!response.ok) {
            return response.text().then(text => { throw new Error(text) });
        }
        return response.json();
    })
    .then(data => {
        alert("Lưu thành công " + tempSerials.length + " mã Serial!");
        location.reload(); // Tải lại trang để cập nhật giao diện
    })
    .catch(error => {
        console.error("Lỗi:", error);
        alert("LỖI LƯU HỆ THỐNG:\n" + error.message);
        btnSave.innerHTML = '<i class="bi bi-cloud-arrow-up-fill me-1"></i> Lưu vào Hệ thống';
        btnSave.disabled = false;
    });
}
// Hàm mở modal và tự động đặt con trỏ chuột vào ô quét mã
function openPutawayModal(btn) {
    document.getElementById('putawayBatchId').value = btn.getAttribute('data-id');
    document.getElementById('putawayBatchCode').textContent = btn.getAttribute('data-code');
    document.getElementById('putawayWhName').textContent = btn.getAttribute('data-wh');
    
    document.getElementById('locationCodeInput').value = '';
    document.getElementById('putawayErrorMsg').classList.add('d-none');
    
    var modal = new bootstrap.Modal(document.getElementById('putawayModal'));
    modal.show();

    // Chờ hiệu ứng modal trượt xuống xong thì tự động focus ô input để đón súng quét
    setTimeout(() => { document.getElementById('locationCodeInput').focus(); }, 500);
}

// Bắt sự kiện súng quét tự động gửi phím Enter sau khi đọc xong mã vạch
document.getElementById('locationCodeInput')?.addEventListener('keypress', function (e) {
    if (e.key === 'Enter') {
        e.preventDefault();
        submitPutaway(); // Kích hoạt gửi dữ liệu lên server
    }
});

// Gửi request AJAX lưu vị trí kệ
function submitPutaway() {
    const batchId = document.getElementById('putawayBatchId').value;
    const locationCode = document.getElementById('locationCodeInput').value.trim();
    const errorDiv = document.getElementById('putawayErrorMsg');

    if (!locationCode) {
        errorDiv.textContent = "Vui lòng quét hoặc nhập mã vị trí kệ kho!";
        errorDiv.classList.remove('d-none');
        return;
    }

    fetch('/batches/api/putaway', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ batchId: batchId, locationCode: locationCode })
    })
    .then(async response => {
        if (!response.ok) {
            const errorMsg = await response.text();
            throw new Error(errorMsg);
        }
        // Thành công -> Tự động tải lại trang để cập nhật cột vị trí mới trên bảng dữ liệu
        window.location.reload();
    })
    .catch(error => {
        // Thất bại (VD: Quét nhầm kệ của kho khác) -> Báo lỗi đỏ, xóa text cũ để thủ kho quét lại
        errorDiv.innerHTML = `<i class="bi bi-exclamation-triangle-fill me-1"></i> ${error.message}`;
        errorDiv.classList.remove('d-none');
        document.getElementById('locationCodeInput').value = '';
        document.getElementById('locationCodeInput').focus();
    });
}

// 3. KHỞI TẠO DOM VÀ SỰ KIỆN (DOM INITIALIZATION)
document.addEventListener("DOMContentLoaded", function () {
    
    // 3.1 Kích hoạt tooltips Bootstrap
    const tooltipTriggerList = [].slice.call(document.querySelectorAll('[data-bs-toggle="tooltip"]'));
    tooltipTriggerList.map(function (tooltipTriggerEl) {
        return new bootstrap.Tooltip(tooltipTriggerEl);
    });

    // 3.2 Lắng nghe phím Enter từ súng quét mã vạch
    const serialInput = document.getElementById('serialInput');
    if (serialInput) {
        serialInput.addEventListener('keypress', function (e) {
            if (e.key === 'Enter') {
                e.preventDefault(); // Ngăn chặn reload trang khi gõ Enter trong form
                addSerialManual();
            }
        });
    }
    
});