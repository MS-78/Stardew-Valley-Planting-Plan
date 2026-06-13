# 阶段1-3 功能验收测试用例

**版本**: 1.0
**创建日期**: 2026-06-11
**基于**: PRD v3.1 + 开发计划
**适用范围**: 阶段1(项目初始化) + 阶段2(数据库) + 阶段3(后端CRUD API)

---

## 阶段1验收：项目初始化（3项）

### S1-01: 后端项目启动

| 项目 | 内容 |
|------|------|
| **前置条件** | MySQL已启动，application.yml配置正确 |
| **操作步骤** | 在 server/ 目录执行 `D:\apache-maven-3.9.15\bin\mvn spring-boot:run` |
| **预期结果** | 控制台输出 "Started StardewPlannerApplication"；监听端口8080；无启动报错 |
| **验证命令** | `curl http://localhost:8080/api/crops` 返回HTTP响应（即使是空列表或错误页面） |
| **通过标准** | Spring Boot成功启动，端口8080可访问 |

### S1-02: Swagger API文档可访问

| 项目 | 内容 |
|------|------|
| **前置条件** | 后端已启动 |
| **操作步骤** | 浏览器访问 http://localhost:8080/swagger-ui.html |
| **预期结果** | 显示Swagger UI页面；能看到 /api/crops、/api/tools、/api/categories、/api/planning 等API分组 |
| **通过标准** | Swagger页面正常渲染，API分组可见 |

### S1-03: 前端项目可启动（阶段1仅验证脚手架）

| 项目 | 内容 |
|------|------|
| **前置条件** | Node.js 18+ 已安装 |
| **操作步骤** | 在 front/ 目录执行 `npm install` 然后 `npm run dev` |
| **预期结果** | Vite开发服务器启动，显示本地访问地址（如 http://localhost:5173）；浏览器访问显示页面（可能是空白或初始页面）；无JS控制台报错 |
| **通过标准** | Vite启动成功，页面无报错 |

---

## 阶段2验收：数据库设计与初始化（4项）

### S2-01: 数据库表结构验证

| 项目 | 内容 |
|------|------|
| **前置条件** | 后端已启动（自动执行schema.sql） |
| **操作步骤** | 连接MySQL执行以下SQL |
| **SQL** | `USE stardew_planner; SHOW TABLES;` |
| **预期结果** | 显示3张表：crops, tools, categories |

**逐表验证字段**:

```sql
-- 验证crops表字段
DESCRIBE crops;
```
| 预期字段 | 类型 | 备注 |
|----------|------|------|
| id | varchar(36) | PRIMARY KEY |
| name | varchar(100) | NOT NULL |
| season | enum('spring','summer','fall') | NOT NULL |
| is_walkable | tinyint(1) | NOT NULL, DEFAULT 1 |
| seed_source | varchar(100) | NOT NULL |
| seed_price | int | NOT NULL |
| growth_days | int | NOT NULL |
| can_regrow | tinyint(1) | NOT NULL, DEFAULT 0 |
| regrow_interval | int | NULL |
| base_sell_price | int | NOT NULL |

```sql
-- 验证tools表字段
DESCRIBE tools;
```
| 预期字段 | 类型 | 备注 |
|----------|------|------|
| id | varchar(36) | PRIMARY KEY |
| name | varchar(100) | NOT NULL |
| type | enum('sprinkler','scarecrow') | NOT NULL |
| coverage_offsets | json | NOT NULL |
| blocks_walking | tinyint(1) | NOT NULL, DEFAULT 1 |
| price | int | NOT NULL, DEFAULT 0 |

```sql
-- 验证categories表字段
DESCRIBE categories;
```
| 预期字段 | 类型 | 备注 |
|----------|------|------|
| id | varchar(36) | PRIMARY KEY |
| name | varchar(100) | NOT NULL |
| type | enum('crop','tool') | NOT NULL |
| season | enum('spring','summer','fall') | NULL（type=crop时必填） |

### S2-02: 种子数据完整性 — 作物（33条）

| 项目 | 内容 |
|------|------|
| **SQL** | `SELECT COUNT(*) FROM crops;` |
| **预期** | 返回 33 |

**按季节分组验证**:

```sql
SELECT season, COUNT(*) as cnt FROM crops GROUP BY season ORDER BY season;
```
| 预期结果 | 数量 |
|----------|------|
| fall | 11 |
| spring | 10 |
| summer | 12 |

**关键作物抽样验证**:

```sql
-- 春季：防风草
SELECT name, seed_price, growth_days, can_regrow, base_sell_price 
FROM crops WHERE name = '防风草' AND season = 'spring';
```
| 预期 | seed_price=20, growth_days=4, can_regrow=0, base_sell_price=35 |

```sql
-- 春季：草莓（可重复收获）
SELECT name, seed_price, growth_days, can_regrow, regrow_interval, base_sell_price 
FROM crops WHERE name = '草莓' AND season = 'spring';
```
| 预期 | seed_price=100, growth_days=8, can_regrow=1, regrow_interval=4, base_sell_price=120 |

```sql
-- 夏季：啤酒花（棚架作物，is_walkable=false）
SELECT name, is_walkable, can_regrow, regrow_interval 
FROM crops WHERE name = '啤酒花' AND season = 'summer';
```
| 预期 | is_walkable=0, can_regrow=1, regrow_interval=1 |

```sql
-- 秋季：茄子（全游戏最高ROI=15.00）
SELECT name, seed_price, growth_days, can_regrow, regrow_interval, base_sell_price 
FROM crops WHERE name = '茄子' AND season = 'fall';
```
| 预期 | seed_price=20, growth_days=5, can_regrow=1, regrow_interval=5, base_sell_price=60 |

```sql
-- 跨季作物验证：玉米和向日葵应各出现2次（夏季+秋季）
SELECT name, GROUP_CONCAT(season) as seasons FROM crops 
WHERE name IN ('玉米', '向日葵') GROUP BY name;
```
| 预期 | 玉米: summer,fall; 向日葵: summer,fall |

### S2-03: 种子数据完整性 — 工具（2条）

| 项目 | 内容 |
|------|------|
| **SQL** | `SELECT id, name, type, coverage_offsets, blocks_walking FROM tools;` |
| **预期** | 返回2条记录 |

| 预期字段 | 喷水器 | 稻草人 |
|----------|--------|--------|
| id | tool-sprinkler | tool-scarecrow |
| name | 喷水器 | 稻草人 |
| type | sprinkler | scarecrow |
| coverage_offsets | `{"shape":"cross","range":1}` | `{"shape":"square","range":6}` |
| blocks_walking | 1 | 1 |
| price | 0 | 0 |

### S2-04: 种子数据完整性 — 分类（4条）

| 项目 | 内容 |
|------|------|
| **SQL** | `SELECT id, name, type, season FROM categories ORDER BY type, season;` |
| **预期** | 返回4条记录 |

| 预期 id | name | type | season |
|---------|------|------|--------|
| cat-spring | 春季作物 | crop | spring |
| cat-summer | 夏季作物 | crop | summer |
| cat-fall | 秋季作物 | crop | fall |
| cat-tool | 工具 | tool | NULL |

---

## 阶段3验收：后端CRUD API（12项）

### S3-01: GET /api/crops — 查询作物列表

| 项目 | 内容 |
|------|------|
| **请求** | `GET http://localhost:8080/api/crops` |
| **预期** | HTTP 200，返回33条作物的JSON数组 |
| **验证** | 数组长度=33；每条包含 id, name, season, seedPrice, growthDays 等字段 |

### S3-02: GET /api/crops?season=spring — 按季节过滤

| 项目 | 内容 |
|------|------|
| **请求** | `GET http://localhost:8080/api/crops?season=spring` |
| **预期** | HTTP 200，返回10条春季作物 |
| **验证** | 数组长度=10；所有记录的season字段均为"spring" |

### S3-03: GET /api/crops?keyword=草 — 关键词搜索

| 项目 | 内容 |
|------|------|
| **请求** | `GET http://localhost:8080/api/crops?keyword=草` |
| **预期** | HTTP 200，返回名称包含"草"的作物（如防风草、草莓、啤酒花等） |
| **验证** | 返回结果中每条记录的name均包含"草"字 |

### S3-04: GET /api/crops/{id} — 查询单个作物

| 项目 | 内容 |
|------|------|
| **请求** | `GET http://localhost:8080/api/crops/crop-s06` |
| **预期** | HTTP 200，返回防风草的完整信息 |
| **验证** | name="防风草", season="spring", seedPrice=20, growthDays=4, baseSellPrice=35 |

### S3-05: POST /api/crops — 新增作物

| 项目 | 内容 |
|------|------|
| **请求** | `POST http://localhost:8080/api/crops` + JSON Body |
| **Body** | `{"name":"测试花","season":"spring","isWalkable":true,"seedSource":"测试","seedPrice":10,"growthDays":4,"canRegrow":false,"baseSellPrice":50}` |
| **预期** | HTTP 201，返回新创建的作物（含自动生成的id） |
| **后续验证** | `GET /api/crops` 返回34条（原33+新增1） |

### S3-06: PUT /api/crops/{id} — 修改作物

| 项目 | 内容 |
|------|------|
| **请求** | `PUT http://localhost:8080/api/crops/{新增作物的id}` + JSON Body |
| **Body** | `{"name":"测试花","season":"spring","baseSellPrice":60}` (修改售价) |
| **预期** | HTTP 200，返回修改后的作物，baseSellPrice=60 |
| **后续验证** | `GET /api/crops/{id}` 确认售价已更新为60 |

### S3-07: DELETE /api/crops/{id} — 删除作物

| 项目 | 内容 |
|------|------|
| **请求** | `DELETE http://localhost:8080/api/crops/{新增作物的id}` |
| **预期** | HTTP 200，返回成功消息 |
| **后续验证** | `GET /api/crops` 恢复为33条；`GET /api/crops/{id}` 返回404 |

### S3-08: GET /api/tools — 查询工具列表

| 项目 | 内容 |
|------|------|
| **请求** | `GET http://localhost:8080/api/tools` |
| **预期** | HTTP 200，返回2条工具（喷水器+稻草人） |
| **验证** | 包含 coverage_offsets 字段，格式为 `{"shape":"cross","range":1}` 和 `{"shape":"square","range":6}` |

### S3-09: 工具 CRUD 完整验证

| 项目 | 内容 |
|------|------|
| **POST** | `POST /api/tools` 新增一个测试工具，预期201 |
| **GET** | `GET /api/tools` 返回3条 |
| **PUT** | `PUT /api/tools/{id}` 修改测试工具名称，预期200 |
| **DELETE** | `DELETE /api/tools/{id}` 删除测试工具，预期200 |
| **验证** | `GET /api/tools` 恢复为2条 |

### S3-10: GET /api/categories — 查询分类列表

| 项目 | 内容 |
|------|------|
| **请求** | `GET http://localhost:8080/api/categories` |
| **预期** | HTTP 200，返回4条分类 |
| **验证** | 包含春季作物、夏季作物、秋季作物、工具四个分类 |

### S3-11: 分类 CRUD 完整验证

| 项目 | 内容 |
|------|------|
| **POST** | `POST /api/categories` 新增"测试分类"(type=crop, season=spring)，预期201 |
| **PUT** | `PUT /api/categories/{id}` 修改名称为"测试分类改名"，预期200 |
| **GET** | `GET /api/categories?keyword=测试` 返回包含"测试"的分类 |
| **DELETE** | `DELETE /api/categories/{id}` 删除测试分类，预期200 |
| **验证** | `GET /api/categories` 恢复为4条 |

### S3-12: CORS 配置验证

| 项目 | 内容 |
|------|------|
| **请求** | `curl -H "Origin: http://localhost:5173" -H "Access-Control-Request-Method: GET" -X OPTIONS http://localhost:8080/api/crops` |
| **预期** | 响应头包含 `Access-Control-Allow-Origin: http://localhost:5173` |
| **验证** | CORS预检请求成功，允许来自前端的跨域请求 |

---

## 阶段3补充验证：数据一致性

### S3-X01: 重启后数据不丢失

| 项目 | 内容 |
|------|------|
| **操作** | 1. 新增一个作物 → 2. 重启后端 → 3. 查询作物列表 |
| **预期** | 重启后新增的作物仍然存在（MySQL持久化） |
| **注意** | init.sql中的 IF NOT EXISTS / INSERT IGNORE 确保重启不会重复插入种子数据 |

### S3-X02: Swagger文档完整性

| 项目 | 内容 |
|------|------|
| **操作** | 访问 http://localhost:8080/swagger-ui.html |
| **预期API分组** | crop-controller, tool-controller, category-controller, planning-controller |
| **预期接口数** | crops: 5个(GET列表+GET单个+POST+PUT+DELETE)；tools: 5个；categories: 5个；planning: 按需 |
| **通过标准** | 每个API都有请求/响应示例，可在线调试 |

---

## 快速验收脚本

以下命令可一次性验证核心功能（在bash/PowerShell中执行）：

```bash
# 1. 后端健康检查
curl -s http://localhost:8080/api/crops | python -c "import sys,json; data=json.load(sys.stdin); print(f'作物数量: {len(data)}')"

# 2. 按季节过滤
curl -s "http://localhost:8080/api/crops?season=spring" | python -c "import sys,json; data=json.load(sys.stdin); print(f'春季作物: {len(data)}')"

# 3. 工具列表
curl -s http://localhost:8080/api/tools | python -c "import sys,json; data=json.load(sys.stdin); print(f'工具数量: {len(data)}')"

# 4. 分类列表
curl -s http://localhost:8080/api/categories | python -c "import sys,json; data=json.load(sys.stdin); print(f'分类数量: {len(data)}')"

# 5. Swagger可访问
curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/swagger-ui.html
```

**预期输出**:
```
作物数量: 33
春季作物: 10
工具数量: 2
分类数量: 4
200
```

---

**文档结束**
