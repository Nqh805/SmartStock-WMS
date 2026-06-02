
function openQuickAddCustomerModal() {
    // 1. Tạo HTML Modal bằng JS để không làm rối file add_sales.html
    if (!document.getElementById('quickAddCustomerModal')) {
        const modalHtml = `
        <div class="modal fade" id="quickAddCustomerModal" tabindex="-1" aria-hidden="true">
          <div class="modal-dialog">
            <div class="modal-content border-0 shadow-lg">
              <div class="modal-header bg-success text-white">
                <h5 class="modal-title fw-bold"><i class="bi bi-person-plus-fill me-2"></i>Thêm Khách Hàng Nhanh</h5>
                <button type="button" class="btn-close btn-close-white" data-bs-dismiss="modal"></button>
              </div>
              <div class="modal-body">
                <div id="qaCustomerError" class="alert alert-danger d-none" style="font-size: 0.9rem;"></div>
                <form id="qaCustomerForm">
                  <div class="mb-3">
                    <label class="form-label fw-semibold">Tên khách hàng <span class="text-danger">*</span></label>
                    <input type="text" class="form-control" id="qaName" placeholder="Nhập tên..." required />
                  </div>
                  <div class="row mb-3">
                    <div class="col-6">
                      <label class="form-label fw-semibold">Số điện thoại <span class="text-danger">*</span></label>
                      <input type="text" class="form-control" id="qaPhone" placeholder="09..." required />
                    </div>
                    <div class="col-6">
                      <label class="form-label fw-semibold">Phân loại <span class="text-danger">*</span></label>
                      <select class="form-select" id="qaType">
                        <option value="INDIVIDUAL">Khách lẻ</option>
                        <option value="BUSINESS">Doanh nghiệp / Đại lý</option>
                      </select>
                    </div>
                  </div>
                  <div class="mb-3">
                    <label class="form-label fw-semibold">Email</label>
                    <input type="email" class="form-control" id="qaEmail" placeholder="ví dụ@gmail.com" />
                  </div>
                  <div class="mb-3">
                    <label class="form-label fw-semibold">Địa chỉ</label>
                    <input type="text" class="form-control" id="qaAddress" placeholder="Nhập địa chỉ..." />
                  </div>
                </form>
              </div>
              <div class="modal-footer bg-light">
                <button type="button" class="btn btn-secondary" data-bs-dismiss="modal">Hủy</button>
                <button type="button" class="btn btn-success fw-bold" onclick="submitQuickAddCustomer()">
                  <i class="bi bi-save me-1"></i> Lưu thông tin
                </button>
              </div>
            </div>
          </div>
        </div>`;
        document.body.insertAdjacentHTML('beforeend', modalHtml);
    }

    // 2. Reset form sạch sẽ trước khi mở
    document.getElementById('qaCustomerForm').reset();
    document.getElementById('qaCustomerError').classList.add('d-none');

    // 3. Hiển thị Modal
    const modal = new bootstrap.Modal(document.getElementById('quickAddCustomerModal'));
    modal.show();
}

function submitQuickAddCustomer() {
    const name = document.getElementById('qaName').value.trim();
    const phone = document.getElementById('qaPhone').value.trim();
    const type = document.getElementById('qaType').value;
    const email = document.getElementById('qaEmail').value.trim();
    const address = document.getElementById('qaAddress').value.trim();
    const errorDiv = document.getElementById('qaCustomerError');

    if (!name || !phone) {
        errorDiv.textContent = "Vui lòng nhập Tên và Số điện thoại!";
        errorDiv.classList.remove('d-none');
        return;
    }

    const customerData = { name, phone, type, email, address };

    fetch('/customers/api/add', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(customerData)
    })
    .then(async response => {
        if (!response.ok) {
            const errorMsg = await response.text();
            throw new Error(errorMsg || "Lỗi từ Server");
        }
        return response.json();
    })
    .then(savedCustomer => {
        // 1. Đóng Modal
        bootstrap.Modal.getInstance(document.getElementById('quickAddCustomerModal')).hide();
        
        // 2. Tự động điền khách hàng mới vào Select2 ở trang Bán hàng
        const selectEl = $('.customer-select'); 
        if (selectEl.length > 0) {
            const newOption = new Option(`${savedCustomer.name} - ${savedCustomer.phone}`, savedCustomer.id, true, true);
            selectEl.append(newOption).trigger('change');
        }
    })
    .catch(error => {
        errorDiv.innerHTML = `<i class="bi bi-exclamation-triangle-fill me-1"></i> ${error.message}`;
        errorDiv.classList.remove('d-none');
    });
}

document.addEventListener("DOMContentLoaded", function () {
    // Lắng nghe sự kiện khi Modal Xóa bắt đầu bật lên
    const deleteModal = document.getElementById("deleteModal");
    if (deleteModal) {
        deleteModal.addEventListener("show.bs.modal", function (event) {
            const button = event.relatedTarget;
            
            // Bắt thuộc tính data-customer-id từ nút Xóa
            const id = button.getAttribute("data-customer-id"); 
            
            // Cập nhật action của Form để trỏ đúng vào API xóa của Backend
            const form = document.getElementById("deleteForm");
            if (form && id) {
                form.action = "/customers/delete/" + id;
            }
        });
    }
});