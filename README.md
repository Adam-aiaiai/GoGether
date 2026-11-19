# Gogether

Find Your People. Make It Happen.

> **C/E 集成分支（2026-11-19）**：`integration/member-c-e-attendance` 合并 E 的 `feat/member-e-attendance` 与 C 的 `feature/registration-avatar-group`。普通活动详情和管理页已接入 E 扫码/出席，资格读取 C 实时报名；主活动与报名支持本机保存。具体调用链、操作步骤和仍待后端接入的边界以 [C/E 集成说明](docs/MEMBER_C_E_INTEGRATION.md) 为准。下面的第一版记录保留作历史背景。

面向小型线下兴趣活动的 Android 应用。第一阶段提供可独立运行的 Demo：发现活动、创建活动、报名及候补、集合信息、签到和个人记录。正式应用名为 **Gogether**；GitHub 仓库和克隆目录保持 **GoGether**。

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
| Architecture | MVVM、Repository interface、In-memory Fake Repository |
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
- **My Events**：Joined / Waitlisted / Created 三个 Tab。
- **Event Management**：仅组织者可管理；参与名单、候补队列、取消活动、自动签到时段、本地公告。
- **Check-in**：进入本活动的 E 扫码/出席页面；组织者首次确认固定集合点，正式参与者在开始前后各 30 分钟内扫码并定位，两个端点均包含。已绑定活动沿用完整 E 评价；未绑定的旧历史活动保留原评分及评论。
- **Profile**：昵称、兴趣、活动历史、切换用户、退出、通知阅读状态。

候补演示：以 Bob 登录 → 打开已满的羽毛球 → 取消报名并确认 → Profile 切换 Emma → My Events 的 Joined 中出现羽毛球 → Profile Inbox 出现转正通知。

签到联调：Alice → 羽毛球详情 → Check-in & meeting，先在约定地点确认本场集合点；切换 Bob 或已转正的 Emma，从同一活动入口查看实时资格和扫码入口。正常活动在开始前 30 分钟至开始后 30 分钟内自动允许签到，无需组织者开启。自动化界面测试使用窗口内夹具；真实相机/定位和跨手机共享应分别验证，详见 C/E 集成说明。

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
