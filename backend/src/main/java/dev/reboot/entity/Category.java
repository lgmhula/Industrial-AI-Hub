package dev.reboot.entity;

/**
 * 产品分类实体 —— XML ResultMap collection 演示（Category → Products 一对多）。
 *
 * @author hula0710
 * @since 2026-07-20
 */
public class Category {

    private Long id;
    private String name;
    private String description;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}