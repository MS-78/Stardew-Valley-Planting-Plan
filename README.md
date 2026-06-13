# 🌾 星露谷物语种植规划系统

> 一款基于 Web 的星露谷物语种植规划工具，通过可视化画布 + 智能算法，帮助玩家制定最优种植方案。

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](./LICENSE)

## ✨ 功能特性

- **🎨 可视化画布规划** — 拖拽式网格画布，直观放置作物与工具（喷水器、稻草人），支持实时覆盖范围预览
- **🤖 一键自动规划** — 智能算法自动生成最优种植方案，支持贪心优化（最大化 ROI、最小化工具、同类作物聚簇）
- **📋 约束检查系统** — 实时检测 6 项硬约束（可达性、喷水器覆盖、稻草人覆盖、区域不空置、覆盖不重叠），问题点位可展开定位
- **💰 经济指标分析** — 实时统计投入产出、ROI、预算余额、土地利用率、生命/能量回复
- **📊 数据管理** — 作物与工具的完整 CRUD 管理，支持分类、搜索、分页
- **🔄 增量规划模式** — 保留手动放置的作物，仅对空白区域进行自动填充
- **↩️ 撤销功能** — 一键撤销自动规划结果，回到操作前状态
- **🌱 多季节支持** — 支持春季、夏季、秋季三季作物规划

## 🖥️ 技术栈

| 层级 | 技术 | 版本 |
|------|------|------|
| **前端框架** | Vue 3 (Composition API) | 3.5 |
| **构建工具** | Vite | 8.x |
| **UI 组件库** | Element Plus | 2.14 |
| **状态管理** | Pinia | 3.x |
| **后端框架** | Spring Boot | 3.2 |
| **编程语言** | Java | 17 |
| **ORM** | MyBatis-Plus | 3.5.5 |
| **数据库** | MySQL | 8.0 |
| **API 文档** | SpringDoc OpenAPI | 2.3 |
| **容器化** | Docker Compose | — |
| **Web 服务器** | Nginx | Alpine |

## 🏗️ 系统架构

```
┌─────────────────────────────────────────────────┐
│                  Browser (9999)                  │
└────────────────────┬────────────────────────────┘
                     │
┌────────────────────▼────────────────────────────┐
│           Nginx (静态资源 + API 反向代理)          │
└──────────┬─────────────────────┬────────────────┘
           │ /api/*              │ /*
┌──────────▼──────────┐  ┌──────▼──────────┐
│   Spring Boot       │  │   Vue 3 SPA     │
│   (REST API)        │  │   (静态资源)      │
│   :8080             │  │                 │
└──────────┬──────────┘  └─────────────────┘
           │
┌──────────▼──────────┐
│   MySQL 8.0         │
│   :3306             │
└─────────────────────┘
```

## 🚀 快速开始

### Docker 一键部署（推荐）

**前置要求**: Docker 和 Docker Compose

```bash
# 1. 克隆仓库
git clone https://github.com/MS-78/Stardew-Valley-Planting-Plan.git
cd Stardew-Valley-Planting-Plan

# 2. 配置环境变量（可选，默认值可直接使用）
cp .env.example .env

# 3. 启动所有服务
docker compose up -d

# 4. 访问应用
# 前端: http://localhost:9999
# API 文档: http://localhost:18080/swagger-ui.html
```

首次启动会自动初始化数据库表结构和星露谷作物数据。

### 本地开发

#### 前端

```bash
cd front
npm install
npm run dev
# 访问 http://localhost:5173（自动代理 API 到后端）
```

#### 后端

```bash
cd server

# 确保 MySQL 已启动并创建数据库
mysql -uroot -p -e "CREATE DATABASE IF NOT EXISTS stardew_planner CHARACTER SET utf8mb4;"

# 初始化表结构和数据
mysql -uroot -p stardew_planner < src/main/resources/schema.sql
mysql -uroot -p stardew_planner < src/main/resources/init.sql

# 启动 Spring Boot
./mvnw spring-boot:run
# 或使用 Maven Wrapper (Windows)
mvnw.cmd spring-boot:run
```

## 📁 项目结构

```
stardew-planner/
├── docker-compose.yml          # Docker 编排（MySQL + 后端 + 前端）
├── .env.example                # 环境变量模板
│
├── front/                      # 前端 (Vue 3)
│   ├── src/
│   │   ├── api/                # Axios HTTP 请求封装
│   │   ├── components/         # UI 组件
│   │   │   ├── GridCanvas.vue  # 网格画布（拖拽核心）
│   │   │   ├── SidePanel.vue   # 侧边栏（作物/工具列表）
│   │   │   ├── StatsPanel.vue  # 统计面板 + 约束检查
│   │   │   ├── ConfigDialog.vue    # 规划配置弹窗
│   │   │   └── AutoPlanDialog.vue  # 自动规划弹窗
│   │   ├── views/              # 页面视图
│   │   ├── stores/             # Pinia 状态管理
│   │   └── utils/              # 工具函数（约束检查器、收益计算器）
│   ├── public/
│   │   └── crop-images/        # 作物图片资源
│   ├── nginx.conf              # Nginx 配置
│   └── Dockerfile              # 前端 Docker 构建
│
├── server/                     # 后端 (Spring Boot)
│   ├── src/main/java/com/stardew/planner/
│   │   ├── algorithm/          # 自动规划算法（约束求解 + 贪心优化）
│   │   ├── controller/         # REST 控制器
│   │   ├── service/            # 业务逻辑层
│   │   ├── model/              # 数据实体
│   │   ├── dto/                # 数据传输对象
│   │   ├── repository/         # MyBatis-Plus Mapper
│   │   └── config/             # 配置类（CORS 等）
│   ├── src/main/resources/
│   │   ├── application.yml     # Spring Boot 配置
│   │   ├── schema.sql          # 数据库建表语句
│   │   └── init.sql            # 初始数据（作物 + 工具）
│   └── Dockerfile              # 后端 Docker 构建
│
├── docs/                       # 项目文档
│   ├── PRD.md                  # 产品需求文档
│   ├── development-plan.md     # 开发计划
│   └── acceptance-test-cases.md # 验收测试用例
│
└── 初始需求/                    # 原始需求与数据资料
```

## 🔧 环境变量

| 变量名 | 说明 | 默认值 |
|--------|------|--------|
| `DB_USERNAME` | MySQL 用户名 | `root` |
| `DB_PASSWORD` | MySQL 密码 | `stardew123` |

通过 `.env` 文件或系统环境变量配置，详见 `.env.example`。

## 📖 文档

- [产品需求文档 (PRD)](./docs/PRD.md) — 完整的功能需求、约束定义、API 设计
- [开发计划](./docs/development-plan.md) — 8 阶段开发流程
- [优化计划 V2](./docs/optimization-plan-v2.md) — 增量规划、约束增强等优化项
- [验收测试用例](./docs/acceptance-test-cases.md) — 功能与算法验收标准

## 🎮 使用指南

1. **创建规划** — 点击「开始新规划」，选择季节、设定地块尺寸和预算
2. **手动布局** — 从左侧面板拖拽作物和工具到画布，右键删除
3. **自动规划** — 点击「一键规划」，选择目标作物和模式（全新生成 / 增量填充）
4. **约束检查** — 底部面板实时显示约束违反情况，点击可展开定位问题格子
5. **数据管理** — 切换到「数据管理」页面，维护作物和工具的基础数据

## 📄 开源协议

本项目基于 [MIT License](./LICENSE) 开源。

## 🙏 致谢

- 作物数据来源：[星露谷物语中文 Wiki](https://zh.stardewvalleywiki.com/)
- UI 框架：[Element Plus](https://element-plus.org/)
- 后端框架：[Spring Boot](https://spring.io/projects/spring-boot)
