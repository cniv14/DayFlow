# Android 1.0.0：连接现有 TaskFlow Windows

这是三次发布中的第二次。保留0.5.0全部 Android 功能，增加直连用户提供的 TaskFlow 0.9.1 Windows 同步服务。Windows 源码及二进制均不修改。

## 使用

1. 在已有 TaskFlow Windows 的“设置 → 同步”启动锚点，设置同步密码。
2. Android“设置 → 数据与同步 → 连接 TaskFlow Windows 端”。
3. 填写电脑局域网 IP 和 Windows 显示的实际端口，例如 `192.168.1.20:17800`，输入同一同步密码。
4. 点击“连接 Windows”，成功后可立即同步，后续也会按系统调度自动同步。

没有二维码或配对码界面。底层使用 Windows 已有的密码挑战／HMAC 认证接口获得 token，密码不长期保存。失败保留输入及本地数据。窗口关闭后 Windows 锚点是否继续运行，取决于原 Windows 应用自身。

普通 DayFlow 云端仍可从原账号页登录。当前自动同步后端由最近一次成功连接决定，界面显示后端名称；两套凭据与同步基线分开。服务器／工作区 ID 发生改变时停止合并，先核对和备份。

## 同步内容

| 内容 | 行为 |
| --- | --- |
| 普通日程、待办、长任务、父子关系 | 双向映射 TaskFlow 的 `schedule` |
| 三级类型、颜色、图标、归档与合并 | 双向映射 `task_type`，保留系统默认类型 ID |
| 定时／全天／无日期、截止日期、完成／取消／删除 | 映射原 Windows 字段 |
| 重复星期、提醒规则、Markdown 备注 | 映射重复、通知 JSON 与 Y.Text |
| Android 笔记／图片笔记、收集箱、单次重复覆盖 | 保存在同一 Yjs 工作区的 `dayflow_record` 扩展表 |
| 其他 Android 扩展字段 | 保存在对应记录的扩展元数据中，不要求 Windows 理解 |

原 Windows v2 只投影 `schedule` 与 `task_type`，因此没有新增 Windows 笔记页面。Android 的笔记仍可经该锚点传递到其他 Android 设备。原 Windows 的重复任务 UI 操作整个系列，Android 单次完成／删除不会强行伪装成整个系列完成；单次覆盖数据保留供 Android 展示。

并发修改比较本地上次同步快照：不同字段合并，同一字段冲突保留本地副本。原有 `.dayflow` 合并与 DayFlow HTTP 增量同步继续保留。

## 资源与兼容

使用 MIT 的 Yjs13.6.33、y-protocols1.0.7、lib0，以及 AndroidX JavaScriptEngine。协议代码随 APK 本地打包，界面保持原生 Android。系统需提供支持 JavaScriptSandbox 的较新 Android System WebView；不支持时提示更新，离线和文件模式继续可用。

连接按需建立，任务结束关闭 WebSocket、隔离环境、连接池及网络线程。不添加常驻服务。引擎堆上限64MiB，缓存最多16MiB，单条网络消息最多8MiB、待处理消息合计最多16MiB，单表最多20000行；超过限制明确报错并保留 SQLite 数据。

同步使用单独 Yjs 缓存及 SQLite 基线；成功写入缓存后再提交基线，失败保留记录供下次重试。客户端同步时仍可离线编辑，检测到同条记录同时在页面中变化时停止该次覆盖。

## 构建与验收边界

Android 版本码100，沿用现有应用 ID、签名证书与签名更新清单。只编译打包，没有运行自动测试、模拟器、实机、双机连接、实际云服务器或付费 AI 调用。适配是根据压缩包源码完成的，设备 WebView、Windows 防火墙和双向同步效果需由用户实测。

下一次1.1.0将继续完善资源占用、操作体验和同步恢复，不修改 Windows 端。
