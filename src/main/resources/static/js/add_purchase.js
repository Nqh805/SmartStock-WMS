let rowIndex = 1;

function addNewRow() {
    const tbody = document.getElementById('purchaseItemsBody');
    if (!tbody) return;

    const firstSelect = document.querySelector('.product-select');
    let productOptionsHtml = '<option value="">-- Chọn sản phẩm --</option>';

    if (firstSelect) {
        productOptionsHtml = firstSelect.innerHTML;
        productOptionsHtml = productOptionsHtml.replace(/data-select2-id="[^"]*"/g, '');
        productOptionsHtml = productOptionsHtml.replace(/selected(="[^"]*")?/gi, '');
    }

    const tr = document.createElement('tr');
    tr.className = 'item-row';
    tr.innerHTML = `
        <td>
            <select name="orderDetails[${rowIndex}].product.id" class="form-select form-select-sm product-select" required>
                ${productOptionsHtml}
            </select>
        </td>
        <td class="text-center fw-medium text-secondary unit-cell" style="font-size: 0.9rem;">-</td>
        <td>
            <input type="number" name="orderDetails[${rowIndex}].quantity" class="form-control form-control-sm text-center qty-input" min="1" value="1" required />
        </td>
        <td>
            <input type="number" name="orderDetails[${rowIndex}].unitPrice" class="form-control form-control-sm text-end price-input" min="0" placeholder="0" required />
        </td>
        <td>
            <input type="number" class="form-control form-control-sm text-center discount-percent-input" min="0" max="100" step="0.1" value="0" placeholder="0%" />
        </td>
        <td>
            <input type="number" name="orderDetails[${rowIndex}].discountAmount" class="form-control form-control-sm text-end discount-amount-input bg-light" min="0" value="0" readonly tabindex="-1" />
        </td>
        <td class="text-end fw-bold row-total text-danger" style="font-size: 0.95rem;">0 đ</td>
        <td class="text-center">
            <button type="button" class="btn btn-sm btn-outline-danger border-0" onclick="removeRow(this)">
                <i class="bi bi-trash"></i>
            </button>
        </td>
    `;
    
    tbody.appendChild(tr);
    rowIndex++;
    
    // Gắn các sự kiện (tính tiền, tính chiết khấu) cho dòng mới
    bindRowEvents(tr);
    
    // Khởi tạo Select2 UI cho thẻ select mới
    const newSelect = tr.querySelector('.product-select');
    initSelect2(newSelect);
    
    // Cập nhật lại hiển thị đơn vị tính
    updateUnitDisplay(newSelect);
}

function removeRow(button) {
    const rows = document.querySelectorAll('.item-row');
    if (rows.length > 1) {
        button.closest('tr').remove();
        calculateGrandTotal();
    } else {
        alert("Hệ thống yêu cầu đơn mua hàng phải có ít nhất một sản phẩm!");
    }
}

function updateUnitDisplay(selectElement) {
    const selectedOption = selectElement.options[selectElement.selectedIndex];
    const row = selectElement.closest('tr');
    const unitCell = row.querySelector('.unit-cell');
    if (unitCell) {
        const unitValue = selectedOption.getAttribute('data-unit');
        unitCell.textContent = unitValue ? unitValue : '-';
    }
}

function calculateRowTotal(row) {
    const qty = parseFloat(row.querySelector('.qty-input').value) || 0;
    const price = parseFloat(row.querySelector('.price-input').value) || 0;
    const discountAmount = parseFloat(row.querySelector('.discount-amount-input').value) || 0;
    
    const subtotal = qty * price;
    let finalTotal = subtotal - discountAmount;
    
    if (finalTotal < 0) finalTotal = 0;

    row.querySelector('.row-total').textContent = finalTotal.toLocaleString('vi-VN') + ' đ';
    return finalTotal;
}

function calculateGrandTotal() {
    let grandTotal = 0;
    document.querySelectorAll('.item-row').forEach(row => {
        grandTotal += calculateRowTotal(row);
    });
    
    const grandTotalEl = document.getElementById('grandTotal');
    if (grandTotalEl) {
        grandTotalEl.textContent = grandTotal.toLocaleString('vi-VN') + ' đ';
    }
}

function bindRowEvents(row) {
    const qtyInput = row.querySelector('.qty-input');
    const priceInput = row.querySelector('.price-input');
    const percentInput = row.querySelector('.discount-percent-input');
    const amountInput = row.querySelector('.discount-amount-input');
    const productSelect = row.querySelector('.product-select');

    const recalculateDiscountFromPercent = () => {
        const qty = parseFloat(qtyInput.value) || 0;
        const price = parseFloat(priceInput.value) || 0;
        let percentText = percentInput.value.trim();
        let percent = parseFloat(percentText);
        
        // Ngầm định % = 0 nếu người dùng xóa trắng ô input
        if (percentText === '' || isNaN(percent)) {
            percent = 0;
        }
        
        const subtotal = qty * price;
        amountInput.value = Math.round(subtotal * (percent / 100));
        calculateGrandTotal();
    };

    if (qtyInput) qtyInput.addEventListener('input', recalculateDiscountFromPercent);
    if (priceInput) priceInput.addEventListener('input', recalculateDiscountFromPercent);

    if (percentInput) {
        percentInput.addEventListener('input', function() {
            if (this.value > 100) this.value = 100;
            if (this.value < 0) this.value = 0;
            recalculateDiscountFromPercent();
        });

        // Tự động điền lại số 0 nếu người dùng xóa trống và bấm chuột ra chỗ khác
        percentInput.addEventListener('blur', function() {
            if (this.value.trim() === '') {
                this.value = '0';
            }
        });
    }

    // (Đã xóa sự kiện nhập tay của amountInput vì bây giờ là readonly)

    if (productSelect) {
        productSelect.addEventListener('change', function() {
            updateUnitDisplay(this);
            handleProductChange(this); // THÊM DÒNG NÀY ĐỂ KÍCH HOẠT API TỰ ĐIỀN GIÁ
        });
    }
}
function handleProductChange(selectElement) {
    const productId = selectElement.value; 
    const row = selectElement.closest('tr');
    
    const priceInput = row.querySelector('.price-input');

    if (!productId) {
        priceInput.value = 0;
        calculateRowTotal(row); 
        calculateGrandTotal();
        return;
    }

    // gửi request lấy giá nhập dự kiến mới nhất
    fetch(`/products/${productId}/latest-import-price`)
        .then(response => {
            if (!response.ok) throw new Error('Không tìm thấy API lấy giá');
            return response.json();
        })
        .then(latestImportPrice => {
            // 1. Điền giá lấy được vào ô input
            priceInput.value = latestImportPrice;
            
            // 2. KÍCH HOẠT SỰ KIỆN 'input' ĐỂ TỰ ĐỘNG TÍNH CHIẾT KHẤU VÀ TỔNG TIỀN
            // (Vì bạn có viết logic tính chiết khấu khi priceInput thay đổi)
            const event = new Event('input', { bubbles: true });
            priceInput.dispatchEvent(event);
            
            // 3. Tính lại dòng và tổng hóa đơn
            calculateRowTotal(row);
            calculateGrandTotal();
        })
        .catch(error => {
            console.error('Lỗi khi lấy giá nhập:', error);
            priceInput.value = 0;
            calculateRowTotal(row);
            calculateGrandTotal();
        });
}
// HÀM KHỞI TẠO Ô TÌM KIẾM SẢN PHẨM
function initSelect2(element) {
    $(element).select2({
        theme: 'bootstrap-5',
        width: '100%',
        language: {
            noResults: function() {
                return "Không tìm thấy sản phẩm";
            }
        }
    }).on('select2:select', function (e) {
        this.dispatchEvent(new Event('change', { bubbles: true }));
    });
}

function updateWarehouseAddress(selectObj) {
            var selectedOption = selectObj.options[selectObj.selectedIndex];
            var address = selectedOption.getAttribute('data-address');
            var container = document.getElementById('warehouseAddressContainer');
            var textSpan = document.getElementById('warehouseAddressText');

            if (address && selectObj.value !== "") {
                textSpan.textContent = address;
                container.style.display = 'block';
            } else {
                container.style.display = 'none';
            }
        }
document.addEventListener('DOMContentLoaded', () => {
    document.querySelectorAll('.product-select').forEach(select => {
        initSelect2(select);
    });

    const whSelect = document.getElementById('wareHouse');
            if(whSelect) updateWarehouseAddress(whSelect);
    
    const existingRows = document.querySelectorAll('.item-row');
    rowIndex = existingRows.length;

    existingRows.forEach(row => {
        const percentInput = row.querySelector('.discount-percent-input');
        const amountInput = row.querySelector('.discount-amount-input');
        const qtyInput = row.querySelector('.qty-input');
        const priceInput = row.querySelector('.price-input');

        // Logic phục hồi chỉ số % khi Form bị tải lại do có lỗi validate từ server
        if (percentInput && amountInput && qtyInput && priceInput) {
            const qty = parseFloat(qtyInput.value) || 0;
            const price = parseFloat(priceInput.value) || 0;
            const amount = parseFloat(amountInput.value) || 0;
            
            if (qty * price > 0 && amount > 0) {
                let pct = (amount / (qty * price)) * 100;
                // Nếu phép chia ra số nguyên (vd: 5%), hiện '5'. Nếu lẻ hiện 1 số thập phân (vd: '5.5')
                percentInput.value = Number.isInteger(pct) ? pct : pct.toFixed(1); 
            }
        }

        bindRowEvents(row);
        const select = row.querySelector('.product-select');
        if (select) updateUnitDisplay(select); 
        calculateRowTotal(row);
    });
    
    calculateGrandTotal();
    
    const expectedArrivalInput = document.getElementById('expectedArrival');
    if (expectedArrivalInput) {
        const today = new Date().toISOString().split('T')[0];
        expectedArrivalInput.min = today;
    }
    
    document.querySelectorAll('.alert').forEach(function(alert) {
        setTimeout(function() {
            const bsAlert = new bootstrap.Alert(alert);
            bsAlert.close();
        }, 3000);
    });
});