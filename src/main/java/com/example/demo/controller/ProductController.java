package com.example.demo.controller;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.example.demo.entity.Product.Category;
import com.example.demo.entity.Product.Product;
import com.example.demo.entity.Product.ProductStatus;
import com.example.demo.repository.ImportBatchRepository;
import com.example.demo.repository.ProductRepository;
import com.example.demo.service.CategoryService;
import com.example.demo.service.ProductService;
import org.springframework.ui.Model;

@Controller
@RequestMapping("/products") // localhost:8080/products
public class ProductController {

    @Autowired
    private ProductService productService;

    @Autowired
    private CategoryService categoryService;

    @Autowired
    private ImportBatchRepository importBatchRepository;

    @Autowired
    private ProductRepository productRepository;

    // API sinh barcode
    @GetMapping("/generate-barcode")
    @ResponseBody
    public String generateBarcode() {
        return productService.generateUniqueBarcode();
    }

    // API lấy child categories theo parent ID
    @PostMapping("/get-child-categories")
    @ResponseBody
    public List<Category> getChildCategories(@RequestParam Long parentId) {
        return categoryService.getChildCategoriesByParentId(parentId);
    }

    // API danh sách sản phẩm
    @GetMapping("/view")
    public String viewProducts(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long parentId,
            @RequestParam(required = false) Long childId,
            @RequestParam(value = "minPrice", required = false) Double minPrice,
            @RequestParam(value = "maxPrice", required = false) Double maxPrice,
            // sort mặc định theo id giảm dần
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "desc") String direction,
            Model model) {

        // 1. Thực hiện lọc và lấy đối tượng Page
        Page<Product> pageData = productService.findProduct(keyword, parentId, childId, minPrice, maxPrice, sortBy,
                direction, page);

        // 2. TÍNH TỔNG TỒN KHO TỪ BATCH VÀ GÁN VÀO TRƯỜNG ẢO CỦA PRODUCT
        List<Product> productList = pageData.getContent();
        for (Product p : productList) {
            Integer total = importBatchRepository.sumQuantityByProductId(p.getId());
            int qty = total != null ? total : 0;
            p.setTotalQuantity(qty);
            productService.updateStatusIfNeeded(p, qty);
        }

        // 3. Gửi dữ liệu phân trang về cho View
        model.addAttribute("products", productList); // Gửi list đã được gắn số lượng
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", pageData.getTotalPages());
        model.addAttribute("totalItems", pageData.getTotalElements());

        // ... (Từ đoạn trạng thái sắp xếp số 3 trở đi giữ nguyên như cũ của bạn)
        model.addAttribute("sortBy", sortBy);
        model.addAttribute("direction", direction);
        model.addAttribute("reverseDirection", direction.equals("asc") ? "desc" : "asc");

        model.addAttribute("parentCategories", categoryService.getAllParentCategories());
        model.addAttribute("childCategories", categoryService.getAllChildCategories());

        model.addAttribute("keyword", keyword);
        model.addAttribute("selectedParent", parentId);
        model.addAttribute("selectedChild", childId);
        model.addAttribute("minPrice", minPrice);
        model.addAttribute("maxPrice", maxPrice);

        return "product_list";
    }

    // 1. API HIỂN THỊ FORM THÊM SẢN PHẨM (MỞ LẦN ĐẦU) - load data lên các dropdown
    @GetMapping("/add")
    public String showAddForm(Model model) {
        model.addAttribute("parentCategories", categoryService.getAllParentCategories());
        model.addAttribute("childCategories", categoryService.getAllChildCategories());
        model.addAttribute("allStatuses", ProductStatus.values());
        model.addAttribute("product", new Product());
        return "add_product";
    }

    // 3. API LƯU SẢN PHẨM VÀO DATABASE
    @PostMapping("/add")
    public String processAddProduct(
            RedirectAttributes redirectAttributes,
            @ModelAttribute("product") Product product,
            Model model,
            @RequestParam(value = "parentCategory", required = false) Long parentId,
            @RequestParam(value = "productType", required = false) Long childId,
            @RequestParam(value = "inputNewCategory", required = false) String newParentName,
            @RequestParam(value = "inputNewType", required = false) String newTypeName,
            @RequestParam(value = "productImage", required = false) MultipartFile imageFile) {

        try {
            // Xử lý Category
            Category finalChildCategory = categoryService.childCategoryProcess(parentId, childId, newParentName,
                    newTypeName);
            product.setCategory(finalChildCategory);

            productService.saveProduct(product, imageFile);

            redirectAttributes.addFlashAttribute("successMessage", "Đã thêm sản phẩm thành công!");
            return "redirect:/products/view";

        } catch (RuntimeException e) {
            // NẾU CÓ LỖI (Ví dụ: Trùng SKU, Trùng Barcode...) -> Trả lại trang để nhập lại
            model.addAttribute("errorMessage", e.getMessage());
            model.addAttribute("parentCategories", categoryService.getAllParentCategories());
            // Load child categories theo parent đã chọn (nếu có)
            if (parentId != null) {
                model.addAttribute("childCategories", categoryService.getChildCategoriesByParentId(parentId));
            } else {
                model.addAttribute("childCategories", categoryService.getAllChildCategories());
            }
            model.addAttribute("allStatuses", ProductStatus.values());
            return "add_product";
        }
    }

    // API fill data form sửa sản phẩm
    @GetMapping("/edit/{id}")
    public String showEditForm(@PathVariable("id") Long id, Model model) {
        Product product = productService.getById(id);
        if (product == null)
            return "redirect:/products/view";

        model.addAttribute("parentCategories", categoryService.getAllParentCategories());
        // Load child categories theo parent của product hiện tại
        if (product.getCategory() != null && product.getCategory().getParent() != null) {
            model.addAttribute("childCategories",
                    categoryService.getChildCategoriesByParentId(product.getCategory().getParent().getId()));
        } else {
            model.addAttribute("childCategories", categoryService.getAllChildCategories());
        }
        model.addAttribute("allStatuses", ProductStatus.values());
        model.addAttribute("product", product);
        return "add_product";
    }

    // API update sản phẩm
    @PostMapping("/edit/{id}")
    public String updateProduct(@PathVariable("id") Long id,
            RedirectAttributes redirectAttributes,
            @ModelAttribute("product") Product product,
            Model model,
            @RequestParam(value = "productImage", required = false) MultipartFile imageFile,
            @RequestParam(value = "parentCategory", required = false) Long parentId,
            @RequestParam(value = "productType", required = false) Long childId,
            @RequestParam(value = "inputNewCategory", required = false) String newParentName,
            @RequestParam(value = "inputNewType", required = false) String newTypeName) {

        product.setId(id);

        try {
            Category finalCategory = categoryService.childCategoryProcess(parentId, childId, newParentName,
                    newTypeName);
            product.setCategory(finalCategory);
            productService.updateProduct(product, imageFile);

            redirectAttributes.addFlashAttribute("successMessage", "Đã cập nhật thông tin sản phẩm!");
            return "redirect:/products/view";

        } catch (Exception e) {
            model.addAttribute("errorMessage", e.getMessage());
            model.addAttribute("parentCategories", categoryService.getAllParentCategories());
            // Load child categories theo parent đã chọn (nếu có)
            if (parentId != null) {
                model.addAttribute("childCategories", categoryService.getChildCategoriesByParentId(parentId));
            } else {
                model.addAttribute("childCategories", categoryService.getAllChildCategories());
            }
            model.addAttribute("allStatuses", ProductStatus.values());
            model.addAttribute("product", product); // giữ lại data user vừa nhập
            return "add_product";
        }
    }

    // API xóa sản phẩm
    @PostMapping("/delete/{id}")
    public String deleteProduct(@PathVariable("id") Long id, RedirectAttributes redirectAttributes) {
        try {
            productService.deleteProduct(id);
            redirectAttributes.addFlashAttribute("successMessage", "Đã xóa sản phẩm thành công!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Không thể xóa sản phẩm: " + e.getMessage());
        }
        return "redirect:/products/view";
    }

    // lấy giá nhập dự kiến trả sang add purchase
    @GetMapping("/{id}/latest-import-price")
    public ResponseEntity<BigDecimal> getLatestImportPrice(@PathVariable Long id) {
        return productRepository.findById(id)
                .map(product -> {
                    BigDecimal latestPrice = product.getStandardCost() != null ? product.getStandardCost()
                            : BigDecimal.ZERO;
                    return ResponseEntity.ok(latestPrice);
                })
                .orElse(ResponseEntity.notFound().build());
    }
}
