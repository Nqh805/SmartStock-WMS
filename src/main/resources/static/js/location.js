document.addEventListener("DOMContentLoaded", function () {
  const warehouseSelect = document.getElementById("locWarehouse");
  const triggers = document.querySelectorAll(".loc-trigger");

  // Hàm xử lý bóc tách mã kho: WH-HN-01 -> HN1
  function getWarehousePrefix() {
    if (!warehouseSelect) return "";
    
    const selectedOption = warehouseSelect.options[warehouseSelect.selectedIndex];
    if (!selectedOption || !selectedOption.value) return "";

    const whCode = selectedOption.getAttribute("data-code"); // Lấy chuỗi dạng "WH-HN-01"
    if (!whCode) return "";

    const parts = whCode.split("-");
    if (parts.length >= 2) {
      const penultimate = parts[parts.length - 2].toUpperCase(); // (HN)
      let last = parts[parts.length - 1]; // (01)

      // Regex loại bỏ số 0 ở đầu nhưng vẫn giữ lại ít nhất 1 số (vd: 01 -> 1, 00 -> 0)
      last = last.replace(/^0+(?=\d)/, "");

      return penultimate + last; // HN1
    }
    return whCode.toUpperCase();
  }

  // Hàm tổng hợp và hiển thị mã Barcode vị trí
  function updateBarcode() {
    const prefix = getWarehousePrefix();
    const shelf = document.getElementById("locShelf")?.value.trim().toUpperCase() || "";
    const tier = document.getElementById("locTier")?.value.trim().toUpperCase() || "";
    const bin = document.getElementById("locBin")?.value.trim().toUpperCase() || "";

    const autoBarcode = [prefix, shelf, tier, bin].filter(Boolean); // Lọc bỏ các giá trị rỗng

    const barcodeInput = document.getElementById("locBarcode");
    if (barcodeInput) {
      barcodeInput.value = autoBarcode.join("-"); // VD: HN1-K01-T02-B05
    }
  }

  // Lắng nghe sự kiện
  if (warehouseSelect) {
    warehouseSelect.addEventListener("change", updateBarcode);
  }

  triggers.forEach((input) => {
    input.addEventListener("input", updateBarcode);
  });
});

// Hàm mở Modal & Gọi AJAX lấy danh sách lô hàng trên kệ
async function openLocationBatchesModal(row) {
  const locId = row.getAttribute("data-id");
  const locCode = row.getAttribute("data-code");

  // 1. Gắn mã kệ lên tiêu đề Modal
  document.getElementById("modalLocCode").textContent = locCode;
  const tbody = document.getElementById("locationBatchesBody");

  // 2. Hiển thị trạng thái Loading
  tbody.innerHTML = `
    <tr>
      <td colspan="5" class="text-center py-4 text-muted">
        <div class="spinner-border spinner-border-sm text-primary me-2"></div>Đang quét dữ liệu trên kệ...
      </td>
    </tr>`;

  // 3. Hiển thị Modal một cách an toàn (tránh tạo nhiều instance)
  const modalEl = document.getElementById("locationBatchesModal");
  const modal = bootstrap.Modal.getOrCreateInstance(modalEl);
  modal.show();

  // 4. Gọi API lấy danh sách bằng async/await
  try {
    const response = await fetch(`/locations/api/${locId}/batches`);
    if (!response.ok) throw new Error("Lỗi kết nối đến máy chủ");
    
    const data = await response.json();

    // Nếu kệ trống
    if (data.length === 0) {
      tbody.innerHTML = `
        <tr>
          <td colspan="5" class="text-center py-4 text-muted">
            <i class="bi bi-inbox fs-3 d-block mb-2"></i>Kệ này hiện đang trống, chưa chứa lô hàng nào.
          </td>
        </tr>`;
      return;
    }

    // Nếu có hàng -> Dùng map() sinh chuỗi HTML rồi gán 1 lần (Tối ưu hiệu năng)
    const rowsHtml = data.map((batch, index) => {
      const dateStr = batch.importDate
        ? new Date(batch.importDate).toLocaleDateString("vi-VN")
        : "N/A";

      return `
        <tr>
            <td class="text-center text-muted">${index + 1}</td>
            <td class="fw-bold text-primary">${batch.batchCode}</td>
            <td class="fw-medium">${batch.productName}</td>
            <td class="text-center fw-bold text-success">${batch.quantity}</td>
            <td class="text-center">${dateStr}</td>
        </tr>`;
    }).join(''); // Gộp mảng thành chuỗi

    tbody.innerHTML = rowsHtml;

  } catch (error) {
    tbody.innerHTML = `
      <tr>
        <td colspan="5" class="text-center py-4 text-danger">
          <i class="bi bi-exclamation-triangle-fill me-1"></i> Lỗi tải dữ liệu: ${error.message}
        </td>
      </tr>`;
  }
}

// Hàm gửi AJAX lưu dữ liệu vị trí mới lên Spring Boot
async function submitAddLocation() {
  const warehouseId = document.getElementById("locWarehouse")?.value;
  const shelfName = document.getElementById("locShelf")?.value.trim();
  const tierName = document.getElementById("locTier")?.value.trim();
  const binName = document.getElementById("locBin")?.value.trim();
  const locationCode = document.getElementById("locBarcode")?.value.trim();
  const errorDiv = document.getElementById("addLocError");

  // Reset hiển thị lỗi trước khi kiểm tra
  errorDiv.classList.add("d-none");
  errorDiv.textContent = "";

  if (!warehouseId || !shelfName || !tierName || !binName || !locationCode) {
    errorDiv.textContent = "Vui lòng nhập đầy đủ tất cả các trường có dấu (*) !";
    errorDiv.classList.remove("d-none");
    return;
  }

  const payload = { warehouseId, shelfName, tierName, binName, locationCode };

  try {
    const response = await fetch("/locations/api/add", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload),
    });

    if (!response.ok) {
      const errorText = await response.text();
      throw new Error(errorText);
    }
    
    // Lưu thành công -> Tải lại trang
    window.location.reload();
  } catch (error) {
    errorDiv.innerHTML = `<i class="bi bi-exclamation-triangle-fill me-1"></i> ${error.message}`;
    errorDiv.classList.remove("d-none");
  }
}

// Lắng nghe sự kiện khi Modal Xóa bắt đầu bật lên
const deleteModal = document.getElementById("deleteModal");
if (deleteModal) {
  deleteModal.addEventListener("show.bs.modal", function (event) {
    const button = event.relatedTarget;
    const id = button.getAttribute("data-id");
    
    const form = document.getElementById("deleteForm");
    if (form && id) {
        form.action = `/locations/delete/${id}`;
    }
  });
}