package dev.reboot.mapper;

import dev.reboot.entity.Product;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * Product XML Mapper 接口 —— 配合 mapper/ProductMapper.xml 使用。
 *
 * <p>不写 @Select，所有 SQL 由 XML 定义。</p>
 *
 * @author hula0710
 * @since 2026-07-20
 */
@Mapper
public interface ProductMapper {

    /** 查询产品及关联分类（association 一对一） */
    Product selectProductWithCategory(Long id);

    /** 查询所有产品及分类 */
    List<Product> selectAllWithCategory();

    /** 按分类 ID 查产品 */
    List<Product> selectByCategoryId(Long categoryId);
}