let rowIndex = 0; // Biến đếm index cho mảng orderDetails[]

$(document).ready(function() {
    // 1. Khởi tạo Khách hàng & Phí ship
    $('.customer-select').select2({ theme: 'bootstrap-5', width: 'style' });
    toggleShippingFee();
    
    $('#paidAmount').on('input', calculateCOD);
    $('#shippingFeeInput').on('input', calculateGrandTotal);

    // 2. SỰ KIỆN SÚNG QUÉT MÃ SERIAL
    $('#posSerialSearch').on('keydown', function(e) {
        if (e.which === 13) { 
            e.preventDefault(); 
            let serial = $(this).val().trim();
            if (serial) processScannedSerial(serial);
            $(this).val(''); 
        }
    });

    $('#btnSearchRight').on('click', function(e) {
        e.preventDefault();
        let serial = $('#posSerialSearch').val().trim();
        if (serial) processScannedSerial(serial);
        $('#posSerialSearch').val('');
        $('#posSerialSearch').focus(); 
    });

    // 3. Logic ẩn/hiện phương thức giao hàng
    $('#deliveryMethod').change(function() {
        toggleShippingFee(); 
        if ($(this).val() === 'SHIPPING') {
            $('#noteDiv').removeClass('col-md-8').addClass('col-md-12 mt-2');
        } else {
            document.querySelector('input[name="destination"]').value = '';
            $('#noteDiv').removeClass('col-md-12 mt-2').addClass('col-md-8');
        }
    });
});

// Hàm gọi API Backend để kiểm tra mã Serial
function processScannedSerial(serial) {
    if (document.querySelector(`input[value="${serial}"]`)) {
        alert("Lỗi: Mã Serial '" + serial + "' đã được quét trong giỏ hàng!");
        return;
    }

    fetch('/sales/api/scan-serial?serial=' + encodeURIComponent(serial))
        .then(response => {
            if (!response.ok) return response.text().then(text => { throw new Error(text) });
            return response.json();
        })
        .then(data => {
            addProductToCart(data); 
        })
        .catch(err => {
            alert(err.message);
        });
}

// Đẩy dữ liệu xuống bảng
function addProductToCart(data) {
    let tbody = document.getElementById('salesItemsBody');
    let emptyRow = document.getElementById('emptyCartRow');
    if (emptyRow) emptyRow.remove();

    let existingInput = document.querySelector(`input[data-prod-id="${data.productId}"]`);
    
    if (existingInput) {
        let tr = existingInput.closest('tr');
        let qtyInput = tr.querySelector('.qty-input');
        qtyInput.value = parseInt(qtyInput.value) + 1;
        
        let serialContainer = tr.querySelector('.serial-container');
        serialContainer.innerHTML += `<input type="hidden" name="scannedSerials" value="${data.serial}" />`;
        serialContainer.innerHTML += `<span class="badge bg-secondary me-1 mt-1">${data.serial}</span>`;
        
        triggerInputEvent(qtyInput); 
        return; 
    }

    let tr = document.createElement('tr');
    tr.className = 'item-row align-middle text-center';
    tr.innerHTML = `
        <td class="stt-cell text-muted fw-semibold"></td>
        <td class="text-start">
            <div class="fw-bold text-primary">${data.productName}</div>
            <small class="text-muted">SKU: ${data.sku} | ĐVT: ${data.unit}</small>
            <div class="serial-container mt-1">
                <input type="hidden" name="orderDetails[${rowIndex}].product.id" data-prod-id="${data.productId}" value="${data.productId}" />
                <input type="hidden" name="scannedSerials" value="${data.serial}" />
                <span class="badge bg-secondary me-1 mt-1">${data.serial}</span>
            </div>
        </td>
        <td class="text-success"><i class="bi bi-check-circle-fill"></i> Có sẵn</td>
        <td>
            <input type="number" name="orderDetails[${rowIndex}].quantity" class="form-control form-control-sm text-center fw-bold qty-input bg-light" min="1" value="1" readonly />
        </td>
        <td>
            <input type="number" name="orderDetails[${rowIndex}].unitPrice" class="form-control form-control-sm text-end price-input" value="${data.price}" required />
        </td>
        <td>
            <input type="number" class="form-control form-control-sm text-center discount-percent-input" min="0" max="100" step="0.1" value="0" />
        </td>
        <td>
            <input type="number" name="orderDetails[${rowIndex}].discountAmount" class="form-control form-control-sm text-end bg-light discount-amount-input" value="0" readonly tabindex="-1" />
        </td>
        <td class="text-end fw-bold fs-6 text-success row-total">${data.price.toLocaleString('vi-VN')} đ</td>
        <td>
            <button type="button" class="btn btn-sm btn-outline-danger border-0" onclick="removeSalesRow(this)"><i class="bi bi-trash"></i></button>
        </td>
    `;
    
    tbody.appendChild(tr);
    rowIndex++;
    updateRowNumbers();
    bindRowEvents(tr);
    calculateGrandTotal();
}

function updateRowNumbers() {
    let rows = document.querySelectorAll('#salesItemsBody .item-row');
    rows.forEach((row, index) => {
        row.querySelector('.stt-cell').textContent = index + 1;
    });
}

function removeSalesRow(btn) {
    btn.closest('tr').remove();
    let tbody = document.getElementById('salesItemsBody');
    if (tbody.children.length === 0) {
        tbody.innerHTML = `
            <tr id="emptyCartRow">
                <td colspan="9" class="text-center py-5 text-muted">
                    <i class="bi bi-upc-scan fs-1 d-block mb-2"></i> Chưa có sản phẩm nào. Vui lòng quét mã Serial để tiếp tục!
                </td>
            </tr>`;
    } else {
        updateRowNumbers();
    }
    calculateGrandTotal();
}

// 🚀 ĐÃ XÓA SẠCH VALIDATE TỒN KHO ẢO Ở ĐÂY
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

    let method = document.getElementById('deliveryMethod').value;
    if (method === 'SHIPPING') {
        let shippingFee = parseFloat(document.getElementById('shippingFeeInput').value) || 0;
        grandTotal += shippingFee;
    }

    document.getElementById('grandTotal').textContent = grandTotal.toLocaleString('vi-VN') + ' đ';
    if (typeof calculateCOD === 'function') calculateCOD();
}

function triggerInputEvent(element) {
    const event = new Event('input', { bubbles: true });
    element.dispatchEvent(event);
}

// 🚀 ĐÃ BỎ LỚP CHẶN SUBMIT CŨ 
function submitSalesOrder() {
    if (document.querySelectorAll('#salesItemsBody .item-row').length === 0) {
        alert("Vui lòng quét ít nhất 1 mã Serial để chốt đơn!");
        return;
    }
    document.getElementById('salesForm').submit();
}

function toggleShippingFee() {
    const method = document.getElementById('deliveryMethod').value;
    const feeDiv = document.getElementById('shippingFeeDiv');
    const destDiv = document.getElementById('destinationDiv'); 
    const codDiv = document.getElementById('codDiv'); 
    
    if (method === 'SHIPPING') {
        feeDiv.style.display = 'block';
        destDiv.style.display = 'block';
        codDiv.classList.remove('d-none');
        codDiv.classList.add('d-flex'); 
    } else {
        feeDiv.style.display = 'none';
        destDiv.style.display = 'none';
        codDiv.classList.remove('d-flex');
        codDiv.classList.add('d-none'); 
        document.getElementById('shippingFeeInput').value = 0; 
    }
    calculateGrandTotal(); 
}

function calculateCOD() {
    const method = document.getElementById('deliveryMethod').value;
    if (method !== 'SHIPPING') return;

    let grandTotalText = document.getElementById('grandTotal').textContent;
    let grandTotal = parseFloat(grandTotalText.replace(/\./g, '').replace(' đ', '')) || 0;
    let paidAmount = parseFloat(document.getElementById('paidAmount').value) || 0;
    
    let cod = grandTotal - paidAmount;
    if (cod < 0) cod = 0;

    document.getElementById('codAmount').value = cod;
}