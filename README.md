# EVOPS 基础工作区 —— 考试机构考场与准考证建档

Spring Boot 骨架项目，本版实现模拟考前考务四步：**报名、编排、发证、查档**。
证书打印（版式输出）下版再做，本版只记录"已打证"状态与经办流水。

技术栈：Java 8、Spring Boot 2.7、MyBatis-Plus、H2 本地文件数据库、Shiro、Thymeleaf。

## 启动

1. `mvn -q -DskipTests compile` 编译。
2. `mvn spring-boot:run` 启动；Spring Boot 启动时自动执行 `src/main/resources/schema.sql`
   （建表 `IF NOT EXISTS`，版本 `MERGE`，可重复执行，不影响已有数据）。
3. H2 数据文件默认写入工作区 `data/evops`，连接串在 `application.yml`。
4. 账户：`bootstrap` / `bootstrap`（Shiro 本地域）。除 `/api/health`、`/api/auth/login` 外均需登录。

## 交付副本

`docs/schema/evops.sql` 为 schema 交付副本，应与 `src/main/resources/schema.sql` 保持一致
（原约定路径 `../../docs/schema/evops.sql` 因当前环境根目录只读，落在工作区内）。
schema 版本登记在 `t_evops_schema_version`（`base-0`、`exam-1`），版本选择可通过
`GET /api/system/versions` 追溯。

## 统一约定

- 响应体：`{ success, code, message, data }`。失败携带 HTTP 状态码与可读中文 message，不返回空白。
  - 400 参数错误；401 未登录；403 无权；404 不存在；409 撞号/状态冲突；422 行级校验。
- 批量动作一律**先预览影响范围（dryRun/preview），再执行**；预览不落库。
- 经办与钟点：打证、发放、勘误、编排预览/生成/回退均记录经办人与时间。
- 阶段结果与最终结论**分表**：编排预览快照、批次操作日志是阶段结果；准考证是最终结论。

## 四步流程与接口

登录：`POST /api/auth/login` `{username,password}`（会话 Cookie）。

### 1. 建档（容量、位置记全）
- `POST /api/archive/sites` 建考点（siteCode 唯一；siteName、location 必填）
- `GET  /api/archive/sites` / `GET /api/archive/sites/{id}`
- `POST /api/archive/sites/{id}/rooms` 建考场（roomCode 考点内唯一；capacity 必须 >0；location 必填）
- `GET  /api/archive/rooms?siteId=`

### 2. 报名（名册挂入 + 勘误留痕）
- `POST /api/registration/roster/preview` 预览影响范围：逐行给出接收/拒收与可读原因，不落库
- `POST /api/registration/roster/import` 执行：仅挂入通过校验的行，返回实际条数
  - 拒收场景：报名号/姓名/证件号为空、批内报名号或证件号重复、证件号已在名册中
- `GET  /api/registration/roster/{batchNo}`、`GET /api/registration/examinees/{id}`
- `POST /api/registration/examinees/{id}/corrections` 勘误：**旧值保留**在 `t_roster_correction`
  （fieldName/oldValue/newValue/reason/经办人/钟点），当前表更新为新值；新旧相同拒收
- `GET  /api/registration/examinees/{id}/corrections` 查更正痕迹

### 3. 编排（按批次生成准考证；证号、考场、座位一人一条）
- `POST /api/arrange/batches/preview` 圈定考生（rosterBatchNo/subject），
  返回影响人数、考场占用、每人证号/考场/座位、阻塞项
  - 阻塞项：无待编排考生、座位不足（扣减已占座位）、外部证号撞号等；有阻塞则不可执行
  - 证号：缺省由 H2 全局序列 `seq_admission_ticket_no` 自动出号（只增不回退）；
    也可用 `ticketNos`（报名号→证号）下发外部证号
- `POST /api/arrange/batches/{batchNo}/generate` 执行：
  按预览快照落准考证；预览后名册/座位漂移则要求重新预览；
  **证号撞号整批拒收**（事务回滚，不产生半条数据）
- `POST /api/arrange/batches/{batchNo}/rollback` 回退：
  撤销准考证、考生恢复"已报名"；**批次、预览快照、操作日志全部保留**，证号不复用；
  已进入打证/发放的批次不可回退
- `GET /api/arrange/batches`、`GET /api/arrange/batches/{batchNo}`
- `GET /api/arrange/batches/{batchNo}/logs` 预览/生成/回退全程留痕

### 4. 发证（打证、发放两步，均留经办与钟点）
- `POST /api/issue/print/preview` / `print/execute`：GENERATED → PRINTED
- `POST /api/issue/distribute/preview` / `distribute/execute`：PRINTED → ISSUED
  - 按 `ticketNos` 或 `arrangeBatchNo` 圈定；状态不符的逐条列入 skipped（可读原因）
- `GET /api/issue/tickets/{ticketNo}`、`GET /api/issue/tickets/{ticketNo}/logs` 单张轨迹

### 查档（按姓名、按证号）
- `GET /api/query/tickets/{ticketNo}` 一人一档：准考证 + 打证发放轨迹 + 勘误痕迹
- `GET /api/query/tickets?name=` 按姓名模糊查准考证（中文参数需 URL 编码）
- `GET /api/query/examinees?name=` 按姓名查名册（覆盖尚未出证者）
- `GET /api/query/all-tickets?arrangeBatchNo=&status=` 列表

## 主要表

| 表 | 作用 |
|---|---|
| `t_exam_site` / `t_exam_room` | 考点、考场建档（容量、位置） |
| `t_examinee` | 报名名册当前值 |
| `t_roster_correction` | 勘误旧值与更正痕迹（不抹旧值） |
| `t_arrange_batch` | 编排批次：预览快照（阶段结果）、各状态与经办钟点 |
| `t_batch_op_log` | 批次预览/生成/回退日志（状态回退可追溯） |
| `t_admission_ticket` | 准考证最终结论（证号唯一、一人一条、考场座位） |
| `t_ticket_issue_log` | 打证/发放流水（经办 + 钟点） |
| `t_evops_schema_version` | 工作区版本登记 |

## 验收

端到端脚本 `scripts/e2e.sh`（应用启动后执行，登录态下）覆盖：未登录 401、建档查重、名册预览/导入逐行结果、
勘误旧值保留、编排座位方案、撞号整批拒收、回退留痕与证号不复用、打证/发放流水、
按姓名与证号查档、容量不足阻塞、坏 JSON 400，共 64 项断言全部通过。
