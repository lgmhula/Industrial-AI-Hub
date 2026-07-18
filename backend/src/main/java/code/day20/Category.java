package code.day20;

/**
 * 产品分类实体 —— 多表关联中"一"的一方。
 *
 * <p>典型用法：一个 Category 下有多个 Product（一对多）。</p>
 *
 * @author hula0710
 * @since 2026-07-18
 */
public class Category {

    /** 分类 ID */
    private Integer id;

    /** 分类名称 */
    private String name;

    /** 分类描述 */
    private String description;

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    @Override
    public String toString() {
        return String.format("Category{id=%d, name='%s', description='%s'}", id, name, description);
    }
}
