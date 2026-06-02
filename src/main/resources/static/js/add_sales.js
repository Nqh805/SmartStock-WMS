let rowIndex = 0; // Biến đếm index cho mảng orderDetails[]

$(document).ready(function() {
    // 1. Khởi tạo Select2 cho Khách hàng
    $('.customer-select').select2({
        theme: 'bootstrap-5',
        width: 'style'
    });

    // bôi đen cả ô nhập tiền
    $('#paidAmount').on('focus', function() {
        $(this).select();
    });
    // 2. Khởi tạo Select2 cho Thanh Quét Mã / Tìm kiếm SP
    $('#posProductSearch').select2({
        theme: 'bootstrap-5',
        width: 'style', // Thay đổi ở đây
        placeholder: "Quét mã vạch (Barcode/SKU) hoặc nhập Tên sản phẩm...",
        allowClear: true
    }).on('select2:select', function (e) {
        let option = $(e.params.data.element);
        let prodId = option.val();
        
        if (prodId) {
            addProductToCart(option);
            $(this).val(null).trigger('change'); 
            $(this).select2('close'); 
        }
    });

    // 3. Logic ẩn/hiện địa chỉ giao hàng (Tương lai mở rộng)
    $('#deliveryMethod').change(function() {
        if ($(this).val() === 'SHIPPING') {
            $('#destinationDiv').show();
            $('#noteDiv').removeClass('col-md-8').addClass('col-md-12 mt-2');
        } else {
            $('#destinationDiv').hide();
            document.querySelector('input[name="destination"]').value = '';
            $('#noteDiv').removeClass('col-md-12 mt-2').addClass('col-md-8');
        }
    });

    // 4. Xử lý sự kiện cho nút Kính lúp bên phải
    $('#btnSearchRight').on('mousedown', function(e) {
        e.preventDefault(); 
        let isOpen = $('.select2-dropdown').length > 0; 
        if (isOpen) {
            let highlightedOption = $('.select2-results__option--highlighted');
            if (highlightedOption.length > 0) {
                highlightedOption.trigger('mouseup');
            }
        } else {
            $('#posProductSearch').select2('open');
        }
    });
});

// Hàm đưa sản phẩm từ thanh Search xuống Giỏ hàng
function addProductToCart(optionEl) {
    let tbody = document.getElementById('salesItemsBody');
    let emptyRow = document.getElementById('emptyCartRow');
    if (emptyRow) emptyRow.remove();

    let id = optionEl.val();
    let name = optionEl.attr('data-name');
    let sku = optionEl.attr('data-sku');
    let unit = optionEl.attr('data-unit') || '-';
    let price = parseFloat(optionEl.attr('data-price')) || 0;
    let stockAttr = optionEl.attr('data-stock');
    let stock = stockAttr ? parseInt(stockAttr, 10) : 0;
    if (isNaN(stock)) stock = 0;
    // A. KIỂM TRA: Nếu sản phẩm đã có trong bảng, chỉ cần tăng số lượng lên +1
    let existingInput = document.querySelector(`input[data-prod-id="${id}"]`);
    if (existingInput) {
        let tr = existingInput.closest('tr');
        let qtyInput = tr.querySelector('.qty-input');
        qtyInput.value = parseInt(qtyInput.value) + 1;
        
        // Cảnh báo nếu bán vượt quá tồn kho (Nhưng vẫn cho bán vì có thể chưa nhập kịp lên phần mềm)
        if (parseInt(qtyInput.value) > stock) {
            qtyInput.classList.add('border-danger', 'text-danger');
        }
        
        triggerInputEvent(qtyInput);
        return;
    }

    // B. NẾU CHƯA CÓ: Tạo dòng HTML mới
    let stockHtml = stock > 0 ? `<span class="text-success fw-bold">${stock}</span>` : `<span class="text-danger fw-bold">0</span>`;

    let tr = document.createElement('tr');
    tr.className = 'item-row align-middle text-center';
    tr.innerHTML = `
        <td class="stt-cell text-muted fw-semibold"></td>
        <td class="text-start">
            <div class="fw-bold text-primary">${name}</div>
            <small class="text-muted">SKU: ${sku} | ĐVT: ${unit}</small>
            <input type="hidden" name="orderDetails[${rowIndex}].product.id" data-prod-id="${id}" value="${id}" />
        </td>
        <td style="font-size: 0.9rem;">${stockHtml}</td>
        <td>
            <input type="number" name="orderDetails[${rowIndex}].quantity" class="form-control form-control-sm text-center fw-bold qty-input" min="1" value="1" required />
        </td>
        <td>
            <input type="number" name="orderDetails[${rowIndex}].unitPrice" class="form-control form-control-sm text-end price-input" value="${price}" required />
        </td>
        <td>
            <input type="number" class="form-control form-control-sm text-center discount-percent-input" min="0" max="100" step="0.1" value="0" />
        </td>
        <td>
            <input type="number" name="orderDetails[${rowIndex}].discountAmount" class="form-control form-control-sm text-end bg-light discount-amount-input" value="0" readonly tabindex="-1" />
        </td>
        <td class="text-end fw-bold fs-6 text-success row-total">${price.toLocaleString('vi-VN')} đ</td>
        <td>
            <button type="button" class="btn btn-sm btn-outline-danger border-0" onclick="removeSalesRow(this)"><i class="bi bi-x-lg"></i></button>
        </td>
    `;
    
    tbody.appendChild(tr);
    rowIndex++;
    
    updateRowNumbers();
    bindRowEvents(tr);
    calculateGrandTotal();
}

// Tính lại STT cho đẹp
function updateRowNumbers() {
    let rows = document.querySelectorAll('#salesItemsBody .item-row');
    rows.forEach((row, index) => {
        row.querySelector('.stt-cell').textContent = index + 1;
        // Logic cập nhật lại mảng orderDetails[index] nếu lỡ bị xóa dòng giữa chừng có thể phát triển thêm ở đây
    });
}

function removeSalesRow(btn) {
    btn.closest('tr').remove();
    let tbody = document.getElementById('salesItemsBody');
    if (tbody.children.length === 0) {
        tbody.innerHTML = `
            <tr id="emptyCartRow">
                <td colspan="9" class="text-center py-5 text-muted">
                    <i class="bi bi-cart-x fs-1 d-block mb-2"></i> Chưa có sản phẩm nào.
                </td>
            </tr>`;
    } else {
        updateRowNumbers();
    }
    calculateGrandTotal();
}

// Bắt các sự kiện tính toán tiền giống hệt bản Purchase
function bindRowEvents(row) {
    const qtyInput = row.querySelector('.qty-input');
    const priceInput = row.querySelector('.price-input');
    const percentInput = row.querySelector('.discount-percent-input');
    const amountInput = row.querySelector('.discount-amount-input');
    const rowTotal = row.querySelector('.row-total');

    const recalculateRow = () => {
        let qty = parseFloat(qtyInput.value) || 0;
        let price = parseFloat(priceInput.value) || 0;
        let percent = parseFloat(percentInput.value) || 0;
        
        let subtotal = qty * price;
        let discount = Math.round(subtotal * (percent / 100));
        amountInput.value = discount;
        
        let finalTotal = subtotal - discount;
        rowTotal.textContent = finalTotal.toLocaleString('vi-VN') + ' đ';
        
        calculateGrandTotal();
    };

    qtyInput.addEventListener('input', recalculateRow);
    priceInput.addEventListener('input', recalculateRow);
    percentInput.addEventListener('input', function() {
        if (this.value > 100) this.value = 100;
        if (this.value < 0) this.value = 0;
        recalculateRow();
    });
    percentInput.addEventListener('blur', function() {
        if (this.value.trim() === '') this.value = '0';
    });
}

function calculateGrandTotal() {
    let grandTotal = 0;
    document.querySelectorAll('#salesItemsBody .item-row').forEach(row => {
        let qty = parseFloat(row.querySelector('.qty-input').value) || 0;
        let price = parseFloat(row.querySelector('.price-input').value) || 0;
        let discount = parseFloat(row.querySelector('.discount-amount-input').value) || 0;
        grandTotal += (qty * price) - discount;
    });
    document.getElementById('grandTotal').textContent = grandTotal.toLocaleString('vi-VN') + ' đ';
}

function triggerInputEvent(element) {
    const event = new Event('input', { bubbles: true });
    element.dispatchEvent(event);
}

function submitSalesOrder() {
    if (document.querySelectorAll('#salesItemsBody .item-row').length === 0) {
        alert("Vui lòng quét chọn ít nhất 1 sản phẩm để tạo đơn hàng!");
        return;
    }
    document.getElementById('salesForm').submit();
}