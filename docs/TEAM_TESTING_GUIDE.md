# 组员拉取、运行和准备验收数据

适用分支：`feat/event-attendance-social-ratings`。本分支包含五阶段的报名/签到、社交互动与举报、通知红点、综合评分及旧数据兼容更新。

建议先用 **Demo 模式 + 单独的验收模拟器**，准备数据后按 [41 项手动验收清单](MANUAL_ACCEPTANCE_PHASES_1_TO_5.md) 检查。Git 同步代码、测试和说明；每台设备的报名、签到、草稿、通知和测试活动各自保存在本机。

## 1. 获取新分支

推荐克隆到新目录，保留自己原来的开发目录：

```bash
git clone --branch feat/event-attendance-social-ratings --single-branch https://github.com/Adam-aiaiai/GoGether.git GoGether-acceptance
cd GoGether-acceptance
git branch --show-current
```

已有仓库且 `git status` 显示工作区干净时，也可以：

```bash
git fetch origin
git switch --track origin/feat/event-attendance-social-ratings
```

如果本地已存在此分支，改用 `git switch feat/event-attendance-social-ratings`，再执行 `git pull --ff-only`。有未提交工作时先自行保存或使用上面的新目录方案；不要为了切分支强制覆盖本地改动。

## 2. 配置并运行 Demo

1. Android Studio 打开克隆目录，等待 Gradle Sync。
2. SDK Manager 安装 **Android SDK Platform 37**、**Build Tools 36.0.0** 和 Platform-Tools；Device Manager 新建一台专用于本次验收的模拟器。项目最低 API 26，本次验证使用 API 37。
3. Gradle JDK 选择 Android Studio 自带 JDK。本机验证环境为 JDK 25；Java/Kotlin 输出字节码目标仍为 17。不要为了同步任意升级仓库里的 Gradle/AGP 版本。
4. 默认是 Demo 模式，直接 Run `app` 即可。若个人 Gradle 配置曾设置 `backend=emulator`，使用下面带 **`-Pbackend=demo`** 的命令明确指定模式。
5. 初始登录页选择 Alice。后续从 **Profile → 右上角三点 → Switch to Bob / Carol / …** 切换；Demo 不需要 Firebase 配置或真实账号密码。

终端需能运行 `adb`。Android Studio 的 SDK 路径下，命令位于 `platform-tools/adb`（Windows 为 `adb.exe`）；可以加入个人 PATH，或在命令中使用完整路径。

macOS / Linux：

```bash
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest -Pbackend=demo
adb devices -l
```

Windows PowerShell：

```powershell
.\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest -Pbackend=demo
adb devices -l
```

若终端 JDK 不正确，先将个人 `JAVA_HOME` 指向 Android Studio 的 `jbr`；macOS 常用路径为 `/Applications/Android Studio.app/Contents/jbr/Contents/Home`。`local.properties` 由本机 Studio 生成，不复制别人的 SDK 路径。

Maven Central 返回 403 时，可在 Gradle 命令最后加：

```text
-PmavenCentralMirror=https://maven-central.storage-download.googleapis.com/maven2
```

若 Google Maven 的 `dl.google.com` 出现 DNS/连接错误，可加下面的可选参数，切换到 Google 的备用下载域名；不会修改依赖版本或关闭证书校验：

```text
-I gradle/google-download-fallback.init.gradle
```

首次下载仍需要可访问这些仓库的网络。只有依赖已完整缓存时才可再加 `--offline`；它不能代替首次下载。

## 3. 一次准备全部手动验收数据

本分支提供 `TeamAcceptanceDemoSetup`，只存在于测试 APK 中。它按需添加第一至第四阶段的活动，并复用已验证的评价/通知准备流程；第五阶段使用这些旧记录检查重启、账号隔离和持久化。

它必须通过 `prepareTeamDemo=true` 显式启用。正常启动 App 不会添加这些活动；正常回归测试也不应把数据准备类作为业务测试计数。

**先在 `adb devices -l` 中确认专用验收模拟器的序列号。** 以下 `emulator-5554` 是示例，替换为自己的目标；多台设备连接时必须保留 `-s`。本流程不清空应用数据，但会新增测试记录，建议始终使用单独的验收模拟器。

macOS / Linux：

```bash
TEAM_DEVICE=emulator-5554
adb -s "$TEAM_DEVICE" install -r app/build/outputs/apk/debug/app-debug.apk
adb -s "$TEAM_DEVICE" install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s "$TEAM_DEVICE" shell am force-stop com.gogether.app
adb -s "$TEAM_DEVICE" shell am instrument -w -e class com.gogether.app.TeamAcceptanceDemoSetup -e prepareTeamDemo true com.gogether.app.test/androidx.test.runner.AndroidJUnitRunner
adb -s "$TEAM_DEVICE" shell am force-stop com.gogether.app
adb -s "$TEAM_DEVICE" shell am start -n com.gogether.app/.MainActivity
```

Windows PowerShell：

```powershell
$teamDevice = 'emulator-5554'
adb -s $teamDevice install -r app/build/outputs/apk/debug/app-debug.apk
adb -s $teamDevice install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s $teamDevice shell am force-stop com.gogether.app
adb -s $teamDevice shell am instrument -w -e class com.gogether.app.TeamAcceptanceDemoSetup -e prepareTeamDemo true com.gogether.app.test/androidx.test.runner.AndroidJUnitRunner
adb -s $teamDevice shell am force-stop com.gogether.app
adb -s $teamDevice shell am start -n com.gogether.app/.MainActivity
```

逐条检查结果：两次安装应显示 `Success`；准备命令应显示 **`OK (1 test)`**，且没有 `FAILURES`、异常或跳过。遇到错误就停止并保留输出；不能只看 shell 返回码。初次准备不需要先手动登录，入口会初始化 Demo 存档，最后停留为 Alice。

### 准备后的活动

| 活动 | 数据/用途 |
| --- | --- |
| Phase 1 - Review access | 已结束；Alice 到场、Bob 签到、Carol 未签到；尚无评价，用于资格和首次评价通知 |
| Phase 1 - Host arrival | 已结束；组织者未到场，用于评价资格拒绝 |
| Phase 1 - Rejoin test | 开始为准备时刻后约 20 分钟，截止约 15 分钟；Bob 有预置签到，用于观察取消重报失效；这不是实际摄像头扫码的证明 |
| Phase 2 - Questions | Bob 问题、Alice 回答和 Bob 追问；David 候补；维护者有一条待处理举报 |
| Phase 2 - Reviews | Bob 匿名 4 星、Carol 实名 5 星、Alice 组织者评价；综合 4.5 / 2 reviews |
| Phase 2 - Private feedback | Carol 匿名私密评价，仅 Carol/Alice 可见 |
| Phase 3 - Conversations | 多轮问答及通知，开始约准备时刻后一天 |
| Phase 3 - Review notifications | Bob/Carol 公开评价、David 私密评价及回复；综合 4.0 / 2 reviews |
| Phase 4 - Public ratings | Bob 5 星、Carol 3 星；另有组织者、私密、删除记录；综合 4.0 / 2 reviews |
| Phase 4 - No public ratings | 只有组织者和私密评价；显示 — / No reviews |

账号包括 Alice、Bob、Carol、David、Emma、Finn、Project maintainer (Demo)。先到 **Alice → Profile → More** 或 **My Events → History** 检查两项 Phase 4 基准，再按验收清单切换账号。

活动时间相对**本次准备时刻**生成。验收清单中 2026-11-28 的具体时间、18 个活动/54 条报名属于原交付设备，不是组员设备的固定预期。本入口新增 10 个活动；新安装的基础 Demo 原有 6 个活动。临近截止/开始的测试仍按清单新建 Q/S/D/B/C，不能修改系统时间来延长签到窗口。

### 重复准备、过期和错误恢复

- 再运行同一入口会跳过已完成的数据组，保留已读状态、删除/重评结果、匿名昵称及其他手动修改，不把评分重置为基准。
- 若准备中断，已完整完成的组可跳过；若发现同名活动已存在但完成标记缺失，或活动被删除/改名，会报错并停止，不擅自清空旧存档。使用另一台新的验收 AVD 获取完整基准。
- 活动过期时，新建有合适时间窗口的活动继续测；重复运行入口不会刷新旧活动时间。
- 需要完全重来时，创建另一台专用 AVD 并重复以上步骤。不要对保存着本人测试结果的设备执行清数据、卸载或全套回归。

## 4. 手动验收与反馈

按 [五阶段手动验收清单](MANUAL_ACCEPTANCE_PHASES_1_TO_5.md) 中的账号、操作和预期结果执行。先检查评分基准，再做删除、举报、拉黑；每项记录“通过 / 失败 / 待测 / 待补测”。反馈至少包含分支和提交号（`git rev-parse --short HEAD`）、用例编号、账号、活动标题、操作步骤、实际现象和截图。

单台模拟器不能扫描自己的屏幕。实际扫码需要模拟器摄像头能获得另一屏幕上的最新二维码，并在 30 秒有效期内完成；条件不具备就标记待补测。预置签到可验证评价资格/历史显示，不代表实体手机扫码已通过。

## 5. 自动化检查和 Firebase 测试

不涉及设备数据的检查：

```bash
./gradlew :app:testDebugUnitTest :app:lintDebug -Pbackend=demo
npm --prefix firebase/functions ci
npm --prefix firebase/functions test
```

Windows 将 `./gradlew` 换成 `.\gradlew.bat`。后端依赖按 `firebase/functions/package.json` 使用 Node 22。

**完整 Android 回归使用另一台测试 AVD。** 一些测试会替换应用存档/清理偏好，不在刚准备完、正在人工验收的设备上运行。Demo 回归的 AndroidJUnitRunner `notClass` 参数应排除 `com.gogether.app.PhaseTwoDemoSetup`、`com.gogether.app.PhaseThreeDemoSetup`、`com.gogether.app.PhaseFourDemoSetup`、`com.gogether.app.TeamAcceptanceDemoSetup` 和 `com.gogether.app.FirebaseBackendFlowTest`。云端用例单独按 [后端指南](BACKEND_SETUP.md) 配置，不要将整包测试当作数据准备命令。

第五阶段交付基线已完成：255 项 Kotlin 单元测试、260 项 Demo 设备测试、1 项 Firebase 双客户端设备测试、47 项 Functions 单元测试、38 项后端集成测试、6 项旧协调/规则测试；lint 0 错误、60 项既有警告。上述是交付版本的回归记录，不等同于每位组员已经完成手动验收。新增的数据准备入口另行做新安装与重复执行验证。

本分支发布前（2026-11-28）另行验证：从排除本机 `gradle-daemon-jvm.properties` 的独立源码副本成功构建 Demo 与测试 APK，255 项单元测试再次通过，lint 0 错误、54 警告。因官方主下载域名 DNS 故障，本次使用上面的 Google 备用域名配置与已有依赖缓存，没有更改依赖版本；这不代表首次联网下载已在所有组员网络上验证。

全新 API 37 测试 AVD 上，未启用参数时准备入口被跳过且不创建主存档；显式准备成功生成 10 个阶段活动（含基础数据共 16 个活动、51 条报名），评分准备流程内断言通过。重复准备成功，7 个业务数据文件逐字节一致。原交付模拟器的数据未用于这次准备测试。

若要测试多设备共享，按 [BACKEND_SETUP](BACKEND_SETUP.md) 启动本机 Firebase Auth/Firestore/Functions/Storage，再以 `-Pbackend=emulator` 构建；必须使用对应的测试账号和云端数据，**不能运行本页 Demo 数据准备入口**。不同电脑的 Demo 存档不共享，上传 Firebase 源码也不会自动部署线上服务。
