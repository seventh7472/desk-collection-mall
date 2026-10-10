# 桌面集在线商城微服务

一个可本地运行的在线商城微服务示例，包含商品展示、分类搜索、注册登录、购物车、订单、模拟支付、订单取消和商品检索问答。项目用于学习 Spring Boot、Spring Cloud、Nacos、OpenFeign、Gateway、MySQL、Docker Compose、Prometheus 与 Grafana 的协作方式。

## 项目结构

```text
common/             公共异常处理和内部请求保护
gateway/             API 网关与商城静态页面
user-service/        用户、登录、令牌和购物车
product-service/     商品、分类、库存预留与释放
order-service/       订单、幂等、模拟支付和取消
assistant-service/   商品检索与规则问答
deploy/              MySQL 初始化、Prometheus 和 Grafana 配置
tests/               网关验收与真实 MySQL 并发验证脚本
```

## 快速开始

环境要求：Docker Desktop、Docker Compose、Maven 3.9+ 和 JDK 17+。项目使用 JDK 21 构建；Windows PowerShell 可执行：

```powershell
$env:JAVA_HOME = 'D:\JAVA\jdk\jdk-21.0.11'
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
.\start.ps1
```

或者分步执行：

```powershell
mvn.cmd clean verify -B -ntp
docker compose up -d --build
```

首次启动会下载 Maven 依赖和 Docker 镜像，建议至少预留 6 GB 容器内存。停止和恢复使用 `docker compose stop`、`docker compose start`；删除容器不会删除 `mall-db` 数据卷。

## 本地地址

| 服务 | 地址 |
| --- | --- |
| 商城 | http://localhost:18080 |
| Nacos | http://localhost:18848/nacos |
| Sentinel | http://localhost:18858 |
| Prometheus | http://localhost:19090 |
| Grafana | http://localhost:13000 |
| 用户 Swagger | http://localhost:18081/swagger-ui/index.html |
| 商品 Swagger | http://localhost:18082/swagger-ui/index.html |
| 订单 Swagger | http://localhost:18083/swagger-ui/index.html |
| 客服 Swagger | http://localhost:18084/swagger-ui/index.html |

所有宿主端口仅绑定到 `127.0.0.1`。教学环境默认凭据写在 Compose 配置中，正式环境请通过环境变量覆盖并更换密钥。

## 验证

```powershell
python tests/acceptance.py
python tests/infrastructure.py
docker compose ps
```

Maven 测试覆盖用户认证、商品与库存、订单和客服服务；验收脚本通过网关检查权限、参数校验、购物车、订单幂等、模拟支付和取消；基础设施脚本检查真实 MySQL 并发库存、重复请求和 Sentinel 限流。

## 设计说明

- 服务通过 Nacos 注册发现，跨服务调用使用 OpenFeign 和 Spring Cloud LoadBalancer。
- 用户密码使用 BCrypt；令牌只保存 SHA-256 摘要并设置过期时间。
- 订单保存价格快照，金额使用 `BigDecimal`；库存使用数据库事务和行锁避免超卖。
- 商品列表配置 Sentinel 20 QPS 限流；Actuator 指标由 Prometheus 采集，Grafana 提供预置仪表板。
- 支付只更新订单状态，不发生实际扣款；客服是商品检索与规则问答，不连接外部大语言模型。

库存补偿采用本地事务和同步补偿，当前示例未实现 Seata、Outbox 或后台补偿任务。若进程在跨服务动作之间崩溃，需要依据日志人工处理 `COMPENSATION_REQUIRED` 情况。生产部署还应增加服务身份认证、HTTPS、密钥管理和会话撤销。

