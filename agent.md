# agent.md — 苍穹外卖（sky-takeout）项目交接文档

> 面向接手本项目的开发者 / AI 助手。本文描述项目**当前真实状态**（2026-09 核对），而非历史模板。
> 配套文件：`.qoder/PROJECT.md`（速览）、`.qoder/ISSUES.md`（问题记录）。注意 `.qoder/` 与 `application-dev.yml` 均已被 gitignore，仅存本地。

## 一、项目概述

餐饮外卖管理系统，分两个使用端：B 端管理后台（员工/分类/菜品/套餐/订单/营业状态/来单提醒）和 C 端小程序点餐（分类浏览/菜品套餐查询/购物车/下单/地址簿）。后端为 Spring Boot 多模块工程，前端为**预编译**的 Vue SPA（打包产物，非源码），由内置 nginx 托管并做反向代理。

这是"黑马/苍穹"教学体系的经典项目，数据模型和分层架构完整，但部分功能仍处于半成品或未配置状态（见第八、九节）。

## 二、技术栈与版本

后端：Spring Boot **2.7.3**（JDK 17 运行，POM 未显式声明 java.version）、MyBatis-Spring 2.2.0、Druid 1.2.1、PageHelper 1.3.0、MySQL（driver `com.mysql.cj.jdbc.Driver`）、Spring Data Redis + Spring Cache、WebSocket（JSR-356）、JWT（jjwt 0.9.1，HS256）、Knife4j 3.0.2（ springfox）、阿里云 OSS SDK 3.18.4、微信支付 wechatpay-apache-httpclient 0.4.8、Apache POI 3.16、Fastjson 1.2.76、Lombok 1.18.20、Hutool 5.8.38。

> 父 POM 的 dependencyManagement 里声明了 MyBatis-Plus 3.5.17，但**代码并未使用**，数据访问实际是原生 MyBatis + PageHelper。接手时不要误以为用了 MP。

前端：Vue 2 + Element UI 预编译产物；nginx 1.20.2（Windows 便携版）。

## 三、仓库结构与模块划分

```
sky-takeout/
  back/                      # Maven 多模块后端
    pom.xml                  # 父 POM：版本管理、编码属性(UTF-8)、modules
    sky-common/              # 公共层：常量、异常、工具、配置属性、Result、BaseContext、@Autofill+AOP
    sky-pojo/                # 纯 POJO：11 Entity + 21 DTO + 17 VO
    sky-server/              # 可运行主应用：controller / service / mapper / config / interceptor / handler / task / websocket
  front/nginx-1.20.2/        # 前端：nginx + 预编译 Vue SPA（html/sky/）
  .qoder/                    # 本地文档（gitignore）
```

依赖方向：`sky-server → sky-common + sky-pojo`。`sky-pojo` 里显式钉死了 `jackson-databind 2.9.2`（比 Boot 管理的版本旧），是一个潜在冲突点。

## 四、环境准备与启动

前置：JDK 17、Maven、MySQL 8（需已建库并导入表结构）、Redis（本地 6379）。**仓库内没有 .sql 文件**，表结构需另行导入。

```bash
# 1) 后端（首次或改了依赖要先 install 让子模块进本地仓库）
cd D:/Projects/sky-takeout/back
mvn install -DskipTests
mvn -pl sky-server spring-boot:run        # 监听 8080

# 2) 前端 nginx
cd D:/Projects/sky-takeout/front/nginx-1.20.2
start nginx.exe                            # 监听 80（当前值，见第八节端口说明）
# 改配置后热加载：nginx.exe -s reload ；停止：nginx.exe -s quit

# 3) 访问
# 管理后台：http://localhost          （nginx 在 80）
# 接口文档：http://localhost:8080/doc.html   （仅"管理端接口"分组可见，见第八节第5条）
```

配置分文件：`application.yml`（框架无关配置 + `${sky.*}` 占位符）+ `application-dev.yml`（`dev` profile 提供数据源/Redis/OSS 真实值）。默认激活 `dev`。**没有 prod/test profile**。

## 五、架构与请求流程

管理端请求链路：`浏览器 → nginx(/api → /admin) → JwtTokenAdminInterceptor(校验 token，写 BaseContext) → Controller → Service → Mapper(注解 SQL 或 XML) → MySQL`。

关键机制：

- **统一响应**：`Result<T>` = `{code(1成功/0失败), msg, data}`；分页 `PageResult` = `{total, records}`。
- **JWT 拦截器**：`JwtTokenAdminInterceptor` 拦截 `/admin/**`，放行 `/admin/employee/login`。解析 token 把 `empId` 放进 `BaseContext`（ThreadLocal）。
- **自动填充 AOP**：`AutoFillAspect` 拦截标注 `@Autofill(INSERT/UPDATE)` 的 mapper 方法，反射填充 createTime/updateTime/createUser/updateUser（依赖 `BaseContext.getCurrentId()`）。
- **全局异常**：`GlobalExceptionHandler` 捕获 `BaseException` → `Result.error(msg)`；并处理唯一键冲突。
- **缓存**：两种风格并存——套餐用注解 `@Cacheable/@CacheEvict`（缓存名 `setmealCache`，key=categoryId）；菜品用**手动 RedisTemplate**（key=`dish_{categoryId}`）。店铺营业状态直接以 Redis 字符串 `SHOP_STATUS` 存。
- **定时任务**（`@EnableScheduling` 已开）：`OrderTask` 每分钟把超 15 分钟未付款订单自动取消；每天 01:00 把派送超 60 分钟订单置为已完成。
- **WebSocket**：`WebSocketServer` `@ServerEndpoint("/ws/{sid}")`，静态 `sessionMap` 广播；仅服务端→管理端推来单/催单提醒。

## 六、数据库设计

库名 `sky_take_out`，11 张表：`employee, category, dish, dish_flavor, setmeal, setmeal_dish, shopping_cart, orders, order_detail, user, address_book`。

关系：`category 1—N dish`（`dish.category_id`）、`category 1—N setmeal`、`dish 1—N dish_flavor`、`setmeal M—N dish` 经 `setmeal_dish`（含 copies，且冗余 name/price）、`orders 1—N order_detail`（明细冗余 name/image/amount）、`user 1—N shopping_cart / orders / address_book`。

订单状态流转：`1 待付款 → 2 待接单 → 3 已接单 → 4 派送中 → 5 已完成`，`6 已取消`。

## 七、功能实现进度（当前真实状态）

**已完成（后端链路可跑）**：员工登录/增删改查/启停用/分页；分类 CRUD/启停用/按类型查列表；菜品 CRUD（含口味）/分页/起停售/按分类查列表；套餐 CRUD（含套餐菜品关联）/分页/起停售；图片上传（阿里云 OSS）；营业状态切换（Redis）；订单管理端查询/统计/接单/拒单/取消/派送/完成；C 端分类/菜品/套餐浏览、购物车、下单、历史订单、地址簿；WebSocket 来单提醒；超时/配送自动处理的定时任务；菜品 Redis 缓存 + 套餐 Spring Cache。

**未完成 / 半成品（重点交接）**：
- **C 端用户登录与鉴权整体缺失**：无 UserController / 微信登录；`UserMapper` 只有 `getById`+`countByMap`，没有 `getByOpenid`/`insert`；**没有注册 `/user/**` 拦截器**，因此所有 C 端接口拿到的 `BaseContext.getCurrentId()` 为 **null**（详见第八节第 1 条）。
- **微信支付未配置**：所有 yml 里没有 `sky.wechat.*`，`WeChatProperties` 字段全 null，`WeChatPayUtil.getClient()` 读不到证书文件返回 null，支付/退款/回调解密运行时会 NPE（应用能启动，用到才崩）。
- **Excel 数据导出/报表**未实现：POI 依赖 + 一批 ReportVO/DTO 都在，但没有 ReportController/ReportService。
- 员工密码仍是**明文比对**（`EmployeeServiceImpl.login` 有 TODO 待加密），新增员工默认密码 `123456`。

## 八、已知问题与风险清单（务必逐条处理）

1. **【严重·功能阻断】C 端无法识别用户**：`/user/**` 没有 `JwtTokenUserInterceptor`，`BaseContext.getCurrentId()` 恒为 null，购物车/下单/地址簿按 `user_id=null` 查询会全部异常。修复：实现微信登录 → 签发用户 JWT → 注册用户端拦截器 → 拦截器里 set 且 **在 afterCompletion 清理 ThreadLocal**。
2. **【严重·运行时崩溃】微信支付未配置**：见第七节。补齐 `sky.wechat` 配置或先用 mock。
3. **【严重·数据一致性】`OrderServiceImpl.submitOrder` 没有 `@Transactional`**：一次写了 orders + order_detail 又删购物车，中途失败会产生脏数据。建议加事务。
4. **【逻辑 bug】WebSocket 推送 payload 拼错**：`OrderServiceImpl` 里 `map.put("content"+"订单号", ...)` 生成的是字面 key `"content订单号"`，且 `"oderId"` 拼错（应为 `orderId`），前端收不到正确字段。
5. **【配置 bug】Knife4j `docket2()`（用户端文档分组）漏了 `@Bean`**：导致 C 端接口在 doc.html 里看不到。
6. **【内存风险】`BaseContext` 的 ThreadLocal 从不调用 `removeCurrentId()`**：线程池复用下有泄漏风险，配合第 1 条一起修。
7. **【脆弱实现】`GlobalExceptionHandler` 唯一键冲突解析用 `message.split("")`**（按空串分割=逐字符），取 `split[2]` 提取用户名极不可靠。
8. **【安全·需立即处理】明文密钥落盘**：`application-dev.yml` 含真实阿里云 AccessKey ID/Secret、MySQL 密码、Redis 密码。该文件已被 gitignore 且已从 git 历史清除，但**密钥仍在本机明文存在，建议到阿里云控制台轮换**，并改用环境变量/密钥管理注入。
9. **【文档/配置不一致】nginx 端口**：`ISSUES.md`/旧记录说改成了 88，但当前 `nginx.conf` 实际是 **80**。若改回 80，注意历史上 80 曾被 `Steam++.Accelerator` 占用导致 nginx `bind 10013` 失败；且前端 WebSocket 地址在编译好的 JS 里**硬编码为 `ws://localhost/ws/`（默认 80）**，一旦把 nginx 挪到其他端口，来单提醒会连不上。

## 九、API 接口速查（管理端 `/admin`，经 nginx 时前端用 `/api` 前缀）

管理端（JWT 保护）：员工 `/admin/employee`（login/logout/新增/`/page`/`/status/{status}`/`/{id}`/PUT 编辑）；分类 `/admin/category`（POST/`/page`/DELETE/PUT/`/status/{status}`/`/list`）；菜品 `/admin/dish`（POST/`/page`/DELETE/`/{id}`/PUT/`/list`/`/status/{status}`）；套餐 `/admin/setmeal`（POST/`/page`/DELETE/`/{id}`/PUT/`/status/{status}`）；订单 `/admin/order`（`/conditionSearch`/`/statistics`/`/details/{id}`/`/confirm`/`/rejection`/`/cancel`/`/delivery/{id}`/`/complete/{id}`）；工作台 `/admin/workspace`（`/businessData`/`/overviewOrders`/`/overviewDishes`/`/overviewSetmeals`）；文件上传 `/admin/common/upload`；店铺 `/admin/shop/{status}` 与 `/admin/shop/status`。

用户端（当前无鉴权，见第八节）：`/user/category/list`、`/user/dish/list`、`/user/setmeal/list` 与 `/user/setmeal/dish/{id}`、`/user/shop/status`、`/user/addressBook/*`、`/user/shoppingCart/*`（add 用 **PUT**）、`/user/order/*`（submit/payment 用 **PUT**，`/historyOrders`、`/orderDetail/{id}`、`/cancel/{id}`、`/repetition/{id}`、`/reminder/{id}`）。回调：`/notify/paySuccess`。

nginx 代理：`/api/ → localhost:8080/admin/`，`/user/ → webservers/user/`，`/ws/ → webservers/ws/`（带 Upgrade 头，read timeout 3600s）。

## 十、编码规范（延续本项目风格）

分层清晰：Controller 只做参数与响应封装，业务在 Service，数据访问在 Mapper（简单 SQL 用注解、动态/复杂 SQL 用 `resources/mapper/*.xml`）。实体/DTO/VO 分离，DTO 入参、VO 出参、Entity 对表；跨层用 `BeanUtils.copyProperties`。统一 `Result` 返回。写多表的业务方法加 `@Transactional`。公共字段（时间/操作人）通过 `@Autofill` + AOP 填充，不在业务里散落 set。命名语义化、无硬编码魔法值（状态用 `StatusConstant`、提示语用 `MessageConstant`）。异步操作 try/catch 并日志，异常抛 `BaseException` 子类交全局处理。文件按功能/领域组织，单文件保持精炼。

## 十一、踩坑记录（构建与运行环境，Windows）

- **YAML 中文注释被 GBK 损坏** → SnakeYAML 报 `MalformedInputException`。根因两层：Maven CLI 需要父 POM 的 `project.build.sourceEncoding=UTF-8` + `sky-server` 显式配 `maven-resources-plugin` encoding；**IDEA 内部构建器不吃 POM 编码**，需要 `.idea/encodings.xml` 覆盖到 `src/main/resources`。已修。
- **QoderWork 自带 node 进程长期占 3000**，本地若起 Node 服务遇端口占用需换端口并用 curl 直连验证。
- **改代码后端口没更新**：IDEA"重启"可能只热更了前端；后端改了需真正 kill 旧 java 进程再启动，用 `netstat`+进程 StartTime 验证。
- **`rg.exe` 报 ENOENT**，命令行搜索用 `findstr`/`grep`。
- 提交前留意不要再把密钥写进任何会被追踪的文件。

## 十二、快速定位

主类 `back/sky-server/src/main/java/com/sky/SkyApplication.java`（`@SpringBootApplication @EnableTransactionManagement @EnableCaching @EnableScheduling`，无 `@MapperScan`，靠各 Mapper 的 `@Mapper`）。Web 配置 `config/WebMvcConfiguration.java`；Redis 配置 `config/RedisConfiguration.java`；自动填充切面 `aspect/AutoFillAspect.java`；定时任务 `task/OrderTask.java`；WebSocket `websocket/WebSocketServer.java`；yml `sky-server/src/main/resources/application*.yml`；mapper XML `sky-server/src/main/resources/mapper/`；nginx `front/nginx-1.20.2/conf/nginx.conf`。
