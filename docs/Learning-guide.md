# 智能工单处理 Agent 系统 — 学习指南

> **项目地址**：https://github.com/sqking-coke/work-order-agent <br>
> **适用对象**：Java 后端开发者，具备 Spring Boot 基础，想深入学习 AI Agent 架构设计

---

## 目录

1. [系统概述](#1-系统概述)
2. [技术架构](#2-技术架构)
3. [项目结构](#3-项目结构)
4. [数据库设计](#4-数据库设计)
5. [核心业务流程](#5-核心业务流程)
6. [API 接口文档](#6-api-接口文档)
7. [配置说明](#7-配置说明)
8. [AI Agent 工具链](#8-ai-agent-工具链)
9. [定时任务](#9-定时任务)
10. [部署与运维](#10-部署与运维)
11. [扩展指南](#11-扩展指南)

---

## 1. 系统概述

智能工单处理 Agent 是一个基于 **Java 原生 AI Agent 架构** 的企业级工单管理系统。它不依赖第三方 Agent 框架（如 LangChain），而是直接通过大模型 API 的 Tool-Use 能力，驱动一套完整的工单处理流水线：

- **接收**：用户提交工单（REST API）
- **解析**：大模型自动识别工单类型、优先级、模块、关键词、复杂度
- **去重**：基于 TF-IDF + 余弦相似度检测重复工单
- **决策**：判断是否可 AI 自动办结
- **执行**：自动答复（RAG 知识库）或分派至人工处理人
- **复盘**：单工单复盘 + 周期（日/周/月）统计复盘报告
- **预警**：超时未处理工单自动催办

**核心技术栈**：

| 层级 | 技术                                    |
|------|---------------------------------------|
| 框架 | Spring Boot 3.5.x + Java 17+          |
| ORM | MyBatis-Plus 3.5.x                    |
| 数据库 | MySQL 8.0                             |
| HTTP 客户端 | OkHttp 4.12（调用 LLM API）               |
| JSON | FastJSON2 2.0.49                      |
| 工具 | Hutool 5.8.27                         |
| LLM | OpenAI 兼容接口（DeepSeek / 通义千问 / Qwen 等） |

---

## 2. 技术架构

```
┌─────────────────────────────────────────────────────────────────┐
│                        REST API Layer                            │
│  WorkOrderController  │  KnowledgeController  │  ReportController│
└──────────────────────────────┬──────────────────────────────────┘
                               │
┌──────────────────────────────▼──────────────────────────────────┐
│                       Service Layer                              │
│  AgentCoreService  │  WorkOrderService  │  KnowledgeService      │
│                    │  ReportService                              │
└──────────────────────────────┬──────────────────────────────────┘
                               │
┌──────────────────────────────▼──────────────────────────────────┐
│                     AI Agent Tool Chain                          │
│  ┌─────────────┐  ┌────────────┐  ┌──────────────┐              │
│  │Intelligent   │  │Duplicate   │  │RAG           │              │
│  │ParserTool    │  │CheckTool   │  │KnowledgeTool │              │
│  └─────────────┘  └────────────┘  └──────────────┘              │
│  ┌─────────────┐  ┌────────────┐                                 │
│  │OrderAssign   │  │ReviewReport│                                 │
│  │Tool          │  │Tool        │                                 │
│  └─────────────┘  └────────────┘                                 │
│                      │                                           │
│              ┌───────▼────────┐                                  │
│              │   LLMClient    │  (OpenAI 兼容接口)                │
│              │   OkHttp       │                                  │
│              └────────────────┘                                  │
└──────────────────────────────┬──────────────────────────────────┘
                               │
┌──────────────────────────────▼──────────────────────────────────┐
│                      Data Layer                                  │
│  MyBatis-Plus Mappers  │  MySQL 8.0                              │
│  work_order / work_knowledge / work_order_report                 │
│  work_order_flow_log / work_dept_config                          │
└─────────────────────────────────────────────────────────────────┘
                               │
┌──────────────────────────────▼──────────────────────────────────┐
│                   Scheduled Tasks                                │
│  WorkOrderTimeoutTask (超时巡检)                                  │
│  ReviewReportTask (日/周复盘)                                     │
└─────────────────────────────────────────────────────────────────┘
```

**关键设计决策**：

- **异步处理**：工单提交同步返回工单编号，AI 解析通过独立线程池异步执行，不阻塞用户提交
- **独立 Processor 组件**：`AgentAsyncProcessor` 作为独立 `@Component` 确保 Spring AOP 能正确拦截 `@Async` 和 `@Transactional`
- **内置轻量 RAG**：基于 TF-IDF + 余弦相似度实现内嵌语义检索，无需外部向量数据库
- **策略模式决策**：AI 解析后根据 `canAutoFinish` 走自动办结或人工分派两条路径

---

## 3. 项目结构

```
src/main/java/com/workorder/agent/
├── WorkOrderAgentApplication.java          # 启动类
├── config/
│   ├── AgentConfig.java                    # 线程池参数配置
│   ├── LLMConfig.java                      # 大模型连接配置
│   ├── ThreadPoolConfig.java               # 线程池 Bean 定义
│   └── MyBatisPlusConfig.java              # MyBatis-Plus 分页插件
├── controller/
│   ├── WorkOrderController.java            # 工单 CRUD + 流转接口
│   ├── KnowledgeController.java            # 知识库管理接口
│   └── ReportController.java               # 复盘报告接口
├── dto/
│   ├── WorkOrderSubmitDTO.java             # 工单提交请求
│   ├── WorkOrderQueryDTO.java              # 工单列表查询参数
│   ├── OrderReviewDTO.java                 # 单工单复盘请求
│   ├── OrderFinishDTO.java                 # 手动办结请求
│   ├── KnowledgeSaveDTO.java               # 知识点保存请求
│   ├── ApiResponse.java                    # 统一响应体
│   └── AiParseResult.java                  # AI 解析结果
├── entity/
│   ├── WorkOrder.java                      # 工单主表实体
│   ├── WorkKnowledge.java                  # 知识点实体
│   ├── WorkOrderFlowLog.java               # 流转日志实体
│   ├── WorkOrderReport.java                # 复盘报告实体
│   └── WorkDeptConfig.java                 # 部门配置实体
├── enums/
│   ├── WorkType.java                       # 工单类型枚举
│   ├── Priority.java                       # 优先级枚举
│   ├── OrderStatus.java                    # 工单状态枚举
│   └── FlowAction.java                     # 流转操作枚举
├── exception/
│   ├── BusinessException.java              # 业务异常
│   └── GlobalExceptionHandler.java         # 全局异常处理
├── mapper/
│   ├── WorkOrderMapper.java                # 工单 Mapper（含自定义统计SQL）
│   ├── WorkKnowledgeMapper.java            # 知识库 Mapper
│   ├── WorkOrderFlowLogMapper.java         # 流转日志 Mapper
│   ├── WorkOrderReportMapper.java          # 报告 Mapper
│   └── WorkDeptConfigMapper.java           # 部门配置 Mapper
├── service/
│   ├── AgentCoreService.java               # Agent 核心调度接口
│   ├── WorkOrderService.java               # 工单查询接口
│   ├── KnowledgeService.java               # 知识库接口
│   ├── ReportService.java                  # 报告接口
│   ├── AgentAsyncProcessor.java            # 异步处理器（@Async + @Transactional）
│   └── impl/
│       ├── AgentCoreServiceImpl.java       # 核心调度实现
│       ├── WorkOrderServiceImpl.java       # 工单查询实现
│       ├── KnowledgeServiceImpl.java       # 知识库实现
│       └── ReportServiceImpl.java          # 报告实现
├── task/
│   ├── WorkOrderTimeoutTask.java           # 超时工单巡检定时任务
│   └── ReviewReportTask.java               # 周期报告生成定时任务
└── tool/
    ├── LLMClient.java                      # 大模型 HTTP 客户端（OkHttp + 重试）
    ├── IntelligentParserTool.java           # AI 智能解析工具
    ├── DuplicateCheckTool.java              # 重复工单检测（TF-IDF）
    ├── RagKnowledgeTool.java               # RAG 知识库检索工具
    ├── OrderAssignTool.java                 # 智能分派工具
    ├── ReviewReportTool.java               # AI 复盘报告生成工具
    └── TextSimilarityUtils.java            # 文本相似度计算工具类
```

---

## 4. 数据库设计

### 4.1 ER 图（逻辑关系）

```
┌─────────────┐     1:N     ┌──────────────────┐
│  WorkOrder  │─────────────│ WorkOrderFlowLog  │
│  (工单主表)  │             │   (流转日志)       │
└──────┬──────┘             └──────────────────┘
       │
       │ N:1 (可选关联)
       ▼
┌──────────────────┐
│ WorkOrderReport  │
│   (复盘报告)      │
└──────────────────┘

┌──────────────────┐     ┌──────────────────┐
│  WorkKnowledge   │     │  WorkDeptConfig  │
│   (知识库)       │     │  (部门配置)       │
└──────────────────┘     └──────────────────┘
```

### 4.2 数据表详解

#### 4.2.1 工单主表 `work_order`

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | BIGINT | 主键，自增 |
| `order_no` | VARCHAR(64) | 工单编号（唯一），格式 `WO20260520A1B2C3D4` |
| `title` | VARCHAR(500) | 工单标题 |
| `content` | TEXT | 工单详细内容 |
| `work_type` | VARCHAR(32) | 工单类型：consult/fault/appeal/suggest/ops_error/func_error |
| `priority` | TINYINT | 优先级：1紧急/2高/3中/4低 |
| `module` | VARCHAR(64) | 所属业务模块（AI 抽取） |
| `status` | TINYINT | 状态：0待处理/1处理中/2已办结/3已关闭 |
| `handler_user_id` | BIGINT | 处理人 ID |
| `handler_user_name` | VARCHAR(64) | 处理人姓名 |
| `dept_name` | VARCHAR(64) | 责任部门 |
| `ai_answer` | LONGTEXT | AI 自动答复内容 |
| `ai_parse_result` | JSON | AI 解析完整结果（JSON） |
| `is_auto_finish` | TINYINT | 是否 AI 自动办结：0否/1是 |
| `is_duplicate` | TINYINT | 是否重复工单：0否/1是 |
| `duplicate_order_id` | BIGINT | 关联的重复工单 ID |
| `submit_user_name` | VARCHAR(64) | 提交人姓名 |
| `create_time` | DATETIME | 创建时间 |
| `update_time` | DATETIME | 更新时间 |
| `finish_time` | DATETIME | 办结时间 |

**索引设计**：
- 唯一索引：`uk_order_no`（`order_no`）
- 普通索引：`idx_work_type`、`idx_priority`、`idx_status`、`idx_module`、`idx_handler`、`idx_create_time`、`idx_dept_name`

#### 4.2.2 知识库文档表 `work_knowledge`

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | BIGINT | 主键 |
| `title` | VARCHAR(500) | 知识点标题 |
| `content` | LONGTEXT | 知识点内容 |
| `module` | VARCHAR(64) | 所属业务模块 |
| `keywords` | VARCHAR(500) | 关键词（逗号分隔） |
| `status` | TINYINT | 状态：0禁用/1启用 |
| `create_time` | DATETIME | 创建时间 |
| `update_time` | DATETIME | 更新时间 |

- 全文索引：`FULLTEXT KEY ft_content (title, content)` — 支持 MySQL 原生全文检索
- 普通索引：`idx_module`、`idx_status`

#### 4.2.3 工单流转日志表 `work_order_flow_log`

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | BIGINT | 主键 |
| `order_id` | BIGINT | 关联工单 ID |
| `order_no` | VARCHAR(64) | 关联工单编号（冗余，便于查询） |
| `action` | VARCHAR(32) | 操作类型：submit/parse/assign/auto_finish/manual_finish/close/timeout_warn/review |
| `operator` | VARCHAR(64) | 操作人 |
| `content` | TEXT | 操作内容说明 |
| `create_time` | DATETIME | 操作时间 |

#### 4.2.4 工单复盘报告表 `work_order_report`

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | BIGINT | 主键 |
| `report_no` | VARCHAR(64) | 报告编号（唯一） |
| `report_type` | VARCHAR(16) | 报告类型：day/week/month |
| `report_period` | VARCHAR(32) | 报告周期（2026-05-20 / 2026-W21 / 2026-05） |
| `total_count` | INT | 工单总数 |
| `finish_count` | INT | 办结数 |
| `ai_auto_count` | INT | AI 自动办结数 |
| `timeout_count` | INT | 超时数 |
| `duplicate_count` | INT | 重复工单数 |
| `report_content` | LONGTEXT | AI 复盘报告内容（Markdown） |
| `create_time` | DATETIME | 生成时间 |

#### 4.2.5 部门责任人配置表 `work_dept_config`

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | BIGINT | 主键 |
| `dept_name` | VARCHAR(64) | 部门名称 |
| `module` | VARCHAR(64) | 负责的业务模块（NULL 表示默认兜底） |
| `handler_user_id` | BIGINT | 默认处理人 ID |
| `handler_user_name` | VARCHAR(64) | 默认处理人姓名 |
| `priority` | TINYINT | 部门处理优先级（数字越小越优先） |
| `status` | TINYINT | 状态：0禁用/1启用 |
| `create_time` | DATETIME | 创建时间 |

**初始数据**：

| 部门 | 负责模块 | 处理人 | 优先级 |
|------|---------|--------|--------|
| 技术运维部 | ops_error, fault | 张三(1001) | 1 |
| 客服部 | consult, appeal | 李四(1002) | 1 |
| 产品部 | suggest, func_error | 王五(1003) | 1 |
| 技术运维部 | NULL（兜底） | 张三(1001) | 10 |

---

## 5. 核心业务流程

### 5.1 工单生命周期状态机

```
                  ┌──────────┐
                  │  提交工单  │
                  └────┬─────┘
                       │
                  ┌────▼─────┐
                  │ 待处理(0) │◄──────────────────────┐
                  └────┬─────┘                       │
                       │                             │
              ┌────────┼────────┐                    │
              │        │        │                    │
         AI自动办结  人工分派   关闭                   │
              │        │        │                    │
         ┌────▼──┐ ┌──▼───┐ ┌──▼───┐                │
         │已办结(2)│ │处理中(1)│ │已关闭(3)│              │
         └────────┘ └──┬───┘ └──────┘                │
                       │                             │
                 人工办结                              │
                       │                             │
                  ┌────▼───┐                         │
                  │已办结(2) │                         │
                  └────────┘                         │
                                                     │
              超时未处理 ──────────────────────────────┘
              (触发预警，状态不变)
```

### 5.2 工单提交 → AI 处理完整流程

```
用户提交工单
    │
    ▼
AgentCoreServiceImpl.submitAndProcess()
    │
    ├── 1. 生成工单编号 (WO + yyyyMMdd + 8位随机码)
    ├── 2. 插入工单记录 (status=0, priority=4 默认)
    ├── 3. 写入流转日志 (action=submit)
    └── 4. 触发异步处理 AgentAsyncProcessor.process()
              │
              ▼
         ┌─────────────────────────────┐
         │  Step 1: AI 智能解析        │
         │  IntelligentParserTool      │
         │  → 调用大模型，输出 JSON    │
         │  → workType, priority,      │
         │    module, keywords,        │
         │    complexity, canAutoFinish│
         └──────────────┬──────────────┘
                        │
         ┌──────────────▼──────────────┐
         │  Step 2: 重复工单检测        │
         │  DuplicateCheckTool         │
         │  → TF-IDF + 余弦相似度      │
         │  → 与最近100条未办结工单比较 │
         │  → 阈值 ≥ 0.75 标记重复     │
         └──────────────┬──────────────┘
                        │
              ┌─────────┴─────────┐
              │                   │
        canAutoFinish=true   canAutoFinish=false
              │                   │
    ┌─────────▼────────┐  ┌──────▼──────────┐
    │ Step 3a: 自动办结 │  │ Step 3b: 人工分派│
    │ RAG 知识库检索    │  │ 部门匹配         │
    │ → LLM 生成答复    │  │ → 设置处理人     │
    │ → status=2        │  │ → status=0       │
    │ → isAutoFinish=1  │  │ → 等待人工处理   │
    └──────────────────┘  └─────────────────┘
```

### 5.3 异常兜底策略

当 LLM 不可用或 AI 解析异常时，系统采用多重兜底：

1. **LLM 未启用**（`llm.enabled=false` / API Key 未配置）：`chat()` 方法直接返回 `null`，所有调用方检查 null 后走降级逻辑
2. **AI 解析失败**：`IntelligentParserTool.buildFallback()` 返回默认值（consult 类型、中等优先级、客服部）
3. **RAG 无匹配**：`answer()` 返回 null → 降级为人工分派
4. **异步处理异常**：catch 后重置 status=0，转人工处理
5. **LLM HTTP 4xx**：直接抛异常不重试；**5xx / IO 异常**：最多重试 2 次

---

## 6. API 接口文档

**Base URL**: `http://localhost:8080`

**统一响应格式**:

```json
{
  "code": 200,
  "msg": "success",
  "data": {}
}
```

成功 code=200，业务失败 code=500，参数校验失败 code=400。

---

### 6.1 工单管理 `/agent/work/order`

#### 6.1.1 提交工单

```
POST /agent/work/order/submit
Content-Type: application/json
```

**请求体**：

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `title` | String | 是 | 工单标题，最大 500 字符 |
| `content` | String | 是 | 工单详细内容，TEXT 类型 |
| `submitUserName` | String | 否 | 提交人姓名，默认"匿名用户" |

**请求示例**：

```json
{
  "title": "线上支付服务响应超时，用户无法完成支付",
  "content": "今天下午3点左右开始，大量用户反馈支付页面一直转圈加载，最终提示超时...",
  "submitUserName": "小明"
}
```

**响应示例**：

```json
{
  "code": 200,
  "msg": "success",
  "data": {
    "id": 1,
    "orderNo": "WO20260520A1B2C3D4",
    "title": "线上支付服务响应超时，用户无法完成支付",
    "content": "今天下午3点左右开始...",
    "workType": null,
    "priority": 4,
    "status": 0,
    "submitUserName": "小明",
    "createTime": "2026-05-20 15:30:00"
  }
}
```

> **注意**：接口同步返回工单基本信息（status=0, priority=4 默认值），AI 解析结果由异步线程更新。

#### 6.1.2 工单列表查询

```
GET /agent/work/order/list
```

**查询参数**：

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `workType` | String | 否 | 工单类型：consult/fault/appeal/suggest/ops_error/func_error |
| `priority` | Integer | 否 | 优先级：1紧急/2高/3中/4低 |
| `status` | Integer | 否 | 状态：0待处理/1处理中/2已办结/3已关闭 |
| `module` | String | 否 | 业务模块 |
| `handlerUserId` | Long | 否 | 处理人ID |
| `deptName` | String | 否 | 责任部门 |
| `isAutoFinish` | Integer | 否 | AI自动办结：0否/1是 |
| `keyword` | String | 否 | 关键词（标题+内容模糊搜索） |
| `startTime` | String | 否 | 开始时间 |
| `endTime` | String | 否 | 结束时间 |
| `page` | Integer | 否 | 页码，默认 1 |
| `pageSize` | Integer | 否 | 每页条数，默认 20 |

**响应示例**：

```json
{
  "code": 200,
  "data": {
    "records": [
      {
        "id": 1,
        "orderNo": "WO20260520A1B2C3D4",
        "title": "线上支付服务响应超时...",
        "workType": "fault",
        "priority": 2,
        "status": 2,
        "deptName": "技术运维部",
        "handlerUserName": "张三",
        "isAutoFinish": 0,
        "createTime": "2026-05-20 15:30:00",
        "finishTime": "2026-05-20 16:45:00"
      }
    ],
    "total": 42,
    "size": 20,
    "current": 1,
    "pages": 3
  }
}
```

#### 6.1.3 工单详情

```
GET /agent/work/order/detail/{id}
```

返回完整工单信息，包含 `aiParseResult`（AI 解析 JSON）、`aiAnswer`（AI 答复内容）等所有字段。

#### 6.1.4 手动办结

```
POST /agent/work/order/finish
```

**请求体**：

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `orderId` | Long | 是 | 工单 ID |
| `handlerName` | String | 否 | 处理人，默认"管理员" |
| `finishContent` | String | 否 | 办结备注 |

办结备注会追加到 `aiAnswer` 字段末尾，标识为 `【人工处理备注】`。

#### 6.1.5 关闭工单

```
POST /agent/work/order/close/{id}?operator=管理员
```

将工单状态置为 `3-已关闭`。

#### 6.1.6 AI 复盘

```
POST /agent/work/order/review
```

**请求体**：

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `orderId` | Long | 否 | 工单 ID |
| `extraPrompt` | String | 否 | 额外分析提示 |

返回 AI 生成的分析报告文本，覆盖分类准确性、优先级合理性、流程规范性、答复质量、优化建议等维度。

---

### 6.2 知识库管理 `/agent/work/knowledge`

#### 6.2.1 新增/编辑知识点

```
POST /agent/work/knowledge/save
```

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `id` | Long | 否 | 有值时编辑，无值新增 |
| `title` | String | 是 | 标题 |
| `content` | String | 是 | 内容 |
| `module` | String | 否 | 所属模块 |
| `keywords` | String | 否 | 关键词，逗号分隔 |
| `status` | Integer | 否 | 0禁用/1启用，新增默认1 |

每次保存后自动触发 `RagKnowledgeTool.refreshIndex()` 刷新内存索引。

#### 6.2.2 知识点列表

```
GET /agent/work/knowledge/list?page=1&pageSize=20&keyword=密码&module=consult
```

#### 6.2.3 知识点详情 / 删除 / 刷新索引

```
GET    /agent/work/knowledge/detail/{id}
DELETE /agent/work/knowledge/delete/{id}
POST   /agent/work/knowledge/refresh
```

---

### 6.3 复盘报告 `/agent/work/report`

#### 6.3.1 获取报告

```
GET /agent/work/report/get?reportType=day&reportPeriod=2026-05-20
```

`reportType`：day / week / month，默认为 day。

`reportPeriod` 格式：
- day：`2026-05-20`
- week：`2026-W21`
- month：`2026-05`

#### 6.3.2 手动生成报告

```
POST /agent/work/report/generate?reportType=day&reportPeriod=2026-05-20
```

同一周期重复调用不会重复生成（先查已有报告，存在直接返回）。

#### 6.3.3 所有报告列表

```
GET /agent/work/report/list
```

---

### 6.4 异常响应示例

**参数校验失败**：

```json
{
  "code": 400,
  "msg": "工单标题不能为空, 工单内容不能为空",
  "data": null
}
```

**业务异常**：

```json
{
  "code": 500,
  "msg": "工单不存在",
  "data": null
}
```

**系统异常**（不可预期错误）：

```json
{
  "code": 500,
  "msg": "系统内部错误，请联系管理员",
  "data": null
}
```

---

## 7. 配置说明

完整配置文件：`src/main/resources/application.yml`

### 7.1 大模型配置 `llm.*`

```yaml
llm:
  enabled: true                         # 必须设为 true
  api-url: https://api.deepseek.com/v1/chat/completions
  api-key: sk-xxxxxxxxxxxxxxxxxxxx      # 替换为真实 API Key
  model: deepseek-chat                  # 模型名称
  connect-timeout: 10                   # 连接超时（秒）
  read-timeout: 60                      # 读取超时（秒）
  max-tokens: 2048                      # 最大输出 token
  temperature: 0.3                      # 0-2，越低越确定（推荐 0.3）
  max-retries: 2                        # 失败重试次数
  retry-delay-ms: 1000                  # 重试间隔（毫秒）
```

**支持的大模型**：任何兼容 OpenAI Chat Completions 格式的接口均可使用：

| 平台 | api-url |
|------|---------|
| DeepSeek | `https://api.deepseek.com/v1/chat/completions` |
| 阿里通义千问 | `https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions` |
| 智谱 GLM | `https://open.bigmodel.cn/api/paas/v4/chat/completions` |
| 本地 Ollama | `http://localhost:11434/v1/chat/completions` |

### 7.2 线程池配置 `agent.thread-pool.*`

```yaml
agent:
  thread-pool:
    core-size: 4                     # 核心线程数
    max-size: 8                      # 最大线程数
    queue-capacity: 200              # 任务队列容量
    keep-alive-seconds: 60           # 空闲线程存活时间
    await-termination-seconds: 30    # 关闭时等待任务完成时间
```

线程池拒绝策略：`CallerRunsPolicy` — 队列满时由调用线程执行，保证任务不丢失。

### 7.3 定时任务配置 `agent.task.*`

```yaml
agent:
  task:
    timeout-cron: "0 */5 * * * ?"       # 超时巡检，每5分钟
    daily-report-cron: "0 0 18 * * ?"   # 每日复盘，18:00
    weekly-report-cron: "0 0 9 * * MON" # 每周复盘，周一09:00
    timeout-hours: 4                    # 超时阈值（小时）
```

### 7.4 RAG 知识库配置 `agent.rag.*`

```yaml
agent:
  rag:
    top-k: 5                        # 召回知识点数量
    similarity-threshold: 0.3       # 相似度阈值（低于此值不召回）
```

### 7.5 重复检测 & 兜底分派

```yaml
agent:
  duplicate:
    threshold: 0.75                 # 相似度 ≥ 0.75 视为重复
  fallback-assign:
    dept-name: "客服部"             # 无匹配部门时兜底
    handler-user-id: 1002
    handler-user-name: "李四"
```

---

## 8. AI Agent 工具链

### 8.1 工具架构设计

Agent 工具链遵循 **Tool-Use 模式**：每个 Tool 是独立的 Spring `@Component`，通过 LLMClient 调用大模型完成特定子任务。各 Tool 之间无直接依赖，由 `AgentAsyncProcessor` 编排调用顺序。

```
AgentAsyncProcessor (编排器)
    │
    ├── IntelligentParserTool.parse(title, content) → AiParseResult
    │
    ├── DuplicateCheckTool.check(title, content) → DuplicateResult
    │
    ├── [if canAutoFinish] RagKnowledgeTool.answer(question) → String
    │
    └── [if !canAutoFinish] OrderAssignTool.assign(workType, module) → AssignResult
```

### 8.2 LLMClient — 大模型客户端

**职责**：封装 OpenAI 兼容 API 的 HTTP 调用，提供 4 种调用方式：

| 方法 | 返回类型 | 用途 |
|------|---------|------|
| `chat(sys, user)` | `String` | 通用对话，返回纯文本 |
| `chat(sys, user, jsonMode)` | `String` | 强制 JSON 输出模式 |
| `chatForJson(sys, user)` | `JSONObject` | 对话 + 自动解析为 JSONObject |
| `chatForObject(sys, user, Class)` | `<T>` | 对话 + 自动反序列化为 Java 对象 |

**重试策略**：
- 4xx 客户端错误：不重试，直接抛 `BusinessException`
- 5xx 服务端错误：重试（最多 2 次，间隔 1 秒）
- IO 异常（网络）：重试（最多 2 次，间隔 1 秒）

**关键实现细节**：
- `extractJson()` 静态方法自动处理 markdown 代码块包裹、首尾非 JSON 字符等常见 LLM 输出格式问题
- LLM 未启用或 API Key 未配置时返回 `null`，所有上级调用方负责 null-safe 降级

### 8.3 IntelligentParserTool — 智能解析工具

**Prompt 设计**：约 2KB 的结构化 system prompt，明确定义了：

1. 6 种工单类型的判别标准
2. 4 级优先级的定义与判定条件
3. simple/complex 复杂度判定
4. canAutoFinish 的决策规则矩阵

**System Prompt 核心约束**：

```
canAutoFinish 判定规则：
- 咨询类 + 简单问题 → true
- 故障类 / 运维报错类 / 功能异常类 → false
- 申诉类 → false（需人工审核）
- 建议类 → false（需产品评估）
```

**异常兜底**：当 LLM 调用失败时，`buildFallback()` 返回保守默认值（consult 类型、中优先级、客服部、不可自动办结），确保系统不因 LLM 故障而中断。

### 8.4 DuplicateCheckTool — 重复检测工具

**算法流程**：

```
新工单文本 → 分词(Tokenize) → 计算 TF → 
                                         → 构建 IDF → 构建向量 → 余弦相似度
最近100条未办结工单 → 逐条分词 → 计算 TF ↗
```

**技术要点**：
- 仅与最近 100 条未办结（status ≠ 2,3）工单比较，控制计算复杂度
- 相似度阈值 0.75 可配置
- 检测到重复工单后标记 `is_duplicate=1` 并记录 `duplicate_order_id`，但不中断正常处理流程

### 8.5 RagKnowledgeTool — 轻量 RAG 知识库

这是一个**无需外部向量数据库的内嵌 RAG 实现**：

**架构设计**：

```
应用启动 → @PostConstruct init()
    │
    ├── 加载全部启用状态的知识点 (status=1)
    ├── 对每篇文档执行 tokenize + computeTF
    ├── 计算全局 IDF（所有文档的逆文档频率）
    └── 缓存至内存 (docs + idf)

查询时：
    ├── 对 query 执行 tokenize + computeTF
    ├── 与每篇文档计算余弦相似度
    ├── 过滤 similarity ≥ threshold 的文档
    └── 返回 top-K 结果

answer()：
    ├── search() 检索匹配的知识点
    ├── 构建 "知识库上下文 + 用户问题" prompt
    └── 调用 LLM 生成自然语言答复
```

**索引刷新时机**：
- 应用启动（`@PostConstruct`）
- 新增/编辑知识点后
- 删除知识点后
- 手动调用 `/knowledge/refresh`

### 8.6 OrderAssignTool — 智能分派工具

**匹配策略**（4 级降级）：

```
1. 精确匹配：workType + module 都匹配
2. module 匹配（忽略 workType）
3. workType 匹配（忽略 module）
4. module=NULL 的兜底配置
5. 配置文件 fallback-assign 作为最终兜底
```

匹配时按 `priority` 升序取第一条配置。

### 8.7 TextSimilarityUtils — 文本相似度

**分词策略**：
- 英文：按空格分词，过滤长度 < 2 的词
- 中文：unigram（单字）+ bigram（双字词组）

**TF-IDF 计算**：
- TF = 词频 / 文档总词数（归一化）
- IDF = ln(1 + N / (1 + df)) （拉普拉斯平滑）

**余弦相似度**：dot(a,b) / (‖a‖ × ‖b‖)

---

## 9. 定时任务

### 9.1 超时巡检 `WorkOrderTimeoutTask`

- **触发频率**：每 5 分钟（可配置）
- **逻辑**：
  1. 查询 `status IN (0,1)` 且 `create_time ≤ 当前时间 - timeoutHours` 的工单
  2. 检查过去 1 小时内是否已发过预警日志（去重）
  3. 未预警的插入 `action=timeout_warn` 流转日志

### 9.2 周期报告 `ReviewReportTask`

- **每日复盘**：每天 18:00 自动生成当日报告
- **每周复盘**：每周一 09:00 自动生成当周报告（ISO 周编号）

---

## 10. 部署与运维

### 10.1 环境要求

| 依赖 | 版本   |
|------|------|
| JDK | 17+  |
| Maven | 3.8+ |
| MySQL | 8.0+ |

### 10.2 启动步骤

```bash
# 1. 初始化数据库
mysql -u root -p < sql/init.sql

# 2. 修改 application.yml 中的数据库连接信息和 LLM API Key

# 3. 编译打包
mvn clean package -DskipTests

# 4. 启动
java -jar target/work-order-agent-1.0.0.jar

# 或者开发模式
mvn spring-boot:run
```

### 10.3 健康检查

```bash
# 应用是否启动
curl http://localhost:8080/agent/work/order/list

# 知识库索引是否加载
curl http://localhost:8080/agent/work/knowledge/list
```

### 10.4 日志配置

```yaml
logging:
  level:
    com.workorder.agent: DEBUG          # Agent 业务日志
    com.workorder.agent.mapper: INFO    # SQL 日志（MyBatis-Plus stdout）
```

关键日志标识：

| 日志关键词 | 含义 |
|-----------|------|
| `工单创建成功` | 新工单入库 |
| `开始异步处理工单` | AI 处理流程启动 |
| `解析完成: type=` | AI 智能解析成功 |
| `检测到重复工单` | 重复工单检出 |
| `执行自动办结流程` | AI 自动答复 |
| `执行人工分派流程` | 分派至人工 |
| `AI处理异常，已兜底转人工` | 异步处理异常兜底 |
| `LLM 未启用` | 大模型未配置 |

### 10.5 测试用例

```bash
1.1 提交工单
  
  # 故障类工单
  curl -X POST http://localhost:8080/agent/work/order/submit \
    -H "Content-Type: application/json" \
    -d '{
      "title": "线上支付服务响应超时，用户无法完成支付",
      "content": "今天下午3点左右开始，大量用户反馈支付页面一直转圈加载，最终提示超时。已检查应用日志发现有大量 ReadTimeoutException，影响范围是全部支付通道。",
      "submitUserName": "小明"
    }'

  # 咨询类工单
  curl -X POST http://localhost:8080/agent/work/order/submit \
    -H "Content-Type: application/json" \
    -d '{
      "title": "如何导出上月的数据报表",
      "content": "我想导出2026年4月的运营数据报表，但不知道从哪里操作，请指导。",
      "submitUserName": "小红"
    }'

  # 功能异常类工单
  curl -X POST http://localhost:8080/agent/work/order/submit \
    -H "Content-Type: application/json" \
    -d '{
      "title": "用户注册页面验证码收不到",
      "content": "用户反馈在注册页面点击发送验证码按钮后，一直收不到短信验证码，换了几个手机号都这样。",
      "submitUserName": "王工程师"
    }'

  # 建议类工单
  curl -X POST http://localhost:8080/agent/work/order/submit \
    -H "Content-Type: application/json" \
    -d '{
      "title": "希望在列表页增加批量导出功能",
      "content": "目前在订单列表页只能逐条查看，希望增加一个批量勾选并导出Excel的功能，方便运营同事做数据分析。",
      "submitUserName": "运营小李"
    }'

  # 申诉类工单
  curl -X POST http://localhost:8080/agent/work/order/submit \
    -H "Content-Type: application/json" \
    -d '{
      "title": "订单退款被误拒，要求重新审核",
      "content": "订单号 ORD20260518001，用户申请退款被系统自动拒绝，理由是超过7天。但用户实际在5月16日就提交过申请，当时系统故障导致未记录，申请重新审核。",
      "submitUserName": "客服小张"
    }'
    
  异常/边界参数：

  # title 为空
  curl -X POST http://localhost:8080/agent/work/order/submit \
    -H "Content-Type: application/json" \
    -d '{"title":"","content":"内容","submitUserName":"小明"}'
  # 预期: {"code":400,"msg":"工单标题不能为空","data":null}

  # content 为空
  curl -X POST http://localhost:8080/agent/work/order/submit \
    -H "Content-Type: application/json" \
    -d '{"title":"标题","content":"","submitUserName":"小明"}'
  # 预期: {"code":400,"msg":"工单内容不能为空","data":null}

  # submitUserName 为 null（允许，默认"匿名用户"）
  curl -X POST http://localhost:8080/agent/work/order/submit \
    -H "Content-Type: application/json" \
    -d '{"title":"测试","content":"测试内容"}'

  # 超长标题（500字符限制）
  # 500个a
  curl -X POST http://localhost:8080/agent/work/order/submit \
    -H "Content-Type: application/json" \
    -d "{\"title\":\"$(python3 -c "print('a'*500)")\",\"content\":\"测试\",\"submitUserName\":\"测试\"}"

  
1.2 工单列表
  
  # 无参数，默认第1页20条
  curl "http://localhost:8080/agent/work/order/list"

  # 按类型筛选 - 故障类
  curl "http://localhost:8080/agent/work/order/list?workType=fault"

  # 按状态筛选 - 待处理
  curl "http://localhost:8080/agent/work/order/list?status=0"
  
  # 按处理人ID
  curl "http://localhost:8080/agent/work/order/list?handlerUserId=1001"

  # 按部门
  curl "http://localhost:8080/agent/work/order/list?deptName=技术运维部"

  # 是否AI自动办结
  curl "http://localhost:8080/agent/work/order/list?isAutoFinish=1"

  # 关键词搜索（标题+内容模糊匹配）
  curl "http://localhost:8080/agent/work/order/list?keyword=支付"

  # 时间范围
  curl "http://localhost:8080/agent/work/order/list?startTime=2026-05-01&endTime=2026-05-20"
  
1.3 工单详情
  
  # 存在的工单
  curl "http://localhost:8080/agent/work/order/detail/1"

  # 不存在的工单
  curl "http://localhost:8080/agent/work/order/detail/99999"
  # 预期: {"code":500,"msg":"工单不存在","data":null}

  # id 为 0
  curl "http://localhost:8080/agent/work/order/detail/0"

  # id 为负数
  curl "http://localhost:8080/agent/work/order/detail/-1"
  
1.4 手动办结
  
  # 正常办结（指定 handlerName 和 finishContent）
  curl -X POST http://localhost:8080/agent/work/order/finish \
    -H "Content-Type: application/json" \
    -d '{"orderId":1,"handlerName":"张三","finishContent":"已联系第三方支付渠道确认，恢复了限流策略，支付服务恢复正常。"}'

  # 只填 orderId，不填可选字段
  curl -X POST http://localhost:8080/agent/work/order/finish \
    -H "Content-Type: application/json" \
    -d '{"orderId":2}'
  
  1.5 关闭工单
  # 正常关闭，指定操作人
  curl -X POST "http://localhost:8080/agent/work/order/close/1?operator=张管理员"

  # 不指定操作人（默认"管理员"）
  curl -X POST "http://localhost:8080/agent/work/order/close/2"

  # 不存在的工单
  curl -X POST "http://localhost:8080/agent/work/order/close/99999"
  # 预期: {"code":500,"msg":"工单不存在","data":null}
  
  1.6 AI 复盘
  # 正常复盘
  curl -X POST http://localhost:8080/agent/work/order/review \
    -H "Content-Type: application/json" \
    -d '{"orderId":1,"extraPrompt":"请重点分析处理时效和知识库匹配度"}'

  # 不带额外提示
  curl -X POST http://localhost:8080/agent/work/order/review \
    -H "Content-Type: application/json" \
    -d '{"orderId":1}'
  
2. 知识库控制器
  # 新增知识点
  curl -X POST http://localhost:8080/agent/work/knowledge/save \
    -H "Content-Type: application/json" \
    -d '{
      "title":"CDN缓存刷新操作指南",
      "content":"1. 登录CDN控制台 2. 选择缓存刷新 3. 输入需要刷新的URL 4. 点击提交。注意：每次最多提交100条URL，每天限额500条。",
      "module":"ops_error",
      "keywords":"CDN,缓存,刷新,静态资源",
      "status":1
    }'

  # 编辑已有知识点
  curl -X POST http://localhost:8080/agent/work/knowledge/save \
    -H "Content-Type: application/json" \
    -d '{
      "id":1,
      "title":"如何重置登录密码（已更新）",
      "content":"请前往系统登录页面，点击\"忘记密码\"链接...",
      "module":"consult",
      "keywords":"密码,登录,重置,忘记密码,2FA",
      "status":1
    }'
    
3.1 获取报告
  # 日报告，指定日期
  curl "http://localhost:8080/agent/work/report/get?reportType=day&reportPeriod=2026-05-20"

  # 周报告
  curl "http://localhost:8080/agent/work/report/get?reportType=week&reportPeriod=2026-W21"

  # 月报告
  curl "http://localhost:8080/agent/work/report/get?reportType=month&reportPeriod=2026-05"

  # 默认 reportType=day，不指定 reportPeriod（返回最新）
  curl "http://localhost:8080/agent/work/report/get"
    
3.2 生成报告
    # 生成日报告
  curl -X POST "http://localhost:8080/agent/work/report/generate?reportType=day&reportPeriod=2026-05-20"

  # 生成周报告
  curl -X POST "http://localhost:8080/agent/work/report/generate?reportType=week&reportPeriod=2026-W21"

  # 生成月报告
  curl -X POST "http://localhost:8080/agent/work/report/generate?reportType=month&reportPeriod=2026-05"

  # 不传 reportPeriod，默认今天
  curl -X POST "http://localhost:8080/agent/work/report/generate?reportType=day"
    
3.3 报告列表
    curl "http://localhost:8080/agent/work/report/list"
```

---

## 11. 扩展指南

### 11.1 添加新的工单类型

1. 在 `WorkType` 枚举中添加新的 `code` 和 `label`
2. 修改 `IntelligentParserTool.PARSE_SYSTEM_PROMPT` 中的工单类型定义
3. 在 `work_dept_config` 表中添加对应的部门分派配置
4. 在 `AiParseResult` 的 workType 注释中补充说明

### 11.2 接入外部向量数据库

如果需要将轻量 RAG 升级为向量数据库方案（如 Milvus / Pinecone / pgvector）：

1. 创建 `VectorStore` 接口，定义 `embed()` 和 `search()` 方法
2. 在 `RagKnowledgeTool` 中注入 `VectorStore`，替换 `search()` 中的 TF-IDF 逻辑
3. 使用 LLM 的 Embedding API 替代 TF-IDF 向量化
4. 保留 `TextSimilarityUtils` 供 `DuplicateCheckTool` 继续使用

### 11.3 添加新的 Agent 工具

1. 在 `tool/` 包下创建新的 `@Component` 类
2. 在 `AgentAsyncProcessor` 中注入新工具
3. 在 `process()` 方法的适当位置编排新工具的调用

### 11.4 接入消息通知

在工单分派、超时预警等节点接入企业微信/钉钉/飞书通知：

1. 创建 `NotificationService` 接口
2. 在 `AgentAsyncProcessor.executeAssign()` 和 `WorkOrderTimeoutTask.checkTimeoutOrders()` 中调用通知
3. 通过 `application.yml` 配置 webhook URL

### 11.5 关键类依赖关系速查

```
要修改 AI 解析逻辑       → IntelligentParserTool.java
要修改 重复检测算法       → DuplicateCheckTool.java + TextSimilarityUtils.java
要修改 RAG 检索策略       → RagKnowledgeTool.java
要修改 分派匹配规则       → OrderAssignTool.java
要修改 工单流转流程       → AgentAsyncProcessor.java
要修改 大模型调用         → LLMClient.java
要修改 复盘报告模板       → ReviewReportTool.java
要修改 API 接口          → WorkOrderController / KnowledgeController / ReportController
要修改 定时任务           → WorkOrderTimeoutTask / ReviewReportTask
要修改 异常处理           → GlobalExceptionHandler + BusinessException
```

---

## 附录 A：工单编号规则

格式：`WO{yyyyMMdd}{8位随机大写十六进制}`

示例：`WO20260520A1B2C3D4`

实现：`AgentCoreServiceImpl.generateOrderNo()` 使用 Hutool 的 `DateUtil.format()` + `IdUtil.fastSimpleUUID()`。

## 附录 B：状态码速查

| 状态码 | 枚举 | 含义 |
|--------|------|------|
| 0 | PENDING | 待处理 |
| 1 | PROCESSING | 处理中 |
| 2 | FINISHED | 已办结 |
| 3 | CLOSED | 已关闭 |

## 附录 C：工单类型速查

| 类型 | 代码 | 典型场景 |
|------|------|---------|
| 咨询类 | consult | 用户提问、使用咨询、流程询问 |
| 故障类 | fault | 系统不可用、功能异常 |
| 申诉类 | appeal | 用户投诉、退款申诉、纠纷 |
| 建议类 | suggest | 功能建议、优化建议 |
| 运维报错类 | ops_error | 服务器错误、数据库报错、中间件异常 |
| 功能异常类 | func_error | 功能不按预期工作、界面 Bug |

## 附录 D：流转操作类型速查

| 操作 | 代码 | 触发时机 |
|------|------|---------|
| 提交 | submit | 用户提交工单 |
| AI解析 | parse | AI 智能解析完成 |
| 分派 | assign | 工单分派至处理人 |
| AI自动办结 | auto_finish | RAG 知识库自动答复 |
| 人工办结 | manual_finish | 人工点击办结 |
| 关闭 | close | 关闭工单 |
| 超时预警 | timeout_warn | 定时任务检测到超时 |
| 复盘 | review | AI 复盘分析 |

遇到任何问题，欢迎提 Issue 或 PR。
