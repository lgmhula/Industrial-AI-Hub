package code.day20;

/**
 * 产品实体 —— 多表关联中"多"的一方。
 *
 * <p>每个 Product 通过 categoryId 关联一个 Category（一对一）。</p>
 *
 * @author hula0710
 * @since 2026-07-18
 */
public class Product {

    /** 产品 ID */
    private Integer id;

    /** 产品名称 */
    private String name;

    /** 价格 */
    private Double price;

    /** 所属分类 ID */
    private Integer categoryId;

    /** 关联的 Category 对象（ResultMap association 填充） */
    private Category category;

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Double getPrice() { return price; }
    public void setPrice(Double price) { this.price = price; }
    public Integer getCategoryId() { return categoryId; }
    public void setCategoryId(Integer categoryId) { this.categoryId = categoryId; }
    public Category getCategory() { return category; }
    public void setCategory(Category category) { this.category = category; }

    @Override
    public String toString() {
        return String.format("Product{id=%d, name='%s', price=%.2f, category=%s}",
                id, name, price, category != null ? category.getName() : "null");
    }
}
