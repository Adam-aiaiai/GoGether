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
| 演示数据突然恢复 | 进程被终止后重建是当前 Fake 设计；持久化尚未接入 |

本次实际执行结果见 [IMPLEMENTATION_REPORT](IMPLEMENTATION_REPORT.md)，不能把测试计划视作已通过证据。
