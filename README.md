# Gogether

Find Your People. Make It Happen.

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
- **Event Management**：仅组织者可管理；参与名单、候补队列、取消活动、Demo 签到开关、本地公告。
- **Check-in**：正式参与者进行一次 Demo 签到；历史活动可提交评分及评论。
- **Profile**：昵称、兴趣、活动历史、切换用户、退出、通知阅读状态。

候补演示：以 Bob 登录 → 打开已满的羽毛球 → 取消报名并确认 → Profile 切换 Emma → My Events 的 Joined 中出现羽毛球 → Profile Inbox 出现转正通知。

签到演示：以 Alice 登录 → 羽毛球 → Manage event → Open Demo check-in → Profile 切换 Bob → 羽毛球详情 → Check-in & meeting → Demo Check-in。若先完成候补演示，可改用已转正的 Emma。

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

所有变更只保存在当前应用进程内；旋转屏幕保留 Repository，结束进程后恢复种子数据。每台设备拥有独立数据。测试用户切换不是安全认证，不接受真实邮箱或密码。应用无需联网权限，也不采集位置。

报名、取消、补位和通知以一次完整快照发布；Mutex 只协调单进程操作，不能保证多设备并发正确。签到明确为本地模拟，未实现相机、二维码、身份 / 位置证明。真实 Authentication、Firestore 持久化与同步、可信后端事务、安全规则及后台通知属于后续阶段。

规则简化：容量包含组织者；组织者若退出须取消活动；截止时刻起停止报名和补位，截止后但开始前仍可取消；活动开始后禁止编辑或取消；活动由组织者填写开始和结束时间，最长 24 小时；为方便排练可提前开启 Demo 签到。失败的请求返回错误，不产生 `REJECTED` 持久记录；该状态保留供后续审批逻辑使用。

## 五人协作与后端接入

每位成员负责功能代码、对应测试和说明；分工详见 [MODULE_OWNERSHIP](docs/MODULE_OWNERSHIP.md)。各自使用 feature branch，通过小 PR 合并。`core/model`、Repository 契约、版本目录与导航属于共享接口，先协商再改动。

后续以 `FirebaseGogetherRepository` 实现现有契约，在 Application 中替换注入。认证身份必须来自 Firebase；报名 / 补位 / 签到由可信后端和数据库事务校验。两设备同步、断网恢复和安全规则测试通过后，才可移除 Demo 标识。

- [架构与状态规则](docs/ARCHITECTURE.md)
- [开发计划](docs/DEVELOPMENT_PLAN.md)
- [模块负责人及接续任务](docs/MODULE_OWNERSHIP.md)
- [测试与排查](docs/TESTING.md)
- [Proposal 对照与本阶段取舍](docs/REQUIREMENTS.md)
- [本次实现与验证记录](docs/IMPLEMENTATION_REPORT.md)
