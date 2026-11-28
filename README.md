# Gogether

Find Your People. Make It Happen.

面向小型线下兴趣活动的 Android 应用。正式应用名为 **Gogether**；GitHub 仓库和克隆目录保持 **GoGether**。

## 当前五阶段验收分支（2026-11-28）

`feat/event-attendance-social-ratings` 基于 `feat/member-ui-polish` 的 `10d07ac`，包含报名/签到资格与定位移除、多轮互动/黑名单/维护者举报处理、互动通知与 My Events 红点、综合评分与 History、旧数据兼容及回归修复。

- 组员获取代码、配置 Demo、一次准备验收活动：[组员测试指南](docs/TEAM_TESTING_GUIDE.md)。
- 切换账号、逐步操作和预期结果：[五阶段手动验收清单（41 项）](docs/MANUAL_ACCEPTANCE_PHASES_1_TO_5.md)。
- 规则与实现：[第一阶段](docs/PHASE1_ATTENDANCE.md)、[第二阶段](docs/PHASE2_SOCIAL.md)、[第三阶段](docs/PHASE3_NOTIFICATIONS.md)、[第四阶段](docs/PHASE4_RATINGS.md)、[第五阶段](docs/PHASE5_COMPATIBILITY.md)。

默认运行 Demo。测试数据保存在各自设备，使用指南中的显式准备入口可创建同样的活动与账号场景；生产 Firebase 部署另行进行。下方带旧日期的整合记录描述对应历史版本。

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
# 完整设备回归使用独立测试 AVD；手动验收数据准备见上方组员测试指南。
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
- **My Events**：Joined / Waitlisted / Created / History 四个 Tab；历史显示签到、个人评分及公开参与者综合评分；Notifications 区分 Interactions / Activity updates，底部图标显示未读红点。
- **Event Management**：仅组织者可管理；活动编辑/复用、参与名单、候补队列、取消、集合信息编辑、公告历史和 E 出席入口。
- **Check-in**：进入当前活动的 E 扫码/出席页面；组织者确认到场后显示动态二维码，参与者凭当前正式报名扫码。开始前后各 30 分钟开放，活动结束即关闭。移除定位与地图功能，保留文字集合信息。
- **Feedback**：结束后，当前正式报名且有效签到的用户每场一次；组织者先确认到场且实名评价。统一使用 E 的评分、评论、分项、标签、图片、匿名、可见范围、草稿和预览；保留旧评价只读兼容。
- **Profile**：编辑昵称与兴趣、个人活动概览、参与历史、切换测试用户、退出确认、通知阅读状态。

候补演示：在活动截止和开始前，以 Bob 登录 → 打开已满的羽毛球 → 取消报名并确认 → Profile 切换 Emma → My Events 的 Joined 中出现羽毛球 → Notifications 的 Activity updates 出现转正通知。

签到联调：创建开始时间约为当前时间后 10 分钟的活动，组织者进入签到页确认到场并显示二维码；正式参与者扫码签到。取消再报名必须重新签到。完整报名边界、旧存档处理和本机测试方式见 [第一阶段规则](docs/PHASE1_ATTENDANCE.md)。

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

本集成分支通过共享快照保存主活动、用户资料及 C 报名数据，未退出登录时重启恢复上次选择的 Demo 用户。E 签到、反馈、草稿与图片沿用本机私有存储；普通活动经 RegistrationAttendanceBridge 读取同一份实时资格，签到投影至管理页。每台设备仍拥有独立数据。测试用户切换不是安全认证，不接受真实邮箱或密码。

报名、取消、补位和通知以一次完整快照保存和发布；Mutex / LocalDataLock 只协调单进程操作。Demo 的二维码与签到记录保存在本机，跨设备同步使用 Firebase 模式；Firebase 的身份、报名、签到与重试由服务器和事务校验，配置见 [后端说明](docs/BACKEND_SETUP.md)。本次验证不部署线上服务。

当前报名、签到、评价资格以及旧数据兼容规则见 [第一阶段规则](docs/PHASE1_ATTENDANCE.md)。扫码仅请求相机权限；不采集设备位置。容量包含组织者，截止后但开始前可取消但不补位；只有有效签到的用户才可在结束后评价。

## 五人协作与后端接入

每位成员负责功能代码、对应测试和说明；分工详见 [MODULE_OWNERSHIP](docs/MODULE_OWNERSHIP.md)。各自使用 feature branch，通过小 PR 合并。`core/model`、Repository 契约、版本目录与导航属于共享接口，先协商再改动。

项目已提供 Demo 与 Firebase 两种后端模式。正式认证身份必须来自真实认证服务；报名 / 补位 / 签到由可信后端和数据库事务校验。两设备同步、断网恢复和权限测试通过后，才可作为正式多人流程验收。

- [架构与状态规则](docs/ARCHITECTURE.md)
- [开发计划](docs/DEVELOPMENT_PLAN.md)
- [模块负责人及接续任务](docs/MODULE_OWNERSHIP.md)
- [测试与排查](docs/TESTING.md)
- [Proposal 对照与本阶段取舍](docs/REQUIREMENTS.md)
- [本次实现与验证记录](docs/IMPLEMENTATION_REPORT.md)
- [E：早期签到原型记录（历史）](docs/CHECKIN_PROTOTYPE.md)（Profile 入口；使用独立模拟活动）

第五阶段的旧数据升级、兼容性修复和回归范围见 [兼容与回归说明](docs/PHASE5_COMPATIBILITY.md)。
