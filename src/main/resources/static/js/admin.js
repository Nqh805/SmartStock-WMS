// 1. Toggle Sidebar (Giữ nguyên)
document.addEventListener('click', event => {
    const toggleBtn = event.target.closest('#sidebarToggle');
    if (toggleBtn) { 
        event.preventDefault();
        document.querySelector('#bdSidebar')?.classList.toggle('toggled'); 
    }
});

// 2. Giữ trạng thái Dropdown và Highlight
document.addEventListener("DOMContentLoaded", function () {
    const collapses = document.querySelectorAll('.sidebar-dropdown');
    const currentUrl = window.location.pathname;

    // --- FIX LỖI: RESET TRẠNG THÁI KHI Ở TRANG DASHBOARD ---
    // Lưu ý: Thay đổi '/', '/dashboard' thành URL thực tế trang Dashboard của bạn
    const isDashboard = currentUrl === '/' || currentUrl === '/dashboard' || currentUrl.endsWith('index.html');
    if (isDashboard) {
        localStorage.removeItem('sidebar_active_dropdown');
    }

    // --- PHẦN A: XỬ LÝ HIGHLIGHT & TỰ ĐỘNG MỞ KHI LOAD TRANG (Có link khớp) ---
    const sidebarLinks = document.querySelectorAll('.sidebar-dropdown .sidebar-link');
    let isSubmenuActive = false;

    sidebarLinks.forEach(link => {
        const href = link.getAttribute('href');
        
        // Thêm điều kiện href !== '/' để tránh lỗi includes() nhận diện nhầm tất cả các link
        if (href && href !== '#' && href !== '/' && currentUrl.includes(href)) {
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

    // --- PHẦN B: PHỤC HỒI TRẠNG THÁI TỪ LOCALSTORAGE ---
    collapses.forEach(collapse => {
        const id = collapse.id;
        
        // Chỉ mở khi: Không ở trang dashboard VÀ không có menu nào được highlight ở Phần A VÀ có lưu trong storage
        if (!isDashboard && !isSubmenuActive && localStorage.getItem('sidebar_active_dropdown') === id) {
            collapse.classList.add('show');
            const toggleBtn = document.querySelector(`[data-bs-target="#${id}"]`);
            if (toggleBtn) {
                toggleBtn.classList.remove('collapsed');
                toggleBtn.setAttribute('aria-expanded', 'true');
            }
        }

        // Lắng nghe sự kiện KHI BẮT ĐẦU MỞ menu (show.bs.collapse)
        collapse.addEventListener('show.bs.collapse', function () {
            localStorage.setItem('sidebar_active_dropdown', this.id);

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

        // Lắng nghe sự kiện KHI MENU ĐÓNG
        collapse.addEventListener('hidden.bs.collapse', function () {
            if (localStorage.getItem('sidebar_active_dropdown') === this.id) {
                localStorage.removeItem('sidebar_active_dropdown');
            }
        });
    });

    // --- PHẦN C: XÓA TRẠNG THÁI NẾU CLICK VÀO MENU ĐƠN ---
    const singleLinks = document.querySelectorAll('.sidebar-item > a:not([data-bs-toggle="collapse"])');
    singleLinks.forEach(singleLink => {
        singleLink.addEventListener('click', () => {
            localStorage.removeItem('sidebar_active_dropdown');
        });
    });
});