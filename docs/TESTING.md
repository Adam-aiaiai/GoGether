# Testing

## 执行命令

```bash
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
./gradlew :app:connectedDebugAndroidTest
```

第二条命令需要已启动的 API 26+ 模拟器或 USB 调试设备。本机 Maven Central 若返回 HTTP 403，在命令后加入：

```text
-PmavenCentralMirror=https://maven-central.storage-download.googleapis.com/maven2
```

单独运行某类单元测试：

```bash
./gradlew :app:testDebugUnitTest --tests 'com.gogether.app.RegistrationEngineTest'
```

报告位置：

- `app/build/reports/tests/testDebugUnitTest/index.html`
- `app/build/reports/lint-results-debug.html`
- `app/build/reports/androidTests/connected/debug/index.html`

## JVM 业务测试

`RegistrationEngineTest` 使用注入时钟和虚构用户，不依赖 Android UI。覆盖活动创建和组织者名额、字段验证、正常报名、满员候补、取消、FIFO 补位及通知、重复报名、取消后重排、截止前 / 截止时刻、活动开始 / 结束 / 取消 / COMPLETED、组织者权限、签到资格和重复签到、反馈与本地身份切换。

`RepositoryTest` 验证共享状态、取消 / 补位 / 通知的原子发布、重复点击的本地串行处理、未登录拦截、严格日期解析及注入时钟的种子数据。并行点击测试仅证明当前 Fake 的单进程约束，不代表真实后端的多人并发保证。

## Compose 界面测试

`MvpFlowTest` 每个用例创建独立 Fake Repository，实际操作 Compose 页面：

1. 创建活动 → 详情 → Explore 搜索结果 → My Events / Created。
2. Bob 取消 → Emma 转正 → 切换用户 → Joined 和 Profile Inbox 更新。
3. Finn 加入满员活动 → 候补第二位 → Waitlisted → 取消并确认队列。
4. Alice 打开 Demo 签到 → Bob 签到 → 按钮禁用且仅有一条签到。
5. Demo 注册新昵称 → 创建空表单 → 显示校验错误。

这些流程覆盖八个页面的主要入口。真实地图应用选择、Android 权限、相机、断网和两设备同步不在本阶段自动化范围。

### Snackbar 与测试时钟

`MvpFlowTest.awaitSnackbar` 在测试线程查询 Snackbar 语义节点，存在时用 `mainClock.advanceTimeBy(100)` 推进虚拟时间，直到节点消失，再调用 `waitForIdle()` 完成 Android 绘制与布局，随后继续真实触摸操作。15 秒是虚拟测试时间上限；仍会在 Snackbar 一直不消失时失败。当前 Compose 1.9 的 `advanceTimeUntil` 会在 UI 线程执行条件，不能在其中调用需要同步的语义查询，因此这里显式分步推进时钟。

Material 3 Snackbar 的自动消失计时和退出动画由 Compose 测试时钟驱动。原来的 `waitUntil(15_000)` 以真实时间计时，虽然每轮会推进一帧，但语义查询、设备同步和调度会消耗真实时间。慢模拟器可能尚未推进完 Snackbar 的显示时长，就先触发真实时间超时。此修复不延长超时数值、不主动关闭 Snackbar，也不修改应用提示行为或依赖版本。Repository 更新等外部条件仍使用 `waitUntil`。

参考：[Android 官方 Compose 测试同步说明](https://developer.android.com/develop/ui/compose/testing/synchronization)。

创建活动流程在 Explore 输入搜索词后显式收起软键盘，再滚动并断言活动卡片可见，避免键盘改变列表可用高度而干扰可见性检查。

## 手动检查

- 重启进程，确认 Demo 种子数据恢复；旋转屏幕，确认仓库数据仍在。
- Explore 输入不存在的标题或城市，检查空状态；清空后恢复。
- 检查深色模式、320dp 宽屏幕和放大字体；正文可换行，表单和页面可滚动。
- 取消活动后切换参与者，检查取消通知；新的报名和签到应被拒绝。
- 在 Profile 中查看历史读书会，提交 1–5 分评论；再次打开显示已提交。
- 已安装地图应用时验证地址 Intent；未安装时检查错误提示。

## 故障定位

| 现象 | 检查 |
| --- | --- |
| 找不到 SDK | 在 Android Studio 安装 Platform 37、Build Tools 36.0.0，并检查本机 `local.properties` 或 `ANDROID_HOME` |
| JDK / Gradle 不兼容 | 选择 Android Studio bundled JDK；不要为绕过问题任意升级 Wrapper / AGP |
| Maven Central HTTP 403 | 使用上方可选镜像；不关闭证书校验 |
| 没有连接设备 | 用 SDK 的 `platform-tools/adb devices` 检查，先启动模拟器再执行 connected 测试 |
| JVM 报 native memory allocation failed | 减少本次构建的 worker 数量及 JVM 内存上限，见下方 Windows 命令；不必修改共享构建配置 |
| 演示数据突然恢复 | 进程被终止后重建是当前 Fake 设计；持久化尚未接入 |

Windows 内存紧张时，可仅为本次验证指定参数：

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:connectedDebugAndroidTest --console=plain --no-daemon --max-workers=2 '-Dorg.gradle.jvmargs=-Xmx1024m -XX:ActiveProcessorCount=2 -Dfile.encoding=UTF-8' '-Pkotlin.compiler.execution.strategy=in-process'
```

设备离线或安装失败时，即使 Gradle 输出 `BUILD SUCCESSFUL`，也不能认定界面测试通过；必须检查本次测试报告的用例数、失败数和跳过数。

第一阶段交付时的验证结果见 [IMPLEMENTATION_REPORT](IMPLEMENTATION_REPORT.md)，不能把历史结果或测试计划视作当前已通过的证据。

## 2026-11-14 Windows 复验

分支：`dev/hcc-mvp`。环境：Windows、JDK 17、Android 17 / API 37 的 `Medium_Phone` x86_64 模拟器。

接手时五条流程均在 `awaitSnackbar` 的真实时间等待中超时。本次诊断单条原逻辑时也观察到通过：Snackbar 等待推进 4,144 ms 虚拟时间，实际消耗 6,343 ms，说明两种计时不能混用，失败受设备负载影响。临时计时日志已移除。修复等待后另发现搜索后键盘展开影响卡片可见性，已补齐收起键盘的测试操作。

| 检查 | 本次结果 |
| --- | --- |
| `:app:assembleDebug` | 成功，增量构建，应用源码未改动 |
| `:app:testDebugUnitTest --rerun` | 31 / 31 通过，0 失败 / 错误 / 跳过 |
| `:app:connectedDebugAndroidTest` | 5 / 5 通过，0 失败 / 错误 / 跳过；实际检查创建、补位、候补取消、签到、注册及表单验证流程 |
| `:app:lintDebug` | 0 错误，9 条依赖版本更新警告；未升级依赖 |
| `git diff --check` | 通过 |

期间曾遇到 JVM native memory allocation failed，以及设备短暂离线导致测试未执行。使用上述低内存参数、确认设备就绪后完成复验；通过结论来自最终 XML 报告中的实际用例结果。
