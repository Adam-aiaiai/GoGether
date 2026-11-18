# 成员 D：实现、接口与验收

## 当前可以运行的内容

分支 `feature/coordination`。默认启动本地持久化 Demo，不需要 Firebase 账号。
Application 将完整快照写入私有目录 `coordination-v1.json`。写入临时文件并原子替换成功后才发布 UI 状态；保存失败保留旧快照。重启保留账户选择、活动、报名、公告、集合信息、通知及阅读状态。Fake 不传 store 时仍为独立的内存测试仓库。

组织者在 Manage event 发布公告和编辑集合地点/时间/说明。公告单独保存历史，并向正式参与者及候补发送站内通知。成员可以在 My Events 打开活动查看公告。Profile 的 Inbox 可打开活动并标记已读。活动详情、组织管理及签到页均显示更新后的集合信息。

集合时间使用新字段 meetingAt；meetingLocation、meetingNotes 同理。它们不改变活动 startAt、endAt、报名截止或容量。B 修改活动时应保留这些字段；缺失时界面回退到原活动时间和地点。

## 网上数据库接入（尚未配置和部署）

现已提供 Firebase Android 数据源、Callable 后端和权限规则。当前 Application 仍注入本地仓库，不能把两台手机上的 Demo 当作已联网。没有 Firebase 项目和 A 的真实认证时不自动切换，不接受 Demo 昵称作为云端身份。

1. 团队创建同一个 Firebase 项目，注册 Android 包 `com.gogether.app`，取得 app/google-services.json。由 A 配置 Google Services 插件及 Firebase Authentication。项目已忽略该配置文件。
2. 在 firebase/functions 执行 npm install；在 firebase 执行 npm install。先使用本地模拟器。真正部署 Callable Functions 通常需要项目启用相应计费方案，由团队决定。
3. 在 firebase 目录将 CLI 连接到指定项目，部署协调函数和经团队合并的 Firestore 规则。这里的规则默认禁止客户端直接写活动和报名，A/B/C/E 需补上自己模块的可信后端；不要直接覆盖团队已有规则。
4. A 登录后通过 FirebaseAuth 的 UID 建立订阅。构造 FirestoreCoordinationDataSource(FirebaseAuth.getInstance(), FirebaseFirestore.getInstance(), FirebaseFunctions.getInstance())。默认函数区域为 us-central1。
5. 团队真实 Repository 将 myActivities / notifications / announcements(eventId) / participants(eventId) 的 Flow 接入各自状态；现有 UI 通过 AppSnapshot 渲染。通知仅订阅当前用户；退出或切换用户时取消旧订阅并清空旧内容。数据源在身份变化时会关闭旧流，界面必须捕获错误并新建订阅。
6. 调用 postAnnouncement / updateMeeting 时为一次提交生成 UUID requestId。网络重试沿用相同 ID；新内容用新 ID。服务端在一次事务中验证身份、读取参与名单、更新内容和写通知，相同请求不会重复发送。
7. markRead 只修改 isRead；必须等待成功回调才显示服务端已确认。SyncedData.fromCache 表示缓存来源，pendingWrites 表示尚未确认的本地写入。两者须显示为离线缓存/同步中，监听失败需提供重试。不要把缓存快照称为云端已同步。

## 共享数据契约（供队友接入）

时间字段统一为 epoch milliseconds；枚举保存 Kotlin 枚举名。所有文档包含模型对应字段；新 meeting 字段可省略。服务器从认证 token 取 UID，客户端传入的 actor 不可信。

| 集合 | ID / 字段 | 负责方 |
| --- | --- | --- |
| events | 活动 ID；Event 原有字段，新增 meetingAt/meetingLocation/meetingNotes | B 负责活动；D 只更新集合字段和 updatedAt |
| registrations | 必须为 eventId_UID；eventId/userId/status/joinedAt/updatedAt/queueOrder | C；状态变更与补位在可信事务中完成 |
| announcements | 服务端生成；eventId/authorId/content/createdAt | D |
| notifications | id/userId/eventId/type/content/createdAt/isRead | D 提供规范；C 在补位事务中生成 PROMOTED，取消活动方生成 EVENT_CANCELLED |
| coordinationRequests | UID_requestId；hash/createdAt | D 后端幂等记录，客户端禁止访问 |

当前云端通知一次支持最多 400 个不同收件人；超过时整体拒绝，不会部分发出。名单变更与通知写入由同一事务协调。大规模活动需队列分发方案。
公告权限为组织者及当前 CONFIRMED/WAITLISTED 成员；退出后不再读取公告历史，已送达的个人通知仍保留。组织者可查看参与名单，普通用户只可查询自己的报名。数据库规则测试覆盖这两种查询。
此 ID 约定只用于云端。Demo 的 reg-event-… ID 不应直接导入云端，导入或种子脚本须转换。

## 操作演示

1. 启动 App，选择 Alice，进入 Badminton → Manage event。
2. Edit meeting information 修改集合信息，保存；发布一条公告。
3. Profile 切换 Bob → My Events → Badminton，查看公告和集合地点；Check-in 页面也显示更新信息。
4. Profile → Inbox → Mark as read。强制关闭应用再打开，检查已读状态和公告仍在。
5. 选择 Emma，验证自己的通知仍未读。选择 Finn（未报名），活动详情不展示参加者公告。

默认 Demo 保留本机数据。清除应用数据会删除本地记录并恢复种子，不作为日常刷新操作。没有设置通知栏系统推送；FCM 属于可选项。

## 测试及尚待实际联调

Android: gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:connectedDebugAndroidTest
后端输入测试: node --test firebase/functions/validation.test.js
数据库模拟器: 在 firebase 运行 npm test（需要 Java 和 Node），使用 demo-gogether，完全不连接正式数据库。

模拟器自动测试验证通知收件人隔离、仅修改已读字段、公告参与资格、报名查询权限、两个独立客户端同步及断线重连。它不能代替两台 Android 设备的完整验收。
待团队配置真实 Firebase / 认证并把数据源接入真实 Repository 后，按以下步骤验收：设备 A 组织者发布 → 设备 B 参与者在 My Events 看到公告及通知 → B 标已读 → 同一账号另一设备同步已读 → B 断网、A 再发布、B 恢复网络后更新。另测 A/B 退出换号不残留前一账号通知，以及 C 补位后 Joined/Waitlisted 同步。
没有配置云项目、未接入 A/C 真实模块、未执行真实两手机验收之前，D1 的团队接口确认和 D6 的手机联调仍是待办，不能标成全部完成。

## 2026-11-18 交互完善

My Events 新增通知入口及未读数；Upcoming 按最近活动排序，History 展示已结束/取消活动。空列表提供 Explore/Create 按钮。通知支持 All/Unread 和 Mark all as read，后者只更新当前用户并持久保存。活动失效时提供 Back to Explore。

集合信息使用系统日期/时间选择器，在地点和时间旁显示校验错误，成功后关闭编辑区。公告显示字数和发布中状态，发送期间禁用输入及按钮，成功清空、失败保留草稿。

共享 Repository 新增 markAllRead()，真实实现可调用 FirestoreCoordinationDataSource.markAllRead()。云端按每批 450 条更新，超过一批时不保证整批原子完成；失败可安全重试。当前 App 默认仍运行本机持久化 Demo。

会议操作详见 MEETING_DEMO.md。原有的日期文本输入说明由日期和时间选择器取代。
