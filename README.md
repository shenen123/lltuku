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

### 3.3 关键配置文件
项目包含两个核心配置文件，请根据实际环境修改：

#### A. `application.yml` (应用主配置)
主要配置服务端口、中间件连接信息、AI 接口地址等。
*   **重点修改项**：
    *   `spring.cloud.nacos.server-addr`: Nacos 地址
    *   `minio.endpoint`: MinIO 服务地址
    *   `ai.model.url`: AI 大模型服务接口地址
    *   `spring.redis.host`: Redis 地址

#### B. `shardingsphere-config-debug.yaml` (分片规则配置)
主要配置数据库分片策略、数据源连接。
*   **重点修改项**：
    *   `dataSources.ds_0.jdbcUrl`: 数据库连接 URL
    *   `dataSources.ds_0.username/password`: 数据库账号密码 (**注意脱敏**)
    *   `rules.SHARDING.tables.thumb.actualDataNodes`: 分表数量配置
