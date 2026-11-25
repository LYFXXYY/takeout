# Sky Takeout · 苍穹外卖

餐饮外卖管理系统，覆盖 **B 端管理后台**（运营侧 Web）与 **C 端小程序点餐**（接口侧）两个入口。后端采用 Spring Boot 多模块分层架构，配套 Redis 缓存、WebSocket 实时推送与定时任务，前端由 nginx 托管并通过反向代理对接后端 API。

![Java](https://img.shields.io/badge/Java-17-orange) ![Spring Boot](https://img.shields.io/badge/Spring%20Boot-2.7.3-green) ![MySQL](https://img.shields.io/badge/MySQL-8.0-blue) ![Redis](https://img.shields.io/badge/Redis-ready-red)

## 功能总览

**管理后台（B 端）**

- 员工管理：登录、分页查询、新增/编辑、账号启停用
- 分类管理：菜品/套餐分类的增删改查、分页、启停用
- 菜品管理：含口味定制、图片上传（阿里云 OSS）、起售停售、按分类查询，Redis 缓存加速 C 端读取
- 套餐管理：套餐与菜品关联维护、Spring Cache 注解缓存、启用前自动校验所含菜品状态
- 订单管理：条件分页查询、每日运营数据统计（完单量/营业额/有效订单）、接单/拒单/派送/完成全状态流转
- 运营辅助：营业状态一键切换（Redis 存储）、WebSocket 实时推送来单与催单提醒、超时未付款自动取消/派送超时自动完成的定时任务、工作台数据概览

**用户端（C 端）**

- 分类/菜品/套餐浏览、按条件查询套餐内菜品
- 购物车增删改查、下单、历史订单分页、订单详情、催单、取消
- 地址簿管理（新增/修改/删除/默认地址）

## 技术选型

| 分类 | 技术 | 说明 |
|------|------|------|
| 核心框架 | Spring Boot 2.7.3 | 多模块 Maven 工程 |
| 数据访问 | MyBatis + PageHelper | 注解 SQL 与动态 XML 混合 |
| 连接池 | Druid | |
| 缓存 | Redis（Spring Cache / RedisTemplate 双模式） | 套餐走注解缓存，菜品走手动缓存清理 |
| 鉴权 | JWT（jjwt） | 拦截器校验 + ThreadLocal 上下文 |
| 实时通信 | WebSocket（JSR-356） | 服务端向管理后台推送订单事件 |
| 接口文档 | Knife4j（Swagger） | 按 B/C 端分组 |
| 对象存储 | 阿里云 OSS SDK | 菜品/套餐图片 |
| 前端 | Vue 2 + Element UI | 预编译产物，nginx 1.20 托管与反向代理 |

## 项目结构

```
sky-takeout/
├── back/
│   ├── sky-common/          # 公共层：统一响应 Result、业务异常体系、常量、
│   │                        #   JWT 工具、配置属性类、BaseContext 线程上下文
│   ├── sky-pojo/            # 数据模型：Entity / DTO / VO
│   └── sky-server/          # 主应用：controller(admin|user) / service / mapper /
│                            #   config / interceptor / aspect / task / websocket
├── front/nginx-1.20.2/      # nginx + 管理后台前端静态资源（html/sky）
├── sql/sky_take_out.sql     # 建库建表与基础数据脚本
└── .gitignore
```

## 快速开始

**前置环境**：JDK 17、Maven 3.6+、MySQL 8、Redis

```bash
# 1) 导入数据库（自动创建 sky_take_out 库，11 张表 + 基础数据）
mysql -u root -p < sql/sky_take_out.sql

# 2) 生成后端私有配置（数据源/Redis/OSS 填入真实值，该文件不入库）
cp back/sky-server/src/main/resources/application-dev.example.yml \
   back/sky-server/src/main/resources/application-dev.yml

# 3) 启动后端（首次需 install 安装子模块到本地仓库），监听 8080
mvn -f back/pom.xml install -DskipTests
mvn -f back/pom.xml -pl sky-server spring-boot:run

# 4) 启动前端（Windows），监听 88
cd front/nginx-1.20.2
start nginx.exe            # 重载：nginx.exe -s reload  停止：nginx.exe -s quit
```

**访问入口**

| 入口 | 地址 | 说明 |
|------|------|------|
| 管理后台 | http://localhost:88 | 默认账号 `admin`，密码 `123456` |
| 接口文档 | http://localhost:8080/doc.html | Knife4j，含管理端接口分组 |

**运行测试**

```bash
mvn -f back/pom.xml -pl sky-server -am test
```

## 设计要点

- **统一响应与异常体系**：`Result<T>{code,msg,data}` 收口所有出参；业务异常继承 `BaseException`，由全局异常处理器统一转换，控制器保持"薄"。
- **鉴权链路**：JWT 拦截器校验管理端令牌，解析出的员工 ID 写入 `BaseContext`（ThreadLocal），供后续业务层无参透传。
- **审计字段自动填充**：自定义 `@Autofill` 注解 + AOP 切面，反射填充创建/更新时间与操作人，业务代码零散落 `set`。
- **双缓存风格对照**：套餐使用 `@Cacheable/@CacheEvict` 声明式缓存，菜品使用 `RedisTemplate` 按 `dish_{categoryId}` 前缀手动清理——同一系统内两种缓存策略的选型对比。
- **订单自动化闭环**：下单写订单 + 明细 + 清购物车，定时任务兜底超时单，WebSocket 实时通知后台，覆盖外卖业务的典型状态机场景。
- **多模块分层**：`common → pojo → server` 单向依赖，公共能力下沉，模型层与业务层分离。

---

*本项目基于 Spring Boot 技术栈实现，前端资源为预编译产物；数据库脚本位于 `sql/` 目录，可一键重建完整环境。*
