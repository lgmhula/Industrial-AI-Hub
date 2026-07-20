# ADR-0008: Spring Boot 3.5 替代独立 MyBatis

**日期:** 2026-07-19  
**状态:** Accepted  
**决策者:** hula0710

## 背景

Day 1~20 使用独立 MyBatis（手工 `SqlSessionFactoryBuilder` + `exec-maven-plugin`），
不适合作为长期项目基础。

## 决策

Day 21 起，项目正式迁移至 Spring Boot 3.5。

## 理由

1. Spring Boot 提供自动装配（DataSource / SqlSessionFactory / TransactionManager）
2. `@Mapper` + 构造器注入替代手工 `SqlSession.selectList()`
3. 内嵌 Tomcat 提供标准 REST API，无需额外 HTTP Server
4. 2026 年 Java 生态的事实标准，面试必备
5. 与后续 Spring Cloud / Spring Security 体系一致

## 后果

- pom.xml 父 POM 改为 `spring-boot-starter-parent:3.5.0`
- 移除 `exec-maven-plugin`，使用 `spring-boot:run`
- 旧 `mybatis-config.xml` 配置文件退化为学习参考（不再被加载）
