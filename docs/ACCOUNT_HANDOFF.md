# A 模块接续说明

日期：2026-11-18。分支 `feat/member-a-account` 基于 B 的 `740de15`，并包含主线分工更新 `47f0db0`。

## 本阶段交付

- 本地测试身份选择/注册、资料编辑、退出确认、会话恢复。
- 昵称校验和兴趣编辑，用户 ID 不变，活动组织者/报名引用保持有效。
- Joined/Waitlisted/Created/History 分类；本人签到、退出和评分摘要。
- 整份本地 Demo 快照保存，包含 B 的起止时间、新手字段、图片 URI、报名、通知、签到及反馈；保存失败不发布成功。
- D 的通知区域独立为 `NotificationSection`，由 A 个人页接入。
- 邮箱登录/注册页面、输入校验、账号后端接口、恢复与错误处理，并以测试适配器验证。

团队确认尚无 Firebase 项目。本次没有创建云项目、收集真实密码或实现 Firebase 适配器。默认运行的是可持久化的 Demo，不是密码认证。正式账号与活动的两设备同步仍待接入。

## 接续入口

| 内容 | 文件 |
| --- | --- |
| 账号后端契约与校验 | `core/account/AccountBackend.kt` |
| 资料状态处理 | `core/account/AccountState.kt` |
| 账号页面与动作 | `feature/auth` |
| 资料编辑、个人记录查询 | `feature/profile` |
| 我的活动页面 | `feature/coordination/MyEventsScreen.kt` |
| 通知组件（后续归 D） | `feature/coordination/NotificationSection.kt` |
| 本地保存 | `core/data/SnapshotStore.kt`、`SnapshotJson.kt`、`FileSnapshotStore.kt` |
| 应用注入与公共调用 | `GogetherApplication.kt`、`GogetherRepository.kt` |

## 账号服务接入契约

`AccountBackend` 提供 `restoreSession`、`signIn`、`signUp`、`updateProfile`、`signOut`。成功返回经过服务端确认的完整 `User`，包括稳定 ID、昵称、兴趣、创建时间、`UserStatus.ACCOUNT` 及可选邮箱。后端拥有密码验证、令牌保存/刷新、资料同步和退出的职责；应用不保存或日志输出密码。

接入步骤：
1. 团队创建并共享同一个 Firebase 项目或其他后端，确定认证方式、资料字段和权限。
2. 实现 AccountBackend（例如使用 Firebase Authentication 管理会话、数据库保存昵称/兴趣）。失败抛出适合显示的错误；协程取消继续传播。用户 ID 不能来自客户端选择，也不能复用 Demo 身份 ID。
3. 在 Application 注入适配器并添加所需 SDK、网络权限和本地配置，配置不进入 Git。接入后运行模式变为 CONNECTED，邮箱表单开启，测试用户切换关闭。
4. 账号适配器只用于验证账号接入。当前 Fake 的活动仍然本地，需由各模块继续实现真实数据操作/监听，并统一接入 Repository；不要把身份接通当成全组后端完成。
5. 后端适配器需处理令牌失效并令应用退出失效会话；真实远程监听在登出/换用户时取消。配置后验证注册、登录、恢复、资料跨设备、离线错误及权限。

本地 SnapshotStore 仅用于 Demo。与账号适配器组合时不能注入 Demo 存储；账号服务拥有可信会话持久化。后续完整远程 Repository 可自行实现缓存策略。

Firebase 参考：[邮箱认证](https://firebase.google.com/docs/auth/android/password-auth)、[认证入门](https://firebase.google.com/docs/auth/android/start)。

## 兼容 B 与其他成员

保留 B 的活动字段、updateEvent、筛选、编辑/复用路由及 UI。A 只为 User 增加带默认值的邮箱字段与 ACCOUNT 类型，为快照增加不落盘的会话恢复状态；个人列表使用 B 的 statusAt。没有把活动模型另存为 A 私有副本。

注册/切换/退出及资料编辑统一通过 Repository。C/E 获取当前身份仍使用 `state.currentUserId`，报名人数仍从同一记录计算。D 可替换 NotificationSection 内部实现，保留组件参数即可。共享修改集中在 A 分支，不覆盖 B 分支或自动合并 B 到主线。

## 当前限制

- 应用仍为单设备活动演示；真实登录服务与跨设备同步尚未配置和验证。
- 保存的种子活动使用首次启动日期，之后会自然进入历史；可以通过发布新活动继续开发。
- 已删除或失去授权的本地图片可能不可读；保留 B 的无图回退。
- 邮箱流程当前由测试服务验证，尚无可供真实用户使用的邮箱认证。

## 本次验证

2026-11-18，macOS、Android Studio JDK、Android 17 模拟器。清理构建输出后完整执行构建、JVM 测试、设备测试和 Lint。

| 检查 | 结果 |
| --- | --- |
| Debug APK | 构建成功 |
| JVM 业务测试 | 57 项通过，0 失败/错误/跳过 |
| Android 设备测试 | 18 项通过，0 失败/错误/跳过；包括 B 的 10 条现有流程 |
| Lint | 0 错误，10 条现有依赖版本/KTX 建议 |
| 界面复核 | 资料编辑、个人页、历史列表及邮箱表单截图已检查 |

测试覆盖本地文件重建后恢复、资料更新及退出保存；账号适配器通过测试服务验证。测试入口、报告路径和运行命令见 [TESTING](TESTING.md)。
