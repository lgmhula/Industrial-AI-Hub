package dev.reboot.entity;

/**
 * 产品实体 —— XML ResultMap association 演示（Product → Category 一对一）。
 *
 * @author hula0710
 * @since 2026-07-20
 */
public class Product {

    private Long id;
    private String name;
    private Double price;
    private Long categoryId;
    private Category category;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Double getPrice() { return price; }
    public void setPrice(Double price) { this.price = price; }
    public Long getCategoryId() { return categoryId; }
    public void setCategoryId(Long categoryId) { this.categoryId = categoryId; }
    public Category getCategory() { return category; }
    public void setCategory(Category category) { this.category = category; }
}