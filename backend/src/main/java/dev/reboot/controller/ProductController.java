package dev.reboot.controller;

import dev.reboot.common.ApiResponse;
import dev.reboot.entity.Product;
import dev.reboot.mapper.ProductMapper;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Product REST 控制器 —— 演示 XML Mapper + ResultMap association。
 *
 * @author hula0710
 * @since 2026-07-20
 */
@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductMapper productMapper;

    public ProductController(ProductMapper productMapper) {
        this.productMapper = productMapper;
    }

    @GetMapping
    public ApiResponse<List<Product>> list() {
        return ApiResponse.success(productMapper.selectAllWithCategory());
    }

    @GetMapping("/{id}")
    public ApiResponse<Product> getById(@PathVariable Long id) {
        Product p = productMapper.selectProductWithCategory(id);
        if (p == null) {
            throw new RuntimeException("Product not found: " + id);
        }
        return ApiResponse.success(p);
    }

    @GetMapping("/category/{categoryId}")
    public ApiResponse<List<Product>> getByCategory(@PathVariable Long categoryId) {
        return ApiResponse.success(productMapper.selectByCategoryId(categoryId));
    }
}
