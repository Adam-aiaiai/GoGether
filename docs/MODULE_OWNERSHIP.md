# 五人模块分工

所有成员都必须参与功能编码、测试和代码审查。以下为模块边界建议；成员姓名由团队补充。

| 成员 | 当前接续入口 | 后续代码任务 | 必须覆盖的验证 |
| --- | --- | --- | --- |
| A | `feature/auth`、`feature/profile`、`navigation/GogetherApp.kt` | Firebase Authentication、资料编辑、真实会话恢复、导航状态和可访问性 | 注册 / 登录错误、登出、用户切换、返回栈、不同字号 |
| B | `feature/explore`、`feature/event` | 活动编辑、日期选择器、完整时间 / 地点筛选、列表性能、规则推荐 | 字段与日期校验、创建后更新、编辑权限、空列表与失效链接 |
| C | `feature/registration/RegistrationEngine.kt`、`core/data` | 可信报名 API、唯一报名约束、FIFO 队列、取消与补位事务、幂等重试 | 并发最后一个名额、截止边界、重复请求、取消 / 补位竞态 |
| D | `feature/coordination`、Profile 的通知区、`core/model/Models.kt` | Firestore 流同步、我的活动、组织者公告、通知持久化与集合提醒 | 两设备状态一致、收件人权限、断网恢复、阅读状态 |
| E | `feature/checkin`、`CheckIn` / `Feedback` 模型 | 签到时间窗口、相机与二维码、服务器凭据验证、反馈和出席汇总 | 无效码、重复码、过期码、非报名用户、签到关闭、重复反馈 |

## 共享文件约定

- `core/model`、`GogetherRepository.kt`、`GogetherApplication.kt`、`GogetherApp.kt` 和 `libs.versions.toml` 属于共享接口。修改前在团队中明确影响及调用方迁移方式。
- A 负责协调导航入口，C 负责协调 Repository 契约，B 协调公共 UI 样式；其他成员仍应亲自实现各自模块代码。
- 一项功能一个小分支；建议 `feature/auth`、`feature/events`、`feature/registration`、`feature/coordination`、`feature/checkin`。不要同时直接改 `main`。
- 每个 PR 写明用户可见变化、验证命令、未覆盖场景。提交前跑本模块测试及一次应用构建；涉及共享状态时运行完整核心测试。
- 成员实际贡献通过提交、PR、测试记录与团队协商确认。此骨架不作为五人的个人贡献分配证明。

## 第一次接手

每位成员先运行一次本地 Demo，复现 README 中的候补场景；阅读 [ARCHITECTURE](ARCHITECTURE.md) 并检查自己模块对应的测试。C 与 D 联合落实跨记录事务和实时同步，A 与 E 联合落实可信身份与签到验证。
