# Gogether 第一阶段交付记录

日期：2026-11-11（Asia/Shanghai）。本记录说明代码实施和验证结果，供小组接续开发。

## 交付内容

在远程已有初始提交 `86e11ec` 的基础上建立单一 Android `app` 模块。原仓库只有 README；用户确认允许新增 Android 工程。未重建 Git 历史，未修改原始课程资料与 Proposal。

应用显示名、Gradle 项目名使用 Gogether；namespace / applicationId 为 `com.gogether.app`。仓库和目录名仍为 GoGether。

| 文件组 | 内容 |
| --- | --- |
| 根构建文件、版本目录、Wrapper、`.gitignore` | AGP 9.4.0 / Gradle 9.6.0、Compose、测试依赖和本机产物排除 |
| `MainActivity.kt`、`GogetherApplication.kt`、Manifest / resources | 应用入口、共享 Repository、图标、显示名、系统栏与备份设置 |
| `core/model`、`core/data`、`core/ui` | 六个模型、集中快照、Repository 契约 / Fake、种子数据、公共组件 |
| `navigation` 与 `feature/*` | 八个主要页面和对应 ViewModel / 业务操作 |
| `ui/theme` | Material 3 紫色主色、橙色强调、浅色 / 深色主题 |
| `app/src/test`、`app/src/androidTest` | 31 个 JVM 用例与 5 个 Compose 页面流程 |
| README 与 `docs` | 启动、架构、计划、五人分工、测试、需求对照及本报告 |

## 完成页面及 Mock 功能

八个页面已接入同一 Repository：Demo 登录 / 注册、Explore、Event Detail、Create Event、My Events、Event Management、Check-in、Profile。底部导航为 Explore / Create / My Events / Profile。

创建活动后立即进入详情，并可从 Explore / Created 找到。六名虚构用户可在同一进程中切换角色。创建者自动占用一席；满员后进入 FIFO 候补；正式成员取消后按截止规则自动补位，并原子写入转正通知。重复报名、已取消、已截止、已开始或结束的无效请求被拒绝。

另提供组织者公告、集合信息、地图 Intent、取消确认、Demo 签到开关与单次签到、活动历史、评分评论和通知已读。UI 对 Demo 身份、签到及本地数据边界有明确标识。

## 实际验证结果

| 检查 | 结果 |
| --- | --- |
| Gradle `:app:assembleDebug` | 成功，生成 debug APK |
| `:app:testDebugUnitTest` | 31 / 31 通过，0 失败 / 错误 / 跳过 |
| `:app:connectedDebugAndroidTest` | Android 17 Medium_Phone ARM64 模拟器，5 / 5 通过 |
| `:app:lintDebug` | 0 errors，8 warnings，均为可更新的构建 / 库版本提示 |
| 最终 APK 安装与启动 | 成功，实际登录界面已查看 |
| 屏幕检查 | 默认约 411dp 宽度；320dp 宽度 + 1.3 字体 + 深色模式的主导航 / 首页无明显文字溢出 |
| `git diff --check` | 通过 |
| 忽略规则 | local.properties、缓存、build、凭据 / 密钥及验证截图被排除 |

五条界面流程已实际验证：创建后 Explore 与 Created 更新；取消后 Emma 自动转正并在 Joined / Inbox 可见；Finn 加入及退出候补；组织者开启后参与者只签到一次；本地注册及空表单校验。

最后对系统栏对比度和旧 Android 备份配置进行了小幅修正，再次完成构建 / Lint 和真实 App 启动检查。单元与 Compose 流程测试覆盖的业务及导航逻辑保持一致。物理手机、API 26 实机、平板、地图 App 实际路线、两设备同步和无障碍读屏尚未完成验证。

本机报告位于 `app/build/reports/`；验证截图在本机 `verification/`，均属于忽略的构建 / 检查产物。可通过 [TESTING](TESTING.md) 中的命令重新生成测试报告。

## 环境问题与处理

本机常规 Maven Central 下载曾返回 HTTP 403，使用可选 Google 托管镜像参数后完成构建。未关闭 TLS 校验，也未更改 Gradle / AGP 版本。

初次界面测试因旧版 Espresso 反射调用 `InputManager.getInstance` 而无法在 Android 17 执行。采用稳定的 Espresso 3.7.0、Runner 1.7.0、JUnit extension 1.3.0 后解决；该修复见 [AndroidX Test 官方发布说明](https://developer.android.com/jetpack/androidx/releases/test#espresso-3.7.0)。测试还会等待 Snackbar 消失，再操作可能被遮挡的底部按钮，保留真实触摸验证。

工具链组合核对依据为 [AGP 9.4 官方兼容性说明](https://developer.android.com/build/releases/agp-9-4-0-release-notes)。未因 Lint 的新版本提示任意升级。

## 正式功能边界与后续接续

Firebase 认证、持久化、真实多人实时同步、数据库事务 / 安全规则、二维码扫描和可信签到验证尚未实现。地图只跳转外部应用；不采集设备位置。活动编辑、历史配置复用、组织者与空闲时段筛选已由本地 Repository 支持；活动图片当前保存设备内容 URI，正式版需迁移到云存储。后台推送、跨设备同步和正式结束动作等见开发计划。

当前数据在进程结束后重置。Mutex 和共享快照只解决单进程状态一致性；后端接入后仍须验证并发最后一席、重复请求、跨用户权限、网络中断恢复与通知幂等。规则简化见 [ARCHITECTURE](ARCHITECTURE.md)。

- A 从 `feature/auth`、`feature/profile` 和导航继续。
- B 从 `feature/explore`、`feature/event` 继续。
- C 从 `RegistrationEngine.kt`、Repository 契约和业务测试继续。
- D 从 `feature/coordination`、通知模型及同步流继续。
- E 从 `feature/checkin`、CheckIn / Feedback 模型继续。

详细验收与协作方式见 [MODULE_OWNERSHIP](MODULE_OWNERSHIP.md)。

## Git 交付

分支：`feat/android-mvp-skeleton`。提交信息：`feat: initialize Gogether Android MVP skeleton`。

提交前只读复查远程仍为初始提交 `86e11ec`。本次只做本地提交，未执行 push、force push、reset hard 或覆盖他人修改。APK、SDK 路径、测试输出和密钥不进入提交。

用户确认后计划执行：

```bash
git fetch origin
git log --oneline --left-right origin/main...HEAD
git push -u origin feat/android-mvp-skeleton
```

推送前若发现远程新增 Android 工程或共享文件变化，应先检查并协调合并。本报告不构成自动推送授权。
