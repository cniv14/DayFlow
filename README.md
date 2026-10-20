# 日栖 DayFlow

以 Android 为核心的离线日程、任务与笔记应用，配套 Windows 桌面挂件和可选的自托管同步服务。MIT 开源。

**不注册、不联网、不部署服务器，也能使用本地核心功能。** AI、天气、同步和更新检查均为可选联网能力。

## 测试版功能

| Android | Windows | 可选服务端 |
| --- | --- | --- |
| 首页仅今天待办；独立表格编辑、模糊时间、月历 | 可置顶、拖动、缩放的挂件窗口 | 账号注册、登录、注销、设备标识 |
| Markdown／压缩图片笔记、搜索、置顶 | 任务与笔记编辑、完成、删除 | 密码 scrypt 哈希、可撤销的过期会话 |
| QQ / 微信 / 邮件应用通知收集箱 | 文件合并与增量同步 | 按用户隔离的 SQLite 存储 |
| 粘贴正文、手动调用文本／视觉 AI 提取多个事项 | 壁纸、透明度、背景模糊与轻量动效 | 版本比较与冲突返回 |
| CSV 单双周课表、基础 ICS 导入 | 每6小时可选更新检查 | 删除标记、分页增量与重试去重 |
| 完成后生成下一次的日／周／月任务 | 签名验证、下载与重启更新 | Docker、可选 Caddy HTTPS |
| 前置事项实际完成后触发后续任务 | 更新保留本地用户数据 | 可发布签名更新清单与两端安装包 |
| 通知提醒、系统日历／系统闹钟入口 | 本地 JSON 数据库 | 不要求 Redis、外部数据库或 AI 运行时 |
| 桌面小组件、快捷设置磁贴、应用内边缘手势 | Windows 10/11，.NET 10 Desktop Runtime | Python 3.12+，标准库运行 |
| 原生深色主题，Android 12+动态强调色／背景模糊 | | |

安装包及操作步骤见 [测试说明](docs/测试说明.md)。

Android 0.4.0 内置原生 Markdown 编辑器，支持真实 .md 保存、单篇／批量导出和桌面主题图标，见 [新版使用说明](docs/Android-0.4.0.md)。今天首页与统一待办操作见 [0.3.0说明](docs/Android-0.3.0.md)。复用 MIT 项目 Calendar View 与 lunar-java，并使用 Apache 2.0 的 Markwon 原生笔记渲染。Android 最低支持8.0；Windows 与服务端同步修正也包含在本次下载中。

## 项目结构

```text
android/             原生 Java / Material Components / AndroidX / SQLite
windows/             WPF 挂件与独立更新辅助程序
server/              Python / SQLite 同步与更新分发服务
shared/              Windows 更新签名验证协议
examples/            课表模板
tools/               构建、签名、发布工具
tests/               离线合并与更新协议测试
update-public.pem    更新签名验证公钥，可公开
.signing/            本机私钥与 Android 签名证书，已忽略，严禁提交
```

## 构建

Android 使用 JDK 17、Gradle 9.6.1、Android SDK 36，Android Gradle Plugin 9.3.1。可使用 Android Studio 打开 `android/`，或用已安装的 Gradle 执行：

```powershell
cd android
gradle :app:assembleDebug
```

正式 APK 需要稳定签名。维护者先执行 `python tools/release_keys.py`（仅构建时需 `cryptography`），在本机 `.signing/` 生成 Android 证书与更新签名私钥。**请离线备份此目录；丢失证书将无法为已安装版本提供兼容更新。** 对本项目进行 fork 时应生成自己的密钥、公钥和应用 ID。

```powershell
cd windows
dotnet build -c Release
dotnet build Updater/DayFlow.Updater.csproj -c Release
```

Windows 打包必须把 `DayFlow.Updater.*` 与 `DayFlow.Widget.*` 一起放到应用目录。设置和日程位于 `%LOCALAPPDATA%\DayFlow`，不位于安装目录。可用环境变量 `DAYFLOW_DATA_DIR` 选择独立测试数据目录。

## 同步协议

本地生成 UUID 事项 ID 与设备 ID，服务器独立生成账号 ID；更换设备不会改变事项 ID。云端采用服务器版本号的比较后写入，不使用手机时钟决定覆盖顺序。不同账号不能读取或修改对方记录。

文件格式为 UTF-8 JSON，扩展名 `.dayflow`，包括事项修订 ID、有限的祖先历史和删除标记。导入时：

1. 相同 ID 和修订不重复创建。
2. 新修订是本地修订的后代时更新原事项。
3. 并发修改或历史不足以判断时保留明确标记的冲突副本。

文件不含 API Key、密码、会话令牌、壁纸或设备配置。图片笔记以压缩 JPEG 内嵌，可随文件和云端同步。文件为明文。Android 单次导入最多16MB；导出有内存保护上限，超过上限时提示改用服务器增量同步，分卷导出尚未实现。

账号绑定后不能直接把同一个本地库切换给其他账号；需要先备份，再建立独立数据目录或清除应用数据重新配置。服务器变更时请先导出，不能沿用旧服务器的同步游标。

## 服务器与更新

完整说明见 [服务器部署](docs/服务器部署.md) 和 [更新机制](docs/更新机制.md)。默认端口 **8765**，客户端填写完整地址及端口。云端使用 HTTPS，显式填写局域网 IP 时可使用 HTTP。

维护者使用官方 GitHub CLI 发布。Windows 已配置系统代理而 CLI 无法联网时，可用 `python tools/gh.py auth login --hostname github.com --web --git-protocol https`；包装脚本只让子进程沿用现有代理并开启 Git TLS 校验，不修改系统网络配置。

## 当前限制

- 通知通过系统通知访问授权采集；应用隐藏的内容、未发到通知栏的聊天、历史聊天不能保证读取。Shizuku 增强尚未接入。
- 截图使用用户选择的视觉 AI 接口；没有内置端侧 OCR。邮件目前通过邮件应用通知、粘贴正文或系统分享导入，没有 IMAP 邮箱抓取。
- 课表先支持标准 CSV 与基础 ICS，不含各学校教务系统登录适配。图片自动压缩，不保留原图。
- 万年历使用 lunar-java 离线显示农历、节气与传统节日，天气按需刷新并缓存；界面尚未提供调休班表、宜忌或学校校历。
- 左下角右滑目前为应用内手势；全局悬浮手势尚未接入。
- Android 的普通通知提醒受系统省电策略影响，当前安排最近100个可提醒事项；系统闹钟接口只传时分，不能表达任意未来日期。系统日历通过确认页面导出，重复导出可能产生重复项，目前没有双向日历同步。
- 每日清单／表格编辑／笔记查询最多200条；表格每页30项，笔记支持标题／正文搜索。全库分页与大量附件管理待完善。
- 阶段任务支持“前置事项完成 + 延迟天数”，未包含任意逻辑表达式。重复任务在用户完成后才生成下一次，不会自动补齐所有漏做周期。
- 更新为原生应用的在线升级：Android 系统确认安装，Windows 重启替换。没有动态下发和执行未安装的 Android 代码。
- 服务端目前适合个人／家庭自托管试用，未实现找回密码、管理员后台、同步日志压缩、集群部署和端到端加密。HTTPS 保护传输，服务器管理员可访问数据库内容。

## 验证

```powershell
cd server
python -m unittest -v
cd ../tests
dotnet run -c Release
```

Android 自定义 instrumentation 测试运行真实 SQLite、导入解析和 Keystore。测试包是独立的开发产物，不随用户 APK 安装。`artifacts/` 中的截图、测试档案及本机测量不作为源码发布。

0.1.0结果：15项服务端测试、13项 Android 设备检查、10项离线合并检查及4项更新协议检查通过；Android Lint 和两端编译通过。该版本 Android 11 模拟器小数据量前台空闲 PSS 约45MB，不能用于推断0.2.0或其他机型的内存。Android 0.2.0、0.3.0与0.4.0按用户要求只做编译，不运行测试，行为由用户实测。

## 许可证与依赖

本项目代码采用 [MIT License](LICENSE)。Android UI 依赖的 Material Components 和 AndroidX 使用 Apache 2.0，详见 [第三方声明](THIRD_PARTY_NOTICES.md)。Android SDK、OpenJDK、.NET、Python、Caddy 与构建工具各自保留其原许可证；MIT 不替代这些许可证。天气来自 [Open-Meteo](https://open-meteo.com/)，AI 使用用户自行配置的服务。

其他客户端开发请参阅 [同步消息类型与 API](docs/客户端同步API.md)。
