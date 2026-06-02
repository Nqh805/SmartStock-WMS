// HÀM LOAD CHILD CATEGORIES THEO PARENT
function loadChildCategories() {
    const parentId = document.getElementById('parentCategory').value;
    if (!parentId) {
        // Nếu không chọn parent, reset child dropdown
        document.getElementById('productType').innerHTML = '<option value="" selected disabled>Chọn loại sản phẩm...</option>';
        return;
    }

    // Gửi parent ID lên server để lấy child categories
    fetch('/products/get-child-categories', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/x-www-form-urlencoded',
        },
        body: 'parentId=' + encodeURIComponent(parentId)
    })
    .then(response => {
        if (!response.ok) throw new Error('Network response was not ok');
        return response.json();
    })
    .then(data => {
        // Cập nhật dropdown child categories
        const selectChild = document.getElementById('productType');
        selectChild.innerHTML = '<option value="" selected disabled>Chọn loại sản phẩm...</option>';
        data.forEach(category => {
            const option = document.createElement('option');
            option.value = category.id;
            option.textContent = category.name;
            selectChild.appendChild(option);
        });
    })
    .catch(error => console.error('Lỗi tải loại sản phẩm:', error));
}

// modal chi tiết sản phẩm
function openDetailModal(row) {
    // 1. Lấy dữ liệu Text
    document.getElementById('detailName').textContent = row.getAttribute('data-name');
    document.getElementById('detailCategory').textContent = row.getAttribute('data-category');
    document.getElementById('detailBrand').textContent = row.getAttribute('data-brand') || 'N/A';
    document.getElementById('detailSku').textContent = row.getAttribute('data-sku');
    document.getElementById('detailBarcode').textContent = row.getAttribute('data-barcode');
    
    // 2. LẤY DỮ LIỆU SỐ BẰNG PARSE FLOAT & TÍNH TOÁN GIÁ
    let basePrice = parseFloat(row.getAttribute('data-price') || 0);
    let costPrice = parseFloat(row.getAttribute('data-cost') || 0);
    let taxRate = parseFloat(row.getAttribute('data-tax') || 0);
    
    // Tính tiền
    let vatAmount = basePrice * (taxRate / 100);
    let finalPrice = basePrice + vatAmount;

    // Đổ lên UI Modal
    document.getElementById('detailCostPrice').textContent = costPrice.toLocaleString('vi-VN') + ' đ';
    document.getElementById('detailPrice').textContent = basePrice.toLocaleString('vi-VN') + ' đ';
    document.getElementById('detailTaxRate').textContent = taxRate + '%';
    document.getElementById('detailVatAmount').textContent = '+ ' + vatAmount.toLocaleString('vi-VN') + ' đ';
    document.getElementById('detailFinalPrice').textContent = finalPrice.toLocaleString('vi-VN') + ' đ';
    
    // 3. Các thuộc tính khác
    document.getElementById('detailUnit').textContent = row.getAttribute('data-unit');
    document.getElementById('detailWarranty').textContent = row.getAttribute('data-warranty') + ' tháng';

    // tổng tồn kho
    let qty = parseInt(row.getAttribute('data-qty') || 0);
    let minStock = parseInt(row.getAttribute('data-minstock') || 0);
    let qtyElement = document.getElementById('detailQty');
    
    // Gán giá trị số lượng
    qtyElement.textContent = qty;
    
    // Xóa các class màu cũ để tránh bị đè màu khi click qua lại giữa các dòng
    qtyElement.className = ''; 

    // Đổi màu chữ theo số lượng dựa trên mức tồn kho tối thiểu
    if (qty <= 0) {
        qtyElement.classList.add('text-danger'); // Màu đỏ (Hết hàng)
    } else if (qty <= minStock) {
        qtyElement.classList.add('text-warning'); // Màu vàng/cam (Sắp hết)
    } else {
        qtyElement.classList.add('text-success'); // Màu xanh lá (Còn hàng)
    }


    document.getElementById('detailMinStock').textContent = minStock;
    document.getElementById('detailDesc').textContent = row.getAttribute('data-desc') || 'Không có mô tả';
    document.getElementById('detailSpecs').textContent = row.getAttribute('data-specs') || 'Không có thông số kỹ thuật';

    // 2. Xử lý logic hiển thị Hình ảnh
    let imgUrl = row.getAttribute('data-img');
    if (!imgUrl || imgUrl.trim() === '') {
        imgUrl = '/asset/images/no-image.png'; // Ảnh mặc định nếu không có
    }
    document.getElementById('detailImg').src = imgUrl;

    // 3. Cập nhật đường link cho nút "Chỉnh sửa" trong Modal
    let productId = row.getAttribute('data-id');
    document.getElementById('detailEditBtn').href = '/products/edit/' + productId;

    // 4. Kích hoạt hiển thị Modal của Bootstrap
    var detailModal = new bootstrap.Modal(document.getElementById('detailModal'));
    detailModal.show();
}
// HÀM XỬ LÝ TOGGLE NGÀNH HÀNG & LOẠI SẢN PHẨM
function setupCategoryToggles() {
    const chkNewParent = document.getElementById('checkNewCategory');
    const inputNewParent = document.getElementById('inputNewCategory');
    const selectParent = document.getElementById('parentCategory');

    const chkNewChild = document.getElementById('checkNewType');
    const inputNewChild = document.getElementById('inputNewType');
    const selectChild = document.getElementById('productType');

    if (!chkNewParent || !chkNewChild) return;

    // 1. Khi tích chọn "Thêm Ngành Hàng Mới"
    chkNewParent.addEventListener('change', function() {
        if (this.checked) {
            // Khóa cả 2 ô Select Dropdown
            selectParent.disabled = true;
            selectParent.required = false;
            selectParent.value = "";

            selectChild.disabled = true;
            selectChild.required = false;
            selectChild.value = "";

            // Mở ô nhập Ngành hàng mới
            inputNewParent.disabled = false;
            inputNewParent.required = true;
            inputNewParent.focus();

            // Tự động tích chọn và bắt buộc nhập "Loại SP mới" (vì ngành hàng mới phải đi kèm loại SP mới)
            chkNewChild.checked = true;
            chkNewChild.disabled = true; // Khóa checkbox không cho người dùng tự tắt
            inputNewChild.disabled = false;
            inputNewChild.required = true;

        } else {
            // Khi BỎ chọn: Trả lại trạng thái bình thường cho Dropdown Ngành hàng
            selectParent.disabled = false;
            selectParent.required = true;
            
            inputNewParent.disabled = true;
            inputNewParent.required = false;
            inputNewParent.value = "";

            // Mở khóa checkbox Loại SP mới để user tự do thao tác lại
            chkNewChild.disabled = false;
            chkNewChild.checked = false; 
            
            // Khôi phục trạng thái mặc định của Loại SP
            selectChild.disabled = false;
            selectChild.required = true;
            inputNewChild.disabled = true;
            inputNewChild.required = false;
            inputNewChild.value = "";
        }
    });

    // 2. Khi chỉ tích chọn "Thêm Loại SP Mới" (Thêm loại con cho Ngành hàng đã có)
    chkNewChild.addEventListener('change', function() {
        // Chỉ xử lý nếu không bị khóa bởi checkNewParent
        if (!this.disabled) {
            if (this.checked) {
                // Chỉ khóa Dropdown Loại Sản Phẩm
                selectChild.disabled = true;
                selectChild.required = false;
                selectChild.value = "";
                
                inputNewChild.disabled = false;
                inputNewChild.required = true;
                inputNewChild.focus();
            } else {
                selectChild.disabled = false;
                selectChild.required = true;
                
                inputNewChild.disabled = true;
                inputNewChild.required = false;
                inputNewChild.value = "";
            }
        }
    });
}

// Hàm xóa ảnh
function removeImage() {
    const inputFile = document.getElementById('productImage');
    const btnRemove = document.getElementById('btnRemoveImage');
    if (inputFile) inputFile.value = "";
    if (btnRemove) btnRemove.classList.add('d-none');
}

// Khởi tạo mỗi lần load trang
document.addEventListener('DOMContentLoaded', function() {
    setupCategoryToggles();

    // Load child categories khi chọn parent category
    const parentCategorySelect = document.getElementById('parentCategory');
    if (parentCategorySelect) {
        parentCategorySelect.addEventListener('change', loadChildCategories);
    }

    // Format tiền tệ
    document.querySelectorAll('.currency-input').forEach(input => {
        const formatValue = (el) => {
            let value = el.value.replace(/\D/g, ""); 
            const hiddenInputId = el.id.replace('Display', '');
            const hiddenInput = document.getElementById(hiddenInputId);
            if (hiddenInput) hiddenInput.value = value;
            el.value = value !== "" ? new Intl.NumberFormat('en-US').format(value) : "";
        };
        if (input.value !== "") formatValue(input);
        input.addEventListener('input', function() { formatValue(this); });
    });

    // gọi API sinh barcode
    const btnBarcode = document.getElementById('btnGenerateBarcode');
    const inputBarcode = document.getElementById('barCode');
    if (btnBarcode && inputBarcode) {
        btnBarcode.addEventListener('click', function() {
            fetch('/products/generate-barcode') 
                .then(response => {
                    if (!response.ok) throw new Error('Lỗi kết nối máy chủ');
                    return response.text();
                })
                .then(newBarcode => {
                    inputBarcode.value = newBarcode;
                    inputBarcode.classList.add('is-valid'); 
                    setTimeout(() => inputBarcode.classList.remove('is-valid'), 2000); 
                })
                .catch(error => {
                    console.error('Lỗi:', error);
                    alert('Có lỗi xảy ra khi sinh mã vạch!');
                });
        });
    }

    // Nút xóa ảnh
    const inputFile = document.getElementById('productImage');
    const btnRemove = document.getElementById('btnRemoveImage');
    if (inputFile && btnRemove) {
        inputFile.addEventListener('change', function() {
            if (this.files && this.files.length > 0) {
                btnRemove.classList.remove('d-none');
            } else {
                btnRemove.classList.add('d-none');
            }
        });
    }

    // Modal xóa
    const deleteModal = document.getElementById('deleteModal');
    const deleteForm = document.getElementById('deleteForm');
    if (deleteModal) {
        deleteModal.addEventListener('show.bs.modal', function (event) {
            const button = event.relatedTarget; 
            const productId = button.getAttribute('data-product-id');
            deleteForm.setAttribute('action', '/products/delete/' + productId);
        });
    }

    // Tự động đóng Alert
    document.querySelectorAll('.alert').forEach(function(alert) {
        setTimeout(function() {
            const bsAlert = new bootstrap.Alert(alert);
            bsAlert.close();
        }, 3000);
    });

    // hover canh bao ton kho
    var tooltipTriggerList = [].slice.call(document.querySelectorAll('[data-bs-toggle="tooltip"]'))
    var tooltipList = tooltipTriggerList.map(function (tooltipTriggerEl) {
        return new bootstrap.Tooltip(tooltipTriggerEl)
    })
});
