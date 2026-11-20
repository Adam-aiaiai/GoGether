# 11 月 19 日会议演示步骤

使用已安装的 Android Studio，打开 D:\Projects\GoGether，分支 feature/coordination。
点击顶部 app 和 ▶，选择 Medium_Phone。运行会自动把 GoGether 安装到虚拟手机，不需要额外安装开发软件。

演示前：旧活动日期可能已过期。用 Alice 创建一场日期在未来的新活动（建议 11 月 20 日），容量至少 3，报名截止也在未来。切换 Bob 报名，再切回 Alice。

1. Alice → My Events → Created，近期活动从最近开始排列；History 可查看已结束或取消的活动。
2. 打开刚创建的活动 → Manage event → Edit meeting information。使用 Date / Time 选择器，填写集合地点、说明。可先输入一个字符的地点，展示字段错误，再改正保存；成功后编辑区自动收起。
3. 发布公告，展示字数提示。提交时按钮禁用，成功后输入框清空；失败会保留输入。
4. Profile 切换 Bob → My Events → 新活动，查看公告和新集合信息。
5. 回到 My Events → Notifications，展示未读数量、All / Unread 筛选；点击 View event 打开活动；Mark all as read 只处理 Bob 的通知。
6. 关闭 App 再打开，确认集合信息、公告和已读状态保留。可切换 Alice 检查自己的通知不会被 Bob 一起标为已读。

空列表有 Explore / Create 入口。不存在的活动显示 Event not found，并可点击 Back to Explore。

目前使用本机持久化 Demo。这套操作用于展示本地功能；共用 Firebase、真实账户及两台 Android 手机联调仍待团队配置，不应把切换 Demo 用户称为跨设备同步。
