# Android → TaskFlow Windows 0.9.1 协议适配

适配对象是用户提供的 `taskflow-src.zip` 中的 `sidecar/src/server.ts`、`auth.ts`、`yws.ts` 和 `src/data/v2/sync.ts`。只读取这些文件确定协议，没有分发或修改其代码。DayFlow 自身 HTTP `/v1/sync` 协议另见《客户端同步API》。

## 鉴权与连接

- `GET /v1/info`：protocol=1、serverId、epoch、requiresSetup、serverName。
- `POST /v1/pair/start`：请求 `{deviceId}`，响应 salt、iterations、nonce、serverId、epoch。界面将它作为密码登录的一部分，不提供二维码流程。
- PBKDF2-HMAC-SHA256：UTF-8密码、base64url解码salt、返回iterations、32字节输出。
- context：`taskflow-pair-v1|<serverId>|<deviceId>|<nonce>|<epoch>`。
- proof：派生密钥对UTF-8 context计算HMAC-SHA256，base64url无填充。
- `POST /v1/pair/finish`：`{deviceId,deviceName,platform:"android",nonce,proof}`，得到token、serverId、epoch。
- WebSocket：`/ws/taskflow-workspace-v1?token=<encoded>&device=<encoded>`；HTTPS地址使用wss，局域网HTTP使用ws。

client固定服务器ID；epoch变化时要求重登。密码不落盘，token单独加密保存。Android不访问Windows管理员接口，不修改密码或启动／停止Windows服务。

## Yjs

使用Yjs13.6.33、y-protocols1.0.7、lib0.2.119，编码保留原协议：外层varUint0表示sync，内部使用syncStep1／syncStep2／update。不发送光标或awareness。同步前后通过state-vector交换确认一轮状态，缓存完整Yjs update；SQLite仍是Android的离线数据源。

`task_type`、`schedule`中的行是Y.Map，schedule.notes是Y.Text。只修改已映射字段，保留其他字段；备注按公共前后缀计算局部文字改动。未知顶层表不清空。

## 字段对应

| TaskFlow | DayFlow |
| --- | --- |
| task_type.name / baseBehavior | type.title / base_kind（ordinary→event,long→project,todo→task） |
| task_type.parentId | type.parent_id |
| icon / color / archivedAt / mergedIntoId | icon / color / archived / merged_into |
| schedule.typeId / parentId | type_id / parent_id |
| title / notes(Y.Text) / location | title / content / location |
| timeMode=timed,startAt/endAt毫秒 | 本机时区的start_at/end_at |
| timeMode=allday,startDate/endDate | 全天00:00日期范围，结束排他 |
| timeMode=undated,dueDate | 无具体时段的due_date |
| status / completedAt / deletedAt | status / completed / deleted |
| recurrenceJson.kind/weekdays | recurrence / weekdays |
| notificationJson.enabled/rules.offsetMinutes | reminder / reminder_offsets（TaskFlow提前为负，Android提前为正） |
| attributesJson.dayflow | Android额外字段，保留课程、模糊时间等元数据 |
| dayflow_record[id].recordJson | 完整Android笔记、收集箱、单次重复覆盖 |

`type-ordinary`、`type-long`、`type-todo`映射到DayFlow稳定默认类型UUID；普通有效UUID原样使用；其他外部ID使用`UUID.nameUUIDFromBytes(UTF8("taskflow:<table>:<id>"))`生成本地UUID，记录原始ID以供返回。

DayFlow类别转换需要改变所在表时，原表行写入deletedAt及dayflowMovedTo，目标表保留同一外部ID；Android忽略已转移行，防止旧副本复活。

## 冲突与失败

每条记录保留上次本地快照与远端行快照。不同字段的修改合并，同一字段冲突保留本地副本。缓存先持久化，再提交SQLite基线；超时或进程退出不清空本地记录。界面编辑与同步发生同记录并发时，通过revision检查停止覆盖。

注意原Windows只显示自己的v2表，不会显示新增扩展表，也不支持Android的单次重复完成语义。因此扩展内容能在同工作区Android客户端间恢复，但不宣称Windows已拥有笔记编辑或单次重复管理UI。

## 构建

`android/sync-engine`使用锁定版本和MIT依赖。维护者执行`npm ci --ignore-scripts`、`npm run build`生成随APK打包的本地协议代码及许可。Android运行时只执行该本地代码，接收的通知／笔记／远端字段作为JSON数据处理，不作为可执行脚本。

本轮仅做编译，未运行协议测试、双机实验或用户服务器连接。真正的连接结果由用户实测。
