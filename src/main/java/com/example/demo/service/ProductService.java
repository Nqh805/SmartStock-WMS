package com.example.demo.service;

import java.util.List;
import java.util.Random;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import com.example.demo.entity.Product.Product;
import com.example.demo.entity.Product.ProductStatus;
import com.example.demo.repository.ImportBatchRepository;
import com.example.demo.repository.ProductRepository;

@Service
public class ProductService {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ImportBatchRepository importBatchRepository;

    // tính tổng tồn kho sản phẩm bằng tổng tồn trong tất cả các lô
    private void loadInventoryAndStatus(Product p) {
        Integer total = importBatchRepository.sumQuantityByProductId(p.getId());
        int qty = total != null ? total : 0;

        p.setTotalQuantity(qty);
        updateStatusIfNeeded(p, qty);
    }

    // tim va tra ve danh sach sort theo id giam dan
    public Page<Product> findProduct(String keyword, Long parentId, Long childId,
            Double minPrice, Double maxPrice,
            String sortBy, String direction, int pageNum) {

        String cleanKeyword = (keyword != null && !keyword.trim().isEmpty()) ? keyword.trim() : null;
        Long pId = (parentId != null && parentId > 0) ? parentId : null;
        Long cId = (childId != null && childId > 0) ? childId : null;

        Sort.Direction dir = "asc".equalsIgnoreCase(direction) ? Sort.Direction.ASC : Sort.Direction.DESC;

        Sort sort;
        if ("status".equals(sortBy)) {
            sort = Sort.by(dir, "status");
        } else if (sortBy != null && !sortBy.isEmpty()) {
            sort = Sort.by(dir, sortBy);
        } else {
            sort = Sort.by(Sort.Direction.DESC, "id");
        }

        int pageSize = 10;
        Pageable pageable = PageRequest.of(pageNum - 1, pageSize, sort);

        Page<Product> productPage = productRepository.searchProducts(cleanKeyword, pId, cId, minPrice, maxPrice,
                pageable);

        for (Product p : productPage.getContent()) {
            loadInventoryAndStatus(p);
        }

        return productPage;
    }

    public Product getById(Long id) {
        return productRepository.findById(id).orElse(null);
    }

    private String saveImage(MultipartFile imageFile) {
        if (imageFile == null || imageFile.isEmpty()) {
            return null;
        }

        try {
            String projectPath = "src/main/resources/static/asset/uploads/";
            String buildPath = "target/classes/static/asset/uploads/";
            String fileName = imageFile.getOriginalFilename();

            new java.io.File(projectPath).mkdirs();
            new java.io.File(buildPath).mkdirs();

            java.nio.file.Path pathInProject = java.nio.file.Paths.get(projectPath + fileName);
            java.nio.file.Path pathInBuild = java.nio.file.Paths.get(buildPath + fileName);

            java.nio.file.Files.copy(imageFile.getInputStream(), pathInProject,
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            java.nio.file.Files.copy(imageFile.getInputStream(), pathInBuild,
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);

            return "/asset/uploads/" + fileName;

        } catch (java.io.IOException e) {
            throw new RuntimeException("Lỗi hệ thống khi lưu file ảnh: " + e.getMessage());
        }
    }

    private int calculateEAN13CheckDigit(String code12) {
        int sum = 0;
        for (int i = 0; i < 12; i++) {
            int digit = Character.getNumericValue(code12.charAt(i));
            sum += (i % 2 == 0) ? digit : digit * 3;
        }
        int mod = sum % 10;
        return (mod == 0) ? 0 : 10 - mod;
    }

    private boolean isValidEAN13(String barcode) {
        if (barcode == null || !barcode.matches("\\d{13}")) {
            return false;
        }
        String code12 = barcode.substring(0, 12);
        int expectedCheckDigit = calculateEAN13CheckDigit(code12);
        int actualCheckDigit = Character.getNumericValue(barcode.charAt(12));

        return expectedCheckDigit == actualCheckDigit;
    }

    private String generateEAN13() {
        StringBuilder code12 = new StringBuilder("20");
        Random random = new Random();
        for (int i = 0; i < 10; i++) {
            code12.append(random.nextInt(10));
        }
        int checkDigit = calculateEAN13CheckDigit(code12.toString());
        return code12.toString() + checkDigit;
    }

    public String generateUniqueBarcode() {
        String barcode;
        do {
            barcode = generateEAN13();
        } while (productRepository.existsByBarCode(barcode));
        return barcode;
    }

    private void validateUniqueness(String skuCode, String barCode, Long excludeId) {
        if (skuCode != null) {
            boolean skuExists = (excludeId == null)
                    ? productRepository.existsBySkuCode(skuCode)
                    : productRepository.existsBySkuCodeAndIdNot(skuCode, excludeId);
            if (skuExists)
                throw new RuntimeException("Mã SKU '" + skuCode + "' đã tồn tại!");
        }

        if (barCode != null) {
            if (!isValidEAN13(barCode))
                throw new RuntimeException("Mã Vạch không hợp lệ (Sai định dạng hoặc số kiểm tra)!");

            boolean barCodeExists = (excludeId == null)
                    ? productRepository.existsByBarCode(barCode)
                    : productRepository.existsByBarCodeAndIdNot(barCode, excludeId);
            if (barCodeExists)
                throw new RuntimeException("Mã Vạch '" + barCode + "' đã bị trùng!");
        }
    }

    // update status theo số lượng tồn
    private ProductStatus determineStatusByInventory(Product product, int totalQuantity) {
        if (product.getStatus() == ProductStatus.INACTIVE) {
            return ProductStatus.INACTIVE;
        }
        if (totalQuantity <= 0) {
            return ProductStatus.OUT_OF_STOCK;
        }
        Integer minStockLevel = product.getMinStockLevel();
        if (minStockLevel != null && totalQuantity <= minStockLevel) {
            return ProductStatus.LOW_STOCK;
        }
        return ProductStatus.ACTIVE;
    }

    public void updateStatusIfNeeded(Product product, int totalQuantity) {
        ProductStatus newStatus = determineStatusByInventory(product, totalQuantity);
        if (product.getStatus() != newStatus) {
            product.setStatus(newStatus);
            productRepository.save(product);
        }
    }

    public Product saveProduct(Product product, MultipartFile imageFile) {
        validateUniqueness(product.getSkuCode(), product.getBarCode(), null);

        if (product.getUnit() == null || product.getUnit().trim().isEmpty()) {
            product.setUnit("Cái");
        }
        if (product.getWarrantyMonths() == null) {
            product.setWarrantyMonths(0);
        }
        if (product.getMinStockLevel() == null) {
            product.setMinStockLevel(0);
        }
        if (product.getTaxRate() == null) {
            product.setTaxRate(new java.math.BigDecimal("10.00"));
        }

        String imageUrl = saveImage(imageFile);
        if (imageUrl != null)
            product.setImgURL(imageUrl);

        return productRepository.save(product);
    }

    public void updateProduct(Product product, MultipartFile imageFile) {
        Product existingProduct = productRepository.findById(product.getId())
                .orElseThrow(() -> new RuntimeException("Sản phẩm không tồn tại!"));

        validateUniqueness(product.getSkuCode(), product.getBarCode(), product.getId());

        if (imageFile != null && !imageFile.isEmpty())
            existingProduct.setImgURL(saveImage(imageFile));

        updateFields(existingProduct, product);
        productRepository.save(existingProduct);
    }

    private void updateFields(Product target, Product source) {
        target.setName(source.getName());
        target.setBasePrice(source.getBasePrice());
        target.setStandardCost(source.getStandardCost());
        target.setCostPrice(source.getCostPrice());
        target.setUnit(source.getUnit());
        target.setBrand(source.getBrand());
        target.setWarrantyMonths(source.getWarrantyMonths());
        target.setMinStockLevel(source.getMinStockLevel());
        target.setTaxRate(source.getTaxRate());
        target.setTechnicalSpecs(source.getTechnicalSpecs());
        target.setSkuCode(source.getSkuCode());
        target.setBarCode(source.getBarCode());
        target.setDescription(source.getDescription());
        target.setStatus(source.getStatus());
        target.setCategory(source.getCategory());
    }

    public void deleteProduct(Long id) {
        if (productRepository.existsById(id)) {
            productRepository.deleteById(id);
        } else {
            throw new RuntimeException("Sản phẩm không tồn tại.");
        }
    }

    // xu ly so luong ton kho tong
    public List<Product> getAllProductsWithInventory() {
        List<Product> products = productRepository.findAll();
        for (Product p : products) {
            loadInventoryAndStatus(p);
        }
        return products;
    }
}