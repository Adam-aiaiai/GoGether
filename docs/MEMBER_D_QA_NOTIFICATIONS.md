# D：活动问答与活动变更通知

2026-11-20，在 `integration/member-a-b-c-d-e` 的 `3fbe67e` 上补充。本次由用户指定先完成本地流程，暂不接后端。本文是实现与验收记录，不是课程提交报告。

## 活动问答

- 活动详情 → **Questions & answers**；组织者也可从 **Manage → Questions & answers** 进入，并查看待回复数量。
- 已登录的非组织者可在报名之前提问；问题与组织者回复对查看该活动的人公开。
- 问题限制 1–500 字符，回复限制 1–1,000 字符，去除首尾空格；空内容、重复问题、无效活动或无权限回复被拒绝。
- 只有该活动的组织者可以回复或修改官方回复。保存相同回复不会再次发通知。
- 提问通知组织者；回复和修改回复通知提问人。My Events 的通知页及 Profile 通知均可直接进入对应问题；支持独立标记已读。
- 发送期间禁用重复提交，失败保留输入；取消或结束的活动保留历史问答，但停止新增提问和回复。
- 问题、回复、作者、时间、通知引用及已读状态一并保存到共享快照。写入失败时数据与通知一起回滚。

## 活动变更通知

- 普通 **Edit event → Save changes** 覆盖开始/结束时间、活动地点/城市、报名截止、标题、容量、类别、说明、备注、参与条件/装备和封面变更。
- 时间和地点等通知包含修改前后内容；同一次保存多处变更合成一条变更通知，每位有效报名/候补用户收到一次，沿用原逻辑保留组织者的通知。
- 退出报名、未报名者不接收活动变更通知。容量增加触发的候补转正仍单独发转正通知。
- 未改动或仅首尾空格变化不发送通知。独立集合编辑的原通知保留；普通编辑同时调整显式集合时间时合并为一次提示。
- Inbox 的 **View event** 打开最新详情。只更新普通活动时间/地点而未单独设置集合信息的情况已覆盖。

## 存档与兼容

共享 JSON 格式升级为 version 3，新增 questions 和 questionId。原 version 1/2 的整合前 AB/ABD 与 CE 读入路径保留，无问答字段时默认为空；CE 再次保存不会丢掉新字段。现有正式整合存档会在下次成功保存时使用 version 3。

审查还发现一个独立的旧版本升级缺口：原 D 分支的 `coordination-v1.json`（Gson 的 version/snapshot 包装格式）没有被 ABCDE 的启动迁移读取。直接从那一版升级，旧公告等仍在原文件内，但新应用不会显示它们。该问题已用临时用例复现，本次只实现用户指定的问答和变更通知，未加入额外存档合并策略。请勿删除旧文件；也不要把新字段文件交给不支持 version 3 的旧 APK 覆盖保存。

## 本轮实际验证

- 修改前：189 个 JVM 单元测试和 33 个已有设备用例通过；另两个临时审查用例分别复现旧 D 存档遗漏、默认集合时间随活动编辑变化但不发通知。
- 修改后：202/202 个 JVM 单元测试通过；40/40 个设备测试通过，含新增 5 个界面流程、2 个存档兼容测试及原 33 个回归。
- Debug APK 与 Android 测试 APK 构建成功。Firebase Functions 的现有参数校验 3/3 通过，但不表示后端已部署。
- Lint 未完成：在线下载停滞，离线检查确认缺少 intellij-core、kotlin-compiler、uast、groovy、play-sdk-proto 等检查器依赖。本次不声称 Lint 通过。
- 设备验证使用临时 `-read-only -no-snapshot-save` 模拟器，不覆盖用户原 AVD 存档。
- 证据在 `verification/d-review-20261120/`：device-baseline.txt、device-features.txt、feature-validation.txt、lint.txt、results-summary.json，以及问答/变更通知截图。verification 被 Git 忽略。

## 仍需区分的边界

本地功能可用不等于两台手机共享。当前应用仍注入 FakeGogetherRepository，真实身份、Firebase 同步、双设备离线重连本次未接入。第三个红框的报名时间冲突提示不属于本次两项新增内容。

整合分支使用 AGP 9.4.0，本机 Android Studio 2026.1.2 支持上限为 AGP 9.3，原有 IDE 同步兼容问题仍需统一团队工具版本；本次命令行构建可成功。参见 [Android 官方兼容表](https://developer.android.com/studio/releases)。

## 本地演示步骤

1. Finn 打开一个未结束的活动 → Questions & answers → 提问。
2. 切换 Alice（该活动组织者）→ My Events → Notifications → View question → Reply → Save answer。
3. 切回 Finn → Notifications → 查看回答通知 → View question → 查看答复；可标记已读。
4. Alice → 活动 Manage → Edit event → 改时间或地点 → 保存。
5. Bob → Notifications → 查看含前后信息的变更通知 → View event → 核对最新活动信息。
6. 重启应用验证问答、活动更新和已读状态仍保留。请用 Demo 用户切换展示本地流程，不能描述成跨手机实时同步。
