# Agent插件内部接口 v2

1.3.0移除蓝心小V/小爱授权入口、外部Bearer密钥和127.0.0.1:8766桥接。该文档描述日栖本体与官方Agent插件之间的内部调用，不是公开HTTP API。云同步协议保持v1，见客户端同步API.md。

## 启动与会话

包名app.dayflow.assistant，显式Activity app.dayflow.assistant.MainActivity，action `app.dayflow.action.ASSISTANT`，`startActivityForResult`请求码117。extras：dayflow_protocol=2、dayflow_session（32随机字节临时授权）、dayflow_model/provider、dayflow_seed、dayflow_dark/font_size/compact。资料整理入口另传dayflow_seed_message、dayflow_review_only及dayflow_timetable。仅从宿主调用，插件无桌面Launcher和额外账号。

宿主记录当前会话，8小时失效，返回后立即撤销。插件请求必须有相同发布签名的INTERNAL权限及正确会话；Provider的旧authority保留为 `content://app.dayflow.api` 供已有Markdown插件提交，类改为PluginProvider。旧开放API方法全部拒绝。

`ContentResolver.call`方法：

| 方法 | 输入 | 输出 |
| --- | --- | --- |
| assistant_model | session、request_uri；文件含messages、tools | status、response_uri；响应文件是模型assistant消息 |
| assistant_tool | session、request_uri；文件含name、arguments、request_id UUID | status、json或error |
| assistant_status | session、request_uri（空对象） | 当前会话创建ID与修订映射 |
| assistant_undo | session、request_uri；ids数组 | undone数量、skipped数组 |

参数文件只接受app.dayflow.assistant.files授权URI，宿主读取后用自己的Keystore Key和模型配置发起网络请求；不将AI Key、云端账号令牌或整库传给插件。模型响应通过app.dayflow.files临时读授权返回。插件finally撤销参数授权并删除文件；宿主结束会话时撤销响应授权并删除输入资料与响应文件。

## 工具与人工核验

| 工具 | 行为 |
| --- | --- |
| today | 指定日期任务摘要，offset分页，每页30条 |
| search_notes | 关键词搜索笔记摘要，offset分页 |
| create_task | 直接请求模式创建待办/日程，支持开始/结束/持续分钟、地点、重复、提醒 |
| create_note | 创建Markdown笔记 |
| capture | 创建inbox/pending，保留原始资料引用 |
| stage_timetable | 仅在选择课表截图后可用，写入校正表格草稿，不导入正式课程 |

通知/短信/聊天资料整理模式由本体保存，模型不能修改。此模式下即使调用create_task，宿主也强制改为inbox/pending；source_ids记录原始资料ID及修订。必须打开原生日程编辑器核验后保存。课表必须在原生表格校正后确认导入。工具不能删除、改写既有用户记录或直接确认资料。

创建ID按request_id确定，重复提交同一request_id不新建记录。撤销只适用于当前会话登记的创建记录，且修订仍一致；手动编辑、同步更新或已删除的记录跳过，保留同步删除标记。未知工具报错。模型每轮最多6次往返、24个工具执行，防止失控调用；到限后明确停止，已经成功的操作保留。列表分页/聊天上下文预算不等于Markdown大小限制。

模型对话编排在插件；本体保留数据校验、AI传输、授权及同步。截图识别和通知整理使用同一条工具调用链，移除原独立JSON提取器和重复AI操作页。只做编译打包，未调用真实AI或运行实机验证。
