# 外卖

餐饮外卖管理系统，包含 B 端管理后台和 C 端小程序点餐两个入口。基于 Spring Boot 2.7 + MyBatis + Vue 2 的全栈教学项目。

## 快速开始

### 环境准备
- JDK 17
- Maven 3.6+
- MySQL 8（需手动建库并导入表结构）
- Redis（本地 6379）

### 启动后端
`bash
cd back
mvn install -DskipTests
mvn -pl sky-server spring-boot:run
`

### 启动前端
`bash
start nginx.exe
`

### 访问地址
| 入口 | URL |
|------|-----|
| 管理后台 | http://localhost |
| 接口文档（Knife4j/Swagger） | http://localhost:8080/doc.html |

### 数据库
仓库内无 SQL 文件，表结构需另行导入到 sky_take_out 库（共 11 张表）。详见 agent.md。

## 已知未完成功能
见 agent.md 第八节——重点关注：C 端用户登录缺失、微信支付未配置、WebSocket payload 字段拼写错误等。

## 说明
- 前端 front/nginx-1.20.2/html/sky/ 为预编译产物，不携带 Vue 源码。
- 敏感配置存放在 application-dev.yml，已加入 .gitignore。首次使用时需自行创建并填写。
- 配套开发指南见 .qoder/ 目录（已在 .gitignore 中，不随仓库推送）。