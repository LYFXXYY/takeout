# 外卖

餐饮外卖管理系统，包含 B 端管理后台和 C 端小程序点餐两个入口。基于 Spring Boot 2.7 + MyBatis + Vue 2 的全栈教学项目。

## 快速开始

### 环境准备
- JDK 17
- Maven 3.6+
- MySQL 8（需手动建库并导入表结构）
- Redis（本地 6379）

### 启动后端
```bash
cd back
mvn install -DskipTests
mvn -pl sky-server spring-boot:run
```

### 启动前端
```bash
cd front/nginx-1.20.2
start nginx.exe
```

### 访问地址
| 入口 | URL |
|------|-----|
| 管理后台 | http://localhost |
| 接口文档（Knife4j/Swagger） | http://localhost:8080/doc.html |

### 运行单元测试
```bash
cd back
mvn -pl sky-server -am test
```
5 个单元测试类共 17 例，2026-11-16 实测全部通过。（曾有 2 个连环境的遗留草稿测试已删除；若删除测试后仍见旧用例失败，清一次 `sky-server/target/test-classes` 再跑。）

### 数据库
仓库内无 SQL 文件，表结构需另行导入到 sky_take_out 库（共 11 张表）。详见 agent.md。

## 已知未完成功能
见 agent.md 第八节——重点关注：C 端用户登录/鉴权整体缺失（`/user/**` 无拦截器）、微信支付未配置、下单未加事务、WebSocket payload 字段拼写错误等。

## 说明
- 前端 front/nginx-1.20.2/html/sky/ 为预编译产物（仅管理后台，页面标题仍是旧品牌"瑞吉外卖"），不携带 Vue 源码；C 端小程序不在本仓库内。
- 敏感配置存放在 application-dev.yml，已加入 .gitignore。首次使用时需自行创建并填写。
- 配套开发指南见 .qoder/ 目录（已在 .gitignore 中，不随仓库推送）。
