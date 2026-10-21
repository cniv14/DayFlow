# 本机助手API v1

本API与云端同步、AI模型Key相互独立。设置→开放API与语音助手，默认关闭；开启后生成32字节随机密钥，保存在Android Keystore保护的设备秘密配置。关闭立即拒绝调用；重置使旧密钥失效，并通知已运行的HTTP桥接停止。

## 使用入口

基础原生接口：`ContentResolver.call(Uri.parse("content://app.dayflow.api"), method, null, bundle)`，bundle包含token和json字符串。返回status、json或error。没有驻留HTTP线程，query/insert/update/delete不开放。方法：capabilities、today、search_notes、capture、create_note、create_task、complete_task。

HTTP桥接是可选DayFlow-assistant.apk，先安装本体，再在本体设置里打开插件并点击开启。只绑定127.0.0.1:8766，Bearer认证，不对局域网广播，也不使用云端账号Token；前台通知可停止，进程重启不自动开放。两条处理线程、有限队列，关闭时回收套接字。

蓝心小V、小爱同学需要厂商允许的自定义HTTP工具、智能体或自动化中间层；本项目没有获取厂商私有权限、签订官方适配或验证实机联通。若厂商工具仅支持云端URL，不能直接访问手机127.0.0.1，需要用户自选的本机桥接；不自动暴露公网或上传API密钥。

## HTTP端点

所有请求带 `Authorization: Bearer YOUR_LOCAL_KEY`，响应JSON。POST使用Content-Length，不支持Transfer-Encoding分块。

| 方法 | 路径 | 输入/作用 |
| --- | --- | --- |
| GET | /openapi.json | OpenAPI 3.0.3描述，亦附在Release |
| GET | /v1/capabilities | 支持的方法及协议 |
| GET | /v1/today?date=2026-10-21&offset=0&limit=20 | 日期可省略，查询安排摘要；翻页用next_offset/has_more |
| GET | /v1/notes/search?query=关键词&offset=0&limit=20 | 搜索笔记摘要，不返回整库正文 |
| POST | /v1/capture | title/content等，创建inbox/pending |
| POST | /v1/notes | title/content，创建Markdown笔记 |
| POST | /v1/tasks | title/content/start_at/end_at/due_date/location/time_hint，创建待办 |
| POST | /v1/tasks/complete | id，已完成时不反向切回未完成 |

创建类可选request_id（UUID），相同request_id重试返回已有记录，避免网络重试重复创建；省略则每次创建新事项。开始和结束为本地ISO时间，结束可省略。不提供删除、付款、云端Key读取或整库导出接口。

```json
{"request_id":"fa854c32-c003-4656-bb6e-89ef7ed6eb84","title":"明天下午交作业","content":"语音记录，时间尚待确认"}
```

capture为待确认收集项；create_note/create_task是用户对持有密钥工具授予的直接创建能力。API密钥只交给信任的本机工具。GET只给有限摘要，列表limit用于分页，不是笔记大小限制。大笔记正文经插件私有临时文件授权传入本体，避免Binder直接传输全文；URI不能由外部HTTP调用者指定。请求解析、编辑与保存仍受物理资源影响，不静默截断内容。

HTTP 200成功，401未授权或API关闭，400无效输入，404未知路径，405方法不匹配。停机、端口占用或系统杀进程时连接可能中断，使用request_id重试创建，避免按中文错误全文编写逻辑。

## 插件内部提交

Markdown插件另用commit_document，仅同发布者签名权限加本次随机文档授权令牌可调用，不受用户助手API开关控制，不是对助手开放的方法。验证文件SHA-256与当前记录修订后才保存；它不授予插件查询其他笔记或账号的权限。

本版只编译，未运行厂商助手或HTTP连通测试。
