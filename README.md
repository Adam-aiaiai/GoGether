# Gogether

Find Your People. Make It Happen.

面向小型线下兴趣活动的 Android 应用。正式应用名为 **Gogether**；GitHub 仓库和克隆目录保持 **GoGether**。

## 共享后端（2026-11-24）

已加入 A/B/C/D/E 的 Firebase 后端与 Android 接入，可在本机使用 Auth、Firestore、Functions、Storage 模拟器联调。默认构建仍为 Demo；共享后端版运行 `firebase/start-local.ps1` 后，用 `build-local-app.ps1 -Install` 构建安装。活动问答和活动变更通知已包含。

团队尚未创建正式 Firebase 云项目，本次没有云端部署。配置、启动、模式切换及限制见 [后端接入指南](docs/BACKEND_SETUP.md)，实测范围见 [验证记录](docs/BACKEND_VERIFICATION.md)。以下为历史整合记录，描述对应当时状态。
## A / B / C / D / E 本地整合（2026-11-20）

当前本地分支 `integration/member-a-b-c-d-e` 合并 ABD 的 `1454159` 与 CE 的 `a6dbab4`，统一账号、活动、报名、公告及 E 签到/完整评价入口。保留全部来源历史；本轮不推送 GitHub。字段衔接、存档迁移和验证边界见 [ABCDE 集成说明](docs/INTEGRATION_A_B_C_D_E.md)。

应用仍使用本机模拟数据。真实账号与跨设备后端尚未配置；D 的活动问答也尚未包含在来源分支中。合并不代表这些功能已经完成。

原有 [ABD 集成记录](docs/INTEGRATION_A_B_D.md) 和 [CE 集成记录](docs/MEMBER_C_E_INTEGRATION.md) 保留作历史背景，以下早期记录中的分支及验收结果对应其记录日期。

## A / B 整合基线（2026-11-19）

`integration/member-a-b` 继承 A 的 `3716d94` 与 B 的 `740de15`，统一使用现有账号、活动数据、导航和主题。A 的分支历史已经包含 B 的上述提交；本次在独立整合分支核对功能保留，并新增跨模块界面与本地保存验证。来源关系、接续方式和验收结果见 [INTEGRATION_A_B](docs/INTEGRATION_A_B.md)。全组功能完成串联与联合验收后再合入 `main`。

### A 模块接续内容（2026-11-18）

本分支基于 B 的 `740de15`，保留活动编辑、新手信息、筛选和导航。新增资料编辑、个人活动四类视图、参与记录、测试身份会话恢复及本地数据保存。真实邮箱登录/注册界面和 `AccountBackend` 接入契约已准备，但团队尚无 Firebase 项目，当前应用继续使用明确标识的 Demo 身份。

运行版本会把演示资料、活动及报名记录保存在设备内部文件中；未主动退出时，再次启动恢复上次选择的测试身份。退出登录后保留资料与活动，下次启动回到登录页。每台设备的数据仍相互独立。账号后端适配器和真实跨设备服务尚未实现，不能把本地保存或测试替身视为真实认证。

A 的入口、接口和验证见 [ACCOUNT_HANDOFF](docs/ACCOUNT_HANDOFF.md)；最新职责见 [MODULE_OWNERSHIP](docs/MODULE_OWNERSHIP.md)。

## 快速开始

1. 在 Android Studio 中打开仓库根目录，等待 Gradle Sync。
2. 安装 Android SDK Platform 37 和 Build Tools 36.0.0；选择 Android Studio 自带 JDK 作为 Gradle JDK。
3. 创建 API 26 或更高的模拟器，或连接 Android 手机。
4. 选择 `app` 并运行。无需 Firebase 配置、API key 或账户密码。
5. 在 Demo login 中选择测试用户。Alice 是羽毛球组织者，Bob 已正式报名，Emma 是首位候补；Finn 尚未报名。

命令行（macOS / Linux）：

```bash
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
./gradlew :app:connectedDebugAndroidTest  # 需先启动模拟器或连接设备
```

Windows 使用 `gradlew.bat`。Android Studio 会生成本机 `local.properties`；命令行也可通过 `ANDROID_HOME` 指向已安装 SDK。

若本地终端使用了其他 JDK，macOS 可设置：

```bash
export JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home'
```

若 Maven Central 返回 HTTP 403，可在上述命令中加入已验证的 Google 托管镜像参数：

```bash
./gradlew :app:assembleDebug :app:testDebugUnitTest \
  -PmavenCentralMirror=https://maven-central.storage-download.googleapis.com/maven2
```

默认构建仍使用 Google Maven 与 Maven Central。镜像参数也作用于插件依赖解析，无需改动版本或关闭 TLS 校验。

调试安装包生成在 `app/build/outputs/apk/debug/app-debug.apk`。

## 技术栈与环境

| 项目 | 版本 / 选择 |
| --- | --- |
| Language | Kotlin 2.2.10（AGP 内置 Kotlin） |
| UI | Jetpack Compose BOM 2025.08.01、Material 3 |
| Architecture | MVVM、Repository interface、本地 Demo / Firebase 后端 |
| State / Async | StateFlow、Coroutines 1.10.2 |
| Navigation | Navigation Compose 2.9.3 |
| Build | AGP 9.4.0、Gradle Wrapper 9.6.0、Kotlin DSL |
| SDK | min 26、compile / target 37 |
| Java | Java / Kotlin bytecode 17；本机使用 Android Studio bundled JDK 25 |
| Tests | JUnit 4、Coroutines Test、Compose UI Test |

初始化前远程仅有 README 和提交 `86e11ec`，没有可继承的 Android 包名或 SDK 配置。经用户确认新增工程；包名、namespace 和 applicationId 均为 `com.gogether.app`。AGP / Gradle 选用本机已安装组合；最低 API 26 支持原生 `java.time`，无需额外 desugaring。后续升级由团队统一评审。

## 页面与 Demo 操作

- **Login / Register**：选择六个虚构测试用户，或创建本地 Demo 昵称与兴趣。
- **Explore**：标题 / 描述搜索、类别、城市 / 地点、未来七天筛选；包含羽毛球、桌游、摄影散步、读书会、徒步。
- **Event Detail**：人数、截止时间、个人报名状态、候补排名、集合地点及操作。
- **Create**：字段与严格日期校验；发布后进入详情，并立即出现在共享活动列表。
- **My Events**：Joined / Waitlisted / Created / History 四个 Tab；历史显示自己的签到及评分。
- **Event Management**：仅组织者可管理；活动编辑/复用、参与名单、候补队列、取消、集合信息编辑、公告历史和 E 出席入口。
- **Check-in**：进入当前活动的 E 扫码/出席页面；组织者确认固定集合点，双方均需在 200 m 内。开始前后各 30 分钟自动开放，两个端点均包含。集合地点或活动时间更新后重新绑定并获取新码。
- **Feedback**：结束后的正式参与者（包括组织者）每场一次；不要求先签到。统一使用 E 的评分、评论、分项、标签、图片、匿名、可见范围、草稿和预览；保留旧评价只读兼容。
- **Profile**：编辑昵称与兴趣、个人活动概览、参与历史、切换测试用户、退出确认、通知阅读状态。

候补演示：以 Bob 登录 → 打开已满的羽毛球 → 取消报名并确认 → Profile 切换 Emma → My Events 的 Joined 中出现羽毛球 → Profile Inbox 出现转正通知。

签到联调：Alice → 羽毛球详情 → Check-in & meeting，先在约定地点确认本场集合点；切换 Bob 或已转正的 Emma，从同一活动入口查看实时资格和扫码入口。正常活动在开始前 30 分钟至开始后 30 分钟内自动允许签到，无需组织者开启。自动化界面测试使用窗口内夹具；真实相机/定位和跨手机共享应分别验证，详见 ABCDE 集成说明。

## 工程结构

```text
app/src/main/java/com/gogether/app/
  core/model/          # 数据模型与只读快照
  core/data/           # Repository 接口、Fake、种子数据
  core/ui/             # 公共组件与 ViewModel 基础类
  navigation/          # 四个主入口和三个子页面
  feature/auth/        # Demo 登录与注册
  feature/explore/     # 搜索与筛选
  feature/event/       # 创建、详情、输入解析
  feature/registration/# 独立报名引擎和操作 ViewModel
  feature/coordination/# 我的活动、组织管理、公告
  feature/checkin/     # Demo 签到、反馈
  feature/profile/     # 个人信息、历史、通知
  ui/theme/            # 紫色与橙色 Material 3 主题
app/src/test/          # Android UI 无关的业务测试
app/src/androidTest/   # 实际 Compose 页面流程测试
docs/                 # 架构、分工、计划与验证说明
```

## Demo Mode 边界

本集成分支通过 LocalSnapshotStore 保存主活动、用户资料及 C 报名数据，重启后保留，但需重新选择 Demo 用户。E 签到、反馈、草稿与图片沿用本机私有存储；普通活动经 RegistrationAttendanceBridge 读取同一份实时资格，签到投影至管理页。每台设备仍拥有独立数据。测试用户切换不是安全认证，不接受真实邮箱或密码。

报名、取消、补位和通知以一次完整快照保存和发布；Mutex / LocalDataLock 只协调单进程操作，不能保证多设备并发正确。普通活动详情已进入 E 签到，组织者先确认本场固定集合点；相机、动态二维码、前台定位与 200 米检查已有实现，但账号、活动与报名仍是本机 Demo 数据，码没有服务端签发验证，也没有跨设备同步。扫码时请求相机和精确位置权限，出码时请求精确位置权限；应用当前没有联网权限。真实认证、共享后端、可信事务及服务端权限校验仍待接入，后端技术与负责人需小组统一确认。旧 Profile 独立演示与本次集成的区别见 [集成说明](docs/MEMBER_C_E_INTEGRATION.md)。

规则简化：容量包含组织者；组织者若退出须取消活动；截止时刻起停止报名和补位，截止后但开始前仍可取消；活动开始后禁止取消；每场活动暂定两小时。签到默认启用但严格受时间窗口约束，取消、标记完成或到达结束时间后停止签到；本次不改变取消权限。失败的请求返回错误，不产生 `REJECTED` 持久记录；该状态保留供后续审批逻辑使用。
运行版本通过版本化的本地文件保存完整 Demo 快照；旋转屏幕、结束进程和重新打开可保留资料及活动记录。测试中不注入存储时仍使用纯内存 Fake。每台设备拥有独立数据。测试用户切换不是安全认证，不接受真实邮箱或密码。应用无需联网权限，也不采集位置。

报名、取消、补位和通知以一次完整快照发布；Mutex 只协调单进程操作，不能保证多设备并发正确。签到明确为本地模拟，未实现相机、二维码、身份 / 位置证明。真实 Authentication、Firestore 同步、可信后端事务、安全规则及后台通知属于后续阶段；A 已提供独立账号服务契约与可接入的邮箱表单。

规则简化：容量包含组织者；组织者若退出须取消活动；截止时刻起停止报名和补位，截止后但开始前仍可取消；活动开始后禁止编辑或取消；活动由组织者填写开始和结束时间，最长 24 小时；为方便排练可提前开启 Demo 签到。失败的请求返回错误，不产生 `REJECTED` 持久记录；该状态保留供后续审批逻辑使用。

## 五人协作与后端接入

每位成员负责功能代码、对应测试和说明；分工详见 [MODULE_OWNERSHIP](docs/MODULE_OWNERSHIP.md)。各自使用 feature branch，通过小 PR 合并。`core/model`、Repository 契约、版本目录与导航属于共享接口，先协商再改动。

后端技术与负责人由小组确认，Firebase 是早期候选方案。正式认证身份必须来自真实认证服务；报名 / 补位 / 签到由可信后端和数据库事务校验。两设备同步、断网恢复和权限测试通过后，才可作为正式多人流程验收。

- [架构与状态规则](docs/ARCHITECTURE.md)
- [开发计划](docs/DEVELOPMENT_PLAN.md)
- [模块负责人及接续任务](docs/MODULE_OWNERSHIP.md)
- [测试与排查](docs/TESTING.md)
- [Proposal 对照与本阶段取舍](docs/REQUIREMENTS.md)
- [本次实现与验证记录](docs/IMPLEMENTATION_REPORT.md)
- [E：双手机扫码＋200 米定位原型试用](docs/CHECKIN_PROTOTYPE.md)（Profile 入口；使用独立模拟活动）
