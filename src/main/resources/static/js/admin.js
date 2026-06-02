// 1. Toggle Sidebar (Giữ nguyên)
document.addEventListener('click', event => {
    const toggleBtn = event.target.closest('#sidebarToggle');
    if (toggleBtn) { 
        event.preventDefault();
        document.querySelector('#bdSidebar')?.classList.toggle('toggled'); 
    }
});

// 2. Giữ trạng thái Dropdown (Chỉ cho phép 1 menu mở) và Highlight
document.addEventListener("DOMContentLoaded", function () {
    const collapses = document.querySelectorAll('.sidebar-dropdown');
    const currentUrl = window.location.pathname;

    // --- PHẦN A: XỬ LÝ HIGHLIGHT & TỰ ĐỘNG MỞ KHI LOAD TRANG ---
    const sidebarLinks = document.querySelectorAll('.sidebar-dropdown .sidebar-link');
    let isSubmenuActive = false;

    sidebarLinks.forEach(link => {
        const href = link.getAttribute('href');
        
        // Nếu URL của link trùng khớp với URL trang hiện tại
        if (href !== '#' && currentUrl.includes(href)) {
            isSubmenuActive = true;
            link.style.color = "#fff"; 
            link.style.fontWeight = "bold";

            const parentCollapse = link.closest('.sidebar-dropdown');
            if (parentCollapse) {
                // Ghi đè biến localStorage thành menu hiện tại
                localStorage.setItem('sidebar_active_dropdown', parentCollapse.id);
                
                // Mở menu chứa link này
                parentCollapse.classList.add('show');
                const toggleBtn = document.querySelector(`[data-bs-target="#${parentCollapse.id}"]`);
                if (toggleBtn) {
                    toggleBtn.classList.remove('collapsed');
                    toggleBtn.setAttribute('aria-expanded', 'true');
                }
            }
        }
    });

    // --- PHẦN B: PHỤC HỒI TRẠNG THÁI NẾU KHÔNG CÓ MENU NÀO ACTIVE TỪ URL ---
    collapses.forEach(collapse => {
        const id = collapse.id;
        
        // Chỉ mở theo LocalStorage nếu trang hiện tại không có submenu nào được highlight ở Phần A
        if (!isSubmenuActive && localStorage.getItem('sidebar_active_dropdown') === id) {
            collapse.classList.add('show');
            const toggleBtn = document.querySelector(`[data-bs-target="#${id}"]`);
            if (toggleBtn) {
                toggleBtn.classList.remove('collapsed');
                toggleBtn.setAttribute('aria-expanded', 'true');
            }
        }

        // Lắng nghe sự kiện KHI BẮT ĐẦU MỞ menu (show.bs.collapse)
        collapse.addEventListener('show.bs.collapse', function () {
            // Lưu ID của menu đang mở vào LocalStorage
            localStorage.setItem('sidebar_active_dropdown', this.id);

            // Tìm tất cả các menu khác và ép chúng đóng lại
            collapses.forEach(otherCollapse => {
                if (otherCollapse !== collapse && otherCollapse.classList.contains('show')) {
                    // Dùng API của Bootstrap 5 để đóng an toàn (kèm theo hiệu ứng)
                    const bsCollapse = bootstrap.Collapse.getInstance(otherCollapse);
                    if (bsCollapse) {
                        bsCollapse.hide();
                    } else {
                        // Fallback nếu Bootstrap API chưa sẵn sàng
                        otherCollapse.classList.remove('show');
                        const otherBtn = document.querySelector(`[data-bs-target="#${otherCollapse.id}"]`);
                        if (otherBtn) {
                            otherBtn.classList.add('collapsed');
                            otherBtn.setAttribute('aria-expanded', 'false');
                        }
                    }
                }
            });
        });

        // Lắng nghe sự kiện KHI MENU ĐÓNG (bởi người dùng tự bấm)
        collapse.addEventListener('hidden.bs.collapse', function () {
            // Nếu menu vừa đóng chính là menu đang lưu trong LocalStorage thì xóa đi
            if (localStorage.getItem('sidebar_active_dropdown') === this.id) {
                localStorage.removeItem('sidebar_active_dropdown');
            }
        });
    });

    // --- PHẦN C: XÓA TRẠNG THÁI NẾU BẤM VÀO MENU ĐƠN ---
    // (Xử lý trường hợp người dùng bấm vào các mục không có dropdown, ví dụ: Dashboard, thì thu hết các dropdown cũ lại)
    const singleLinks = document.querySelectorAll('.sidebar-item > a:not([data-bs-toggle="collapse"])');
    singleLinks.forEach(singleLink => {
        singleLink.addEventListener('click', () => {
            localStorage.removeItem('sidebar_active_dropdown');
        });
    });
});