# DayFlow 客户端同步消息与 API（协议 v1）

对应当前 `server/server.py`，以及 Android／Windows 客户端。传输协议仍为 v1，Android 0.5.0 增加类型定义与重复实例扩展字段，HTTP 路由不变。TaskFlow Windows 的 Yjs/WebSocket 是另一套协议，Android 1.0.0适配见《TaskFlow-Windows连接协议》。

## 1. 基础约定

- 默认地址：`http://局域网IP:8765`；云端使用 HTTPS。服务器可独立部署，也可完全不使用。
- 传输：HTTP + UTF-8 JSON，请求 `Content-Type: application/json; charset=utf-8`。
- 受保护接口：`Authorization: Bearer <token>`。
- POST 必须发送 JSON 对象并带正确 `Content-Length`；注销也要发送 `{}`。不支持 chunked／分块请求体。
- 成功 HTTP 200；失败为相应状态码与 `{"error":"说明"}`。没有另包一层 `data`。
- 当前没有 WebSocket、MQTT、SSE 或独立消息队列，也没有实时服务端推送同步。客户端主动增量拉取。
- 同一客户端应串行执行同步。每个 `(服务器, user_id)` 使用独立本地同步状态；不要把不同账号／服务器的游标和记录版本混用。

## 2. API 一览

| 方法 | 路径 | 鉴权 | 用途 |
| --- | --- | --- | --- |
| GET | `/health` | 无 | 服务与协议版本 |
| POST | `/v1/auth/register` | 无 | 注册并立即登录 |
| POST | `/v1/auth/login` | 无 | 登录并登记设备 |
| GET | `/v1/me` | Bearer | 当前账号与设备列表 |
| POST | `/v1/auth/logout` | Bearer | 撤销当前 token |
| POST | `/v1/sync` | Bearer | 上传修改并获取一页增量变化 |

没有单独的笔记、课表、删除或附件 CRUD API，均通过 `/v1/sync` 的完整记录处理。

### 健康检查

```json
{"ok":true,"protocol":1,"service":"dayflow"}
```

### 注册／登录请求

```json
{
  "username":"alice",
  "password":"example-password-123",
  "device_id":"5d20a850-77ad-43f5-b8ed-2e4120436d03",
  "device_name":"My Android",
  "invite_code":""
}
```

`username` 去首尾空白并小写，需匹配 `[a-z0-9_.-]{3,40}`；密码10–128字符。`device_id` 为每次安装持久化的 UUID，不应每次登录重新生成。`device_name` 可选、最多80字符；邀请码只在注册时使用，由服务器配置决定是否需要。服务器也可以关闭注册。

响应：

```json
{
  "token":"<opaque-token>",
  "user_id":"69b796bc-88cb-40c2-88b3-ed36365c1454",
  "device_id":"5d20a850-77ad-43f5-b8ed-2e4120436d03",
  "expires_at":1800000000
}
```

token 为不透明字符串，不是 JWT；期限为30天，`expires_at` 为 Unix 秒。当前没有 refresh-token 或找回密码接口，过期需重新登录。系统安全存储 token，不放入 `.dayflow` 导出文件。

`GET /v1/me` 响应示例：

```json
{"user_id":"69b796bc-88cb-40c2-88b3-ed36365c1454","devices":[{"id":"5d20a850-77ad-43f5-b8ed-2e4120436d03","name":"My Android"}]}
```

`POST /v1/auth/logout` 请求体 `{}`，响应 `{"ok":true}`，只撤销当前 token，不删除账号或本地内容。

## 3. “消息类型”与记录类型

同步没有 `type:create/update/delete` 消息标签，语义是完整记录的 upsert：

| 载体 | 语义 |
| --- | --- |
| `mutations[]` | 客户端待上传的新建／修改／删除记录 |
| `accepted[]` | 服务端确认写入的记录 ID 与新版本 |
| `conflicts[]` | 版本不匹配及服务端当前记录 |
| `changes[]` | 一页服务器增量变化，包含删除标记 |
| `record.deleted=true` | 软删除，不能直接物理移除并忘记同步 |

业务类型由 `record.kind` 区分，现有合法枚举：

| kind | 含义 |
| --- | --- |
| `task` | 待办 |
| `event` | 日程 |
| `course` | 某次课程，也是当天待办的一种；不是整学期的容器 |
| `note` | Markdown／文字／图片笔记 |
| `inbox` | 待确认的通知、邮件正文、截图等 |
| `project` | 项目／长期事项 |
| `type` | 0.5.0新增：可同步的任务类型定义，非日程 |

每一节课为单独记录。课程、笔记与待办使用同一同步流，不要为其另建不兼容的表和 API。

## 4. Record 数据模型

其他客户端至少按以下字段创建记录，兼容 Android 和 Windows 的校验：

```json
{
  "id":"9b85ab7b-de7d-4f6a-a73d-a4f9ac81b7d1",
  "revision":"784cfab6-cf56-483a-818d-4c3a4e093afa",
  "ancestors":[],
  "kind":"note",
  "title":"会议笔记",
  "content":"# 会议记录\n\n- 第一项\n",
  "content_format":"markdown",
  "start_at":"",
  "end_at":"",
  "time_hint":"",
  "completed":"",
  "deleted":false,
  "updated_at":"2026-10-20T03:00:00Z",
  "pinned":false
}
```

| 字段 | 类型／规则 |
| --- | --- |
| `id` | UUID，记录的永久身份；改标题、时间或类型都不换 ID |
| `revision` | UUID，每次本地修改生成新修订；网络重试不要重新生成 |
| `ancestors` | 修订 UUID 字符串数组，最多256项，用于文件合并；现有客户端保留最近254项，再追加修改前 revision |
| `kind` | 上述七种类型之一 |
| `title` | 字符串，最多1000字符 |
| `content` | 正文字符串；笔记直接传 Markdown 原文，不传 HTML |
| `content_format` | 笔记可填 `markdown`；老记录可能没有该字段 |
| `start_at` / `end_at` | 空字符串或无时区的本地 ISO 时间，如 `2026-10-20T09:00:00`，也接受分钟精度；不是 Unix 时间戳 |
| `time_hint` | 模糊时间文字，未知具体时间时 start/end 保持空字符串 |
| `completed` | 未完成为 `""`；完成为 ISO 时间字符串，一般 UTC |
| `deleted` | JSON 布尔值，默认 false |
| `updated_at` | ISO UTC 字符串，用于展示；不能据此决定云端覆盖顺序 |
| `pinned` | 笔记置顶布尔值，可选 |
| `location` | 地点文字，可选 |
| `image_base64` | 压缩 JPEG 的裸 Base64，不含 `data:` 前缀，可选 |
| `reminder` / `remind_minutes` | 是否提醒／提前分钟数，可选；由各客户端安排本地系统提醒 |
| `repeat` | 旧版 `""` / `daily` / `weekly` / `monthly` 完成后生成下一次；0.5.0新编辑使用 `recurrence` |
| `trigger_after` / `offset_days` | 前置事项 UUID／完成后延迟天数，可选 |
| `activation` | 阶段状态，现有值 `waiting` / `active` |
| `source` | 收集来源／导入来源文字，可选 |
| `conflict_of` | 冲突副本指向的原 ID，可选 |

**保留未知字段**：编辑时应复制原记录再修改已知字段，不要只序列化自己认识的字段，否则会丢失其他客户端的功能数据。

时间目前采用“浮动本地时间”。跨时区客户端不应擅自把 `start_at` 转为 UTC；当前协议没有独立 `timezone` 字段，未来需要通过明确协议升级扩展。

单条记录以 JSON UTF-8 计，服务端上限65536字节（其大小检查使用 Python 默认空格分隔序列化，客户端要预留少量余量）。图片、正文、修订历史都计入；没有独立的附件上传接口。

## 5. 增量同步请求／响应

请求：

```json
{
  "cursor":0,
  "mutations":[
    {
      "base_version":0,
      "record":{
        "id":"9b85ab7b-de7d-4f6a-a73d-a4f9ac81b7d1",
        "revision":"784cfab6-cf56-483a-818d-4c3a4e093afa",
        "ancestors":[],"kind":"task","title":"给植物浇水",
        "content":"","start_at":"2026-10-20T09:00:00",
        "end_at":"2026-10-20T09:10:00","time_hint":"",
        "completed":"","deleted":false,
        "updated_at":"2026-10-20T00:30:00Z"
      }
    }
  ]
}
```

- 首次 `cursor=0`，`mutations=[]` 可只拉取。
- 新记录 `base_version=0`；修改／删除填本地最后确认的该记录服务器版本。
- 发送的是完整 `record`，不是 JSON Patch。
- 上限100条 mutation／请求；**当前客户端采用每批20条**，避免图片笔记碰到2MiB请求体限制。
- `base_version` 和 `cursor` 是非负 JSON 整数。建议其他语言用64位整数保存。

响应示例：

```json
{
  "accepted":[{"id":"9b85ab7b-de7d-4f6a-a73d-a4f9ac81b7d1","version":1}],
  "conflicts":[],
  "changes":[],
  "cursor":0,
  "has_more":false
}
```

重要区别：

- `version` 是**每条记录**的服务器版本，每次成功写入递增。
- `cursor` 是当前账号的增量日志位置，不是记录版本，不要求连续，不能自行 `+1`。
- 服务端先取本轮推送前的变化快照，再接受写入。因此本轮上传成功的数据不必同时出现在 `changes`；用 `accepted` 更新版本。
- `has_more=false` 只表示当前快照已拉完。如果本地还有 dirty 数据，继续下一轮。

`changes` 元素：

```json
{"seq":123,"version":4,"record":{"...":"完整Record"}}
```

服务端每页最多200条，同时按记录正文约512KiB截断；HTTP JSON 包装另占少量空间。不要假设一页一定200条，也不要把分页体积当成完整 HTTP 响应的严格上限。

### 客户端处理顺序

1. 复制当前 dirty 批次，同时记住每个 ID 发送时的 revision。
2. 调用 `/v1/sync`。
3. 处理 `accepted`：保存服务器 version；**只有当前本地 revision 仍等于发送时 revision 才清除 dirty**，防止请求期间的新编辑丢失。
4. 处理 `conflicts` 与 `changes`，保存记录和版本。只应用比本地已确认版本更高的远程记录，避免重复拉取倒退。
5. 远程记录覆盖未上传本地版本时，先保留本地冲突副本。
6. 所有这一页记录持久化成功后，才保存响应 cursor；尽量在事务里提交。
7. 若 `has_more=true` 或还有 dirty 数据，继续；否则结束。

断网后可以用**原始同一条记录**重试。服务端检测到记录完全相同时返回 accepted，不重复制造版本；不要为了重试生成新 revision 或更新时间。

## 6. 冲突语义

普通版本冲突的 HTTP 状态仍为 **200**，在响应 `conflicts` 数组中：

```json
{
  "id":"9b85ab7b-de7d-4f6a-a73d-a4f9ac81b7d1",
  "version":5,
  "record":{"...":"服务器当前完整Record"}
}
```

服务端没有对应记录时，version 可为0、record 为 null；客户端应重新核对 base_version，不能无限发送原错误版本。

现有客户端策略：保留本地未上传修改的副本，再接受服务端记录。副本字段：

- `id`：**UUID v3，无 namespace**，对 UTF-8 字节 `原id + ":" + 原revision` 做 MD5，再设置 RFC4122 version=3／variant 位。等价 Java `UUID.nameUUIDFromBytes(...)`，不是带 namespace 的标准库 `uuid3(namespace, name)`。
- `title`：`[冲突副本] ` + 原标题前990字符。
- `conflict_of`：原 ID；`deleted=false`。
- 其他字段及原 revision／ancestors 保留，作为新记录以 base_version=0 上传。

这可让多端为同一版本创建相同冲突 ID，避免重复副本。不要用设备时间的 last-write-wins 静默覆盖内容。

HTTP 409 则用于账号重复、游标越界等请求级错误，区别于上述记录级版本冲突。

## 7. 删除、重复任务与课程

删除：修改原记录 `deleted=true`，生成新 revision／ancestors，按原服务器 version 上传；保留 tombstone 供后续设备同步。恢复同理改回 false。

课程：CSV／ICS 导入会展开为多条 `course` 记录。某节完成只更新该节的 `completed`。新客户端可自行使用 UUID，但同一来源重复导入应保持稳定 ID。

重复待办：现有客户端在用户完成当次后，按开始时间加一天／一周／一月，保留时长，生成下一条；下一条 ID 用上述无 namespace UUID v3 算法，输入 `当次id + ":next"`，避免多设备同时完成造成重复。不要让服务端另起一套自动展开逻辑。

阶段：`activation=waiting` 的事项等 `trigger_after` 对应事项实际完成后，由客户端激活。规则字段随记录同步，服务器不运行任务逻辑或发送日程提醒。

## 8. 离线文件同步与 .md 导出

完整 `.dayflow` 是 UTF-8 JSON：

```json
{
  "format":"dayflow",
  "schema":1,
  "exported_at":"2026-10-20T03:00:00Z",
  "records":[{"...":"完整Record，包含删除标记"}]
}
```

不包含服务器版本／游标、设备凭据、密码、API Key、权限或壁纸。文件为明文。当前 Android 导入最多16MiB／20000条，导出按正文长度有安全体积限制；这些是客户端限制，不是服务端 API。

合并：同 ID／同 revision 跳过；本地 ancestors 包含远程 revision 则保留本地；远程 ancestors 包含本地 revision 则更新；无法判断时建立冲突副本。合并得到的新修改要标记 dirty，继续上传云端。

**`.md` 与同步消息不同**：`.md` 是可移植笔记正文文件；ID、置顶和修订等元数据仍由 Record 管理。.md 导入创建新笔记，不能拿同名 .md 文件覆盖猜测记录身份。完整往返同步使用 `.dayflow` 或 `/v1/sync`。

## 9. 错误与限制

| 状态 | 常见含义 |
| --- | --- |
| 400 | 无效 JSON、字段／UUID／mutation 数量错误、分块请求不支持 |
| 401 | 未登录、token 失效或密码错误 |
| 403 | 注册关闭／邀请码错误 |
| 404 | 接口或更新文件不存在 |
| 409 | 用户名已占用／cursor 超出当前账号日志范围 |
| 413 | 请求体超过2MiB，或 POST 未提供有效非空请求体 |
| 429 | 同来源认证尝试过多，当前窗口15分钟、上限20次 |
| 500 | 内部错误；不要据此清除本地数据 |

当前不会为服务端异常返回可稳定依赖的错误 code 枚举，只有 HTTP 状态和 error 字符串。客户端不要按中文消息全文硬编码业务流程。

## 10. 安装包更新接口（独立于业务同步）

可选服务器公开 GET：`/updates/android-update.json`、`/updates/windows-update.json`、`/updates/DayFlow-android.apk`、`/updates/DayFlow-windows.zip`。也可直接使用 GitHub Release URL。

清单外层：`{"payload":"Base64原始JSON字节","signature":"Base64签名"}`。以项目公钥验证 RSA PKCS#1 v1.5／SHA-256，验证时使用解码后的原始 payload 字节，不重新序列化。

payload 字段：`schema:1`、`platform`、`version`、`version_code`、`url`、`sha256`、`size`、`notes`。验证平台、版本、URL、文件大小和 SHA-256 后再安装。没有通用的其他平台安装包分发路由；新增平台需扩展现有 allowlist 与签名流程。接口不分发动态 Android 代码。

## 11. AI 接口与预设（客户端直连，不经过同步服务器）

Android 提供 DeepSeek、通义千问／视觉、Kimi、OpenAI 与自定义预设。服务商根地址、模型与 Key 是各设备私有配置，不加入 Record 或同步文件。预设与请求参数见 `AiPresets.java`。

客户端以 `POST <根地址>/chat/completions` 调用 OpenAI 兼容格式，使用服务商自己的 Bearer Key。请求包含 `model`、`messages`、`stream:false`，部分模型另带关闭思考参数。文字消息为字符串；图片消息使用 `[{"type":"text","text":"..."},{"type":"image_url","image_url":{"url":"data:image/jpeg;base64,..."}}]`。

读取 `choices[0].message.content` 中的 JSON，日程提取约定为 `{"items":[{"title":"...","start_at":"本地ISO或空","end_at":"本地ISO或空","location":"...","time_hint":"..."}]}`，最多20项。客户端验证后创建 `kind=inbox`、`source="AI 待确认"` 的本地记录，由用户确认再进入日程。使用同步服务器的登录 token 不能调用模型供应商接口，模型 Key 也不能作为同步服务器 token。

## 实现参考

- 服务端：`server/server.py` 的 `Store.auth`、`Store.sync`、`Handler.dispatch`。
- Android：`Data.java`、`Network.java`、`SyncJob.java`、`MarkdownFiles.java`。
- Windows：`LocalStore.cs` 与 `MainWindow.xaml.cs`。

本文基于源码整理。本轮按用户要求没有运行协议或客户端测试。

## 12. Android 0.5.0 任务模型扩展

路由和 CAS／游标语义仍为 v1。服务器0.5.0允许 `kind=type`，`/health` 附带 `version:"0.5.0"` 与 `capabilities:["types","recurrence"]`；旧服务器会拒绝类型定义，需升级服务端后同步。字段均仍包含在完整 record 中，其他客户端应原样保留未知字段。

| 字段 | 含义 |
| --- | --- |
| `type_id` | 自定义类型记录 UUID；空串表示沿用 kind |
| `parent_id` | 对业务事项为所属长任务 UUID；对 type 为父类型 UUID |
| `status` | `planned` / `done` / `cancelled`；完成同时更新原有 `completed` |
| `all_day` | 全天；start_at/end_at 使用本地日期的00:00，结束排他 |
| `due_date` | 无具体时段待办的截止日期，`YYYY-MM-DD`；不向 start_at 写入伪造时刻 |
| `recurrence` | `""` / `daily` / `weekly` / `monthly`，只有系列模板设置非空值 |
| `weekdays` | 周重复的 ISO 星期数组1–7；空数组用开始日期的星期 |
| `recurrence_until` | 重复结束日期，含当天，空串表示未设置 |
| `series_id` | 单次覆盖记录关联的系列模板 UUID |
| `occurrence_date` | 此次重复原始所属日期，即使单次被移到其他日期也保留 |
| `reminder_offsets` | 最多5个非负提前分钟的逗号分隔字符串，例如 `10,60,1440` |

`kind=type` 的其他字段：`title` 名称、`icon`、`color`（#RRGGBB）、`base_kind`（event/project/task）、`system_type`、`archived`、`merged_into`。层级最多三级，子类型继承基础行为。归档不删除既有引用；合并会迁移引用并归档来源。

重复实例由读取端生成，不预先同步成百上千个未来副本。实例 UUID 算法：对 UTF-8 `dayflow:occurrence:<series UUID>:<YYYY-MM-DD>` 计算 MD5，设置 UUID version3 与 RFC4122 variant；等价 Java `UUID.nameUUIDFromBytes`，**不额外拼接命名空间**。

单次未修改时其临时 revision 由 `<模板 revision>:<日期>` 按相同 UUIDv3 算法派生；第一次保存单次覆盖时必须生成随机新 revision，临时 revision 放入祖先。保存／完成／软删除单次，传输带 `series_id` 和 `occurrence_date` 的普通完整 record。读取某次日期时，存在同实例 UUID 的落库记录（含 tombstone）便抑制模板生成，避免删除后重新出现。

系列修改应用于未覆盖的实例，已覆盖实例保持自己的字段。删除／取消／完成整个系列时 Android 日历隐藏其覆盖实例。每月同日遇到无该日期的月份跳过，不移动到月末。日程时刻沿用本地无时区 ISO 约定。

AI 扩展 items 字段：kind（event/task/project）、due_date、all_day、recurrence、weekdays、recurrence_until、reminder、remind_minutes、type_hint。识别结果仍是 inbox，拟定行为存在 proposed_kind，需用户确认；API Key 不进入这些记录。
