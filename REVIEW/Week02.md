# Week 02 复盘（2026-07-11 ~ 2026-07-13）

## 总体数据

| 指标 | 数值 |
|------|------|
| 天数 | 6 天（Day 8 ~ Day 13） |
| 新增 Java 文件 | 12 个 |
| 阶段代码量 | ~2,100 行 |
| Git commits | 10 次 |
| LeetCode | 6 道（206/217/242/125/14） |
| 覆盖知识点 | 集合框架→泛型→异常→IO→字符串→包装类→日期 |

## 知识图谱

```
Day 8:  ArrayList/LinkedList 性能对比 + 通讯录 + Collections 工具
Day 9:  HashSet/HashMap/TreeSet/TreeMap + equals/hashCode 契约
Day 10: 泛型/Comparable/Comparator + 斗地主综合实战
Day 11: String/StringBuilder/包装类/日期时间（独立完成）
Day 12: 异常处理 try-catch-finally/throws/自定义异常（独立完成）
Day 13: String/StringBuilder/包装类/日期（系统化巩固）
```

## 能力增长

- [x] 集合框架选择能力：知道什么时候用 ArrayList/HashMap/TreeSet
- [x] 性能意识：10 万条数据实测，能讲出 O(1) vs O(n) 的实际差距
- [x] equals/hashCode 契约：能写正确的实现，能解释为什么
- [x] 泛型思维：自定义泛型类/方法，理解通配符
- [x] 异常处理：try-catch-finally、throws、自定义异常
- [x] IO 流基础：字节流/字符流/缓冲流
- [x] String 不可变性 + StringBuilder 性能优化
- [x] 独立编码能力：Day 11-12 自主完成（重大里程碑）

## 关键里程碑

**斗地主发牌**（Day 10）—— 第一个综合项目：
54 张牌生成 → 洗牌 → 发牌 → 排序 → 展示，170 行代码串联了泛型、集合、Comparable、Comparator、switch 表达式。

**独立编码**（Day 11-12）—— 最重要的能力信号：
在没有 AI 辅助下完成了异常处理和 IO 流的学习和代码编写。这说明能力在真正恢复。

## 技术债务

1. Day 11-12 代码缺少 package 声明（已修复）
2. Day 8-13 部分文件缺少 DAILY 日志（已补齐）
3. 部分 class 文件误提交（已清理）

## Week 1 vs Week 2 对比

| 维度 | Week 1 | Week 2 |
|------|--------|--------|
| 核心目标 | 恢复基础语法 | 建立工程思维 |
| 代码风格 | 单文件练习 | 多文件 + package |
| 复杂度 | for 循环 | 泛型 + 集合 + 综合项目 |
| 独立性 | 全程 AI 辅助 | Day 11-12 自主完成 |
| LeetCode | 6 道基础 | 6 道进阶 |

## 下一步（第三阶段预告）

- Day 15~17：MySQL 复习 + JDBC + MyBatis
- Day 18~21：SpringBoot 入门 + 工业 AI Hub V1 启动

目标：两周内跑通 Spring Boot + MyBatis + 数据库的完整 CRUD 链路。
