# Android插件API v1

插件是用户单独安装的Android APK，通过Activity Intent通信，不是动态dex、脚本注入或网络服务。主应用不向插件传递账号、Key、完整数据库或同步令牌；正式数据仍使用客户端同步API.md。

## 发现和打开

插件Activity声明exported=true，Intent filter包含action `app.dayflow.action.EXTENSION`与category `android.intent.category.DEFAULT`。主应用queryIntentActivities发现后按包名记录启用状态；首次安装默认启用，但必须由用户打开。请求使用显式ComponentName及整数extra `dayflow_protocol=1`，startActivityForResult请求码114。非同签名插件会显示来源确认页，主程序不为第三方签名背书。

插件用setResult(RESULT_OK, intent)后finish。RESULT_CANCELED无导入；错误可返回字符串extra `dayflow_error`。返回以下一种数据：

- `Intent.EXTRA_TEXT`：UTF-8不超过48000字节的文字，进入收集流程，仍需用户确认。
- `dayflow_items`：JSON数组字符串，UTF-8不超过48000字节、最多40项。每项允许title、content、start_at、end_at、location、time_hint、due_date及kind。kind取event/task/course/project，其他值回退event；主应用生成全新UUID和revision，并标记inbox、confirmation_status=pending、proposed_kind。插件不能指定记录ID或覆盖既有数据。用户预览并确认后才保存。

同意插件输出并不代表自动确认真实活动。插件必须合理限制Intent体积，不通过Binder传输Bitmap或大型附件；本版没有通用附件写入API。

## 官方离线OCR

包名app.dayflow.ocr，action `app.dayflow.action.OCR`。主应用要求签名与自身一致；插件要求getCallingPackage为app.dayflow，并核对协议版本。插件公开EXTENSION入口仅用于发现。

用户主动选图→裁剪/旋转→主应用复制临时图→FileProvider content URI作为Intent data及image/jpeg MIME、ClipData、FLAG_GRANT_READ_URI_PERMISSION。输入最多12MiB，授权只覆盖本次图片。插件不申请联网/短信/全盘存储权限，解码有像素上限，单次90秒超时；返回EXTRA_TEXT或dayflow_error后结束自身进程，主应用撤销URI授权并删除共享文件。云端AI是另一个显式入口，不由OCR自动上传。

官方插件分arm64-v8a、armeabi-v7a安装包，发布附件名DayFlow-ocr-arm64.apk、DayFlow-ocr-armv7.apk；选择与设备ABI匹配的一份。不会在不支持的x86设备显示下载按钮。旧版上游原生库兼容性由实机确认。

## 构建、更新和存储

先执行 `python tools/fetch_ocr.py`，固定从RapidAI官方1.3.0 Release下载AAR并核对SHA-256；二进制依赖不提交Git。再执行 `gradle -p android :app:assembleRelease :ocrPlugin:assembleRelease`。发布签名配置仅保存在本机.signing中，fork需替换应用ID和签名及相应调用者检查。

大型后续能力优先新增插件模块；主应用只保留基础UI、数据模型与狭窄接口。主包走原有RSA签名更新清单及系统安装器，插件目前由Release手动安装/升级，不支持静默热替换代码。服务端现有updates路由不额外开放插件文件名，私有镜像请用单独HTTPS静态下载地址，不能直接假定已有分发API。

插件有独立私有目录和进程；主程序缓存清理无法删除插件模型，交给系统存储/卸载入口。插件不持有长驻服务和无期限任务。扩展启用设置、权限、缓存和正在运行的专注计时是设备配置，不参与云同步，确认后的记录正常同步。
