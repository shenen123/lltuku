## 📖 1. 项目介绍 (Project Introduction)

本项目旨在解决传统图库系统在海量数据存储、检索效率低下以及内容理解能力不足的问题。系统不仅提供了高效的文件存储方案，更深度集成了人工智能技术，让图片“开口说话”。

### ✨ 核心亮点
*   **🚀 极致性能**：采用 **Redis + Caffeine** 构建本地 + 远程多级缓存架构，热点数据毫秒级响应。
*   **🤖 AI 赋能**：
    *   **自动标签**：上传图片后，AI 自动分析画面内容并生成精准标签（如：风景、人物、赛博朋克风格）。
    *   **文生图 (Text-to-Image)**：内置 AI 绘画大模型接口，用户输入文字描述即可自动生成创意图片。
*   **💾 海量存储**：集成 **MinIO** 对象存储，支持 PB 级文件高效存取；后端采用 **ShardingSphere** 实现数据库动态分库分表，轻松应对亿级数据增长。
*   **🔍 多维检索**：支持基于标签、颜色、时间、上传者等多维度的组合搜索，结合 Elasticsearch 实现高性能全文检索。
*   **🛡️ 安全可控**：基于 **Sa-Token** 实现细粒度的 RBAC 权限控制，确保数据安全。
*   **⚡ 实时协作**：通过 **WebSocket** 实现图片上传进度实时推送、多人在线编辑通知等功能。

---

## 🛠️ 2. 技术选型 (Technology Stack)

本项目采用前后端分离架构，后端基于 Spring Boot 生态，核心技术栈如下：

| 模块 | 技术组件 | 说明 |
| :--- | :--- | :--- |
| **核心框架** | Spring Boot 3.x | 快速开发基石 |
| **数据库** | MySQL 8.0 + **ShardingSphere** | 动态分库分表，解决单表性能瓶颈 |
| **对象存储** | **MinIO** | 高性能分布式文件存储，兼容 S3 协议 |
| **缓存架构** | **Redis** + **Caffeine** | **多级缓存**：Caffeine(本地) + Redis(分布式)，极大降低数据库压力 |
| **搜索引擎** | Elasticsearch | 多维度全文检索与数据分析 |
| **AI 能力** | Stable Diffusion / CLIP | **文生图**生成、图像**自动打标**与大模型接入 |
| **权限安全** | **Sa-Token** | 轻量级 RBAC 权限认证，支持多端登录 |
| **实时通信** | **WebSocket** | 实时消息推送、协作状态同步 |
| **服务治理** | Nacos + Sentinel | 服务注册发现、配置中心、流量熔断降级 |
| **消息队列** | RabbitMQ | 异步解耦（如：上传后异步触发 AI 分析） |
| **文档工具** | Knife4j (Swagger) | 在线 API 接口文档 |

---

## ⚙️ 3. 环境配置 (Environment Configuration)

在启动项目前，请确保已安装以下基础环境，并正确配置环境变量或配置文件。

### 3.1 基础依赖
*   **JDK**: 17+ (推荐 JDK 17 或 21)
*   **Maven**: 3.6+
*   **Docker & Docker Compose**: 用于快速部署中间件

### 3.2 中间件准备
建议使用 `docker-compose` 一键启动所有依赖服务（参考项目根目录 `docker-compose.yml`）：
*   MySQL (主从/集群)
*   Redis
*   MinIO
*   Nacos
*   RabbitMQ
*   Elasticsearch
*   AI 推理服务 (如部署了本地 SD 模型)

### 3.3 关键配置文件示例

# ⚙️ 核心配置文件说明 (Configuration Guide)

本项目配置分为两部分，均已进行**脱敏处理**。生产环境部署时，请通过环境变量或配置中心（如 Nacos）注入真实值。

1. **`application.yml`**：Spring Boot 应用主配置，包含服务发现、缓存、消息队列及分库分表逻辑。
2. **`shardingsphere-config-debug.yaml`**：ShardingSphere 独立运行时的调试配置（或作为配置中心的数据源），用于验证分片规则。
---

## 1. 应用主配置 (`application.yml`)

此文件定义了微服务的基础设施连接及核心业务逻辑参数。

```yaml
server:
  port: 8101                      # 服务端口
  servlet:
    context-path: /api            # 全局 API 前缀
spring:
  application:
    name: lltuku                  # 应用名称 (用于 Nacos 注册等)
  profiles:
    active: local                 # 激活环境：local, dev, prod

  # --- Redis 缓存配置 ---
  redis:
    host: ${REDIS_HOST:127.0.0.1} # 支持环境变量覆盖
    port: 6379
    database: 0
    timeout: 5000ms

  # --- ShardingSphere 分库分表配置 ---
  shardingsphere:
    datasource:
      names: yuntuku
      yuntuku:
        driver-class-name: com.mysql.cj.jdbc.Driver
        jdbc-url: jdbc:mysql://${DB_HOST:localhost}:3306/yuntuku?serverTimezone=Asia/Shanghai&...
        username: ${DB_USERNAME:root}
        password: ${DB_PASSWORD:1234}
    rules:
      sharding:
        tables:
          picture:
            actual-data-nodes: yuntuku.picture_$->{0..1023} # 分表范围：0-1023
            table-strategy:
              standard:
                sharding-column: space_id                   # 分片键
                sharding-algorithm-name: picture_sharding_algorithm
        sharding-algorithms:
          picture_sharding_algorithm:
            type: CLASS_BASED                               # 自定义分片算法
            props:
              algorithmClassName: com.liubinrui.manager.PictureShardingAlgorithm

  # --- 文件上传限制 ---
  servlet:
    multipart:
      max-file-size: 10MB
      max-request-size: 20MB

# --- MyBatis Plus 配置 ---
mybatis-plus:
  mapper-locations: classpath:mapper/*.xml
  configuration:
    map-underscore-to-camel-case: true # 开启驼峰命名映射
  global-config:
    db-config:
      logic-delete-field: isDelete     # 逻辑删除字段
      logic-delete-value: 1
      logic-not-delete-value: 0

# --- AI 大模型配置 (DashScope) ---
dashscope:
  api-key: ${DASHSCOPE_API_KEY:sk-xxx} # 【重要】请通过环境变量注入真实 Key
  model: qwen-vl-max                   # 使用的模型版本

# --- API 文档配置 (Knife4j) ---
knife4j:
  enable: true
  openapi:
    title: "${spring.application.name} 接口文档"
    version: 1.0.0
