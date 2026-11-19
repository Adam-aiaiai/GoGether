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
4. 使用窗口内的测试活动 → Alice 查看自动签到时段 → Bob 无需等待开启即可签到 → 按钮禁用且仅有一条签到。
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

## E：自动签到窗口与资格校验

本轮确认：正常活动默认启用签到，无需手动开关；窗口为开始前 30 分钟至开始后 30 分钟，两个端点都允许。沿用活动取消 / 完成 / 到达结束时间后禁止签到的限制。组织者仍只能在活动开始前取消；开始后取消留给 B 确认。未修改报名、候补转正、取消报名和反馈资格规则。

- `CheckInWindowTest`：两个端点、端点外 1 ms、取消 / 完成、短活动提前结束、跨午夜。
- `CheckInEligibilityTest`：各报名状态、登录状态、时间变化、账号隔离、已有签到记录、活动关闭提示。
- `CheckInRepositoryTest`：绕过 UI 直接提交也必须满足窗口与资格；最新取消 / 转正状态；账号切换；同进程并发重复提交；取消活动后保留已有签到且阻止新增；一次提交只读取一次时间。
- `CheckInScreenTest`：不退出页面时跨越时间端点、候补转正与取消、切换 / 退出账号、提交中、已签到、活动取消 / 完成 / 结束后的按钮与提示；保留原有反馈资格。
- `MvpFlowTest`：自动签到流程使用窗口内活动，其他四条流程继续使用原有种子数据。默认 Demo 活动在未来，不能再通过管理页开关提前排练签到。

测试只验证本地 Fake 与页面行为。真实扫码、可信身份和服务器时间、持久化、两设备同步、缺席结算及预约限制仍不在本轮范围内。

2026-11-14 本轮最终验证（Windows、JDK 17、Medium_Phone / API 37）：`assembleDebug` 成功；单元测试 53 / 53 通过；设备界面测试 12 / 12 通过（新增 7 条，保留并适配原有 5 条），均无失败、错误或跳过；`lintDebug` 0 错误、9 条已有依赖更新警告；`git diff --check` 通过。已检查本轮 XML 报告和设备测试退出码。

## 2026-11-15 E：双手机扫码与定位原型

试用入口、步骤及模拟服务限制见 [CHECKIN_PROTOTYPE](CHECKIN_PROTOTYPE.md)。新增 `PrototypeProtocolTest`（10 条）、`PrototypeViewModelTest`（6 条）、`PrototypeScreenTest`（4 条）。

- `assembleDebug` / `assembleDebugAndroidTest` 成功；全部 JVM 测试 **69 / 69**，全部设备界面测试 **16 / 16**，失败、错误和跳过均为 0，设备测试退出码为 0。
- `lintDebug` 0 错误、14 条警告：10 条依赖 / 工具版本提示，4 条 SharedPreferences KTX 写法建议。同步提交偏好用于确认本地记录写入是否成功，尚未升级其他依赖。
- 已验证独立模拟服务之间传递二维码、实际 ZXing 编解码、双方 200 米边界、位置质量 / 新鲜度 / 模拟位置拦截、短码过期、最新位置重新校验、后台取消定位任务、候补和未报名拦截、重复签到及本地持久化。
- 已在 API 37 模拟器实际安装调试 APK，手动检查 Profile 入口、Android 相机和精确位置系统授权、相机扫码页面启动及取消返回；检查二维码页面截图，无裁切遮挡。为取得排版截图另单独运行了二维码展示用例，成功。
- **尚未完成两台实体手机现场试验**，没有宣称验证了室内定位质量或相机对另一台手机屏幕的识别率。模拟器测试通过注入位置验证规则，不绕过真实原型对已标记模拟位置的拦截。
- `git diff --check` 通过。继续在 `dev/hcc-mvp`，没有提交、推送或修改其他分支。

## 2026-11-15 E：组织者二维码与分组名单

- 组织者页面改为上方二维码、下方已签到 / 未签到头像与名字；名单自动换行并可随整页滚动。默认头像为姓名缩写；保留模拟报名名单及本机记录范围。
- `PrototypeProtocolTest` 新增 2 条：正式名单与本场签到关联、活动隔离 / 查看权限；独立手机没有同步时不能虚构签到结果。
- `PrototypeScreenTest` 新增 3 条：分组更新与空状态；320dp 窄屏多排与最后一人可见；多人示例不改真实状态且换活动重置预览。
- 本轮 `assembleDebug` / `assembleDebugAndroidTest` 成功；全部 JVM 测试 **71 / 71**，本模块设备界面测试 **7 / 7**，均无失败、错误或跳过，设备测试退出码 0。未重复运行其他模块的界面测试。
- `lintDebug` 0 错误、14 条既有警告；`git diff --check` 通过。额外单独运行一次预览用例（1 / 1）取得界面截图，已检查二维码、头像和换行无挤压或遮挡，后续名单可滚动查看。
- 真实头像与跨设备名单同步仍待 A / C / 后端接入；没有新增缺席判断、预约限制或报名规则。仅在 `dev/hcc-mvp` 本地修改，尚未提交或推送。

同日界面文案调整：移除名单周围的“示例”“虚构”和开发说明小字，页面文字及日期不再使用圆点分隔符，返回按钮改为“< 返回”；两组均保留头像和姓名。重新构建 APK 及测试包成功，通过直接 instrumentation 在模拟器运行现有 `PrototypeScreenTest`，结果 **7 / 7**。已查看页面顶部及滚动后两组名单截图；本轮不改变业务规则，未重跑 JVM 测试。

## 2026-11-15 E：手机屏幕适配

- 组织者页改为固定可用高度，竖屏二维码在上，横屏二维码在左；“已签到 / 未签到”标签切换名单，仅名单网格滚动。集合信息与次要操作移入“更多”面板。
- 新增 320×568dp 小屏、640×360dp 横屏、320×568dp 加 1.6 倍字体三项配置测试，使用 40 人名单，验证滚动至最后一人时二维码位置不变、按钮可见、两个标签可切换、“更多”可用。
- 大字体下标签分两行显示状态和人数，并检查文字布局无溢出。视觉检查发现过标签截断，已修复可用宽度和高度；最终截图显示状态、人数及头像姓名完整。
- 最终 `assembleDebug` / `assembleDebugAndroidTest` 成功；直接 instrumentation 运行 `PrototypeScreenTest` **10 / 10** 通过，完整输出位于 `app/build/reports/prototype-layout-test.txt`。`lintDebug` 0 错误、14 条既有警告，`git diff --check` 通过。只修改界面与相关测试，未重跑 JVM 或其他模块设备测试。
- 已检查普通竖屏、小屏、横屏及大字体截图；不代表完成双实体手机扫码和现场定位验收。修改仍在 `dev/hcc-mvp`，尚未提交、推送。

## 2026-11-15 E：扫码历史与活动评价

- E1–E6 的本地 / 服务端边界及选做项核对见 `MEMBER_E_CHECKLIST.md`。本轮将原型活动、用户关联、签到、评价接入独立“我的记录”页面，不增加二维码主屏内容，也不修改 B/C 的活动和报名规则。
- `PrototypeFeedbackTest` 新增 7 条：扫码至评价链路、服务重建后重复评价、结束端点、无需签到的既有评价资格、取消 / 未报名 / 候补 / 他人活动拦截、评分与评论长度、活动元数据冲突、不同服务实例并发防重、无效码不生成历史。
- `PrototypeViewModelTest` 新增 1 条：同一活动扫码成功后推进时钟到结束，从记录提交评价，无需重扫过期码；重复评价不覆盖原内容，切换账号隔离。
- `PrototypeHistoryTest` 新增 4 条：待评价筛选与完整表单、失败保留输入与账号切换清理、结束时间与取消状态更新、真实 SharedPreferences 重建及多个服务实例并发签到 / 评价只成功一次。磁盘测试还覆盖换行、引号与 emoji 评论保存。
- 本轮完整 JVM **79 / 79**、完整设备界面 **26 / 26**，失败 / 错误 / 跳过均为 0，设备测试退出码 0；`assembleDebug` 与测试包构建成功。`lintDebug` 0 错误、16 条警告（10 条版本提示、6 条 SharedPreferences KTX 写法建议）；`git diff --check` 通过。
- 额外运行一次评价界面用例 **1 / 1** 获取截图，已检查历史列表、评价表单与已提交内容可读，无裁切。真实双手机现场扫码及跨设备同步尚未验收。
- 新记录保存完整活动信息。旧版本仅有签到时间的记录保留防重，不能从中推断结束时间；需重新读取原活动信息后才能进入新历史评价流程。

## 2026-11-15 E：头像详情、姓名查找与签到轻提示

- 主页面新增放大镜入口，姓名查找在独立面板中进行；查找覆盖已签到 / 未签到两组、中文部分名字、英文大小写、首尾空格、同名不同用户和无结果状态。点头像或查找结果显示最新签到时间，未签到不显示虚构时间。
- Bob 的浮层只在记录成功保存后出现；Alice 根据新读取到的签到记录提示，并对旧记录去重，多人合并。离开页面、换活动清理提示，旧计时器不能清除新提示。当前无跨手机同步，测试通过写入同一本地存储验证 Alice 的新增记录触发。
- 全部 JVM 测试 **81 / 81**，无失败、错误或跳过，新增两项覆盖参与者成功 / 失败 / 重复 / 恢复旧记录、组织者已有记录 / 新增 / 多人 / 后台 / 换活动。
- 本模块设备测试通过 **14 项**：原有 `PrototypeScreenTest` 10 项及新增 `AttendanceInteractionTest` 4 项。包含 320×568dp、横屏、1.6 倍字体、输入法、详情跟随记录变化、提示定时消失和二维码位置不变。未重跑其他模块设备测试。
- 初次测试包未包含刚新增的测试类，出现加载失败；重新构建测试包后新增 4 项全部通过。原有 10 项输出在 `app/build/reports/attendance-interaction-tests.txt`，新增 4 项最终输出在 `app/build/reports/attendance-interaction-new-tests.txt`。
- `assembleDebug`、`assembleDebugAndroidTest` 成功；`lintDebug` 0 错误、16 条既有警告。已查看时间详情、带键盘的查找结果、Bob 成功浮层、Alice 到场浮层截图；提示不增加布局高度，不遮二维码。
- 修改仅在 `dev/hcc-mvp` 本地，未提交或推送；未完成实体双手机及服务端实时同步验收。

## 2026-11-15 E：移除查看时间的确认弹窗

- 头像详情改为原有名单区域内显示，支持收起、系统返回与切换分组；二维码保持原位。搜索结果直接在列表中展开时间，重复点击收起，查找面板改为平直边缘和分隔线。
- 按用户确认，将“刚刚签到”和“签到成功”背景从约 93% 不透明降到 80%，文字不透明；圆角从 20dp 缩到 4dp，减小内边距。
- 应用及测试包构建成功，`AttendanceInteractionTest` 和 `PrototypeScreenTest` 合计 **14 / 14** 通过，输出位于 `app/build/reports/attendance-flat-ui-tests.txt`。验证详情不再是对话框、二维码位置不变、最新记录更新、查找展开收起、提示消失、窄屏 / 横屏 / 大字体。仅改展示交互，本轮未重跑业务 JVM 测试和 lint。
- 已检查四张新截图：`attendance-time-flat.png`、`attendance-search-flat.png`、`participant-success-light.png`、`organizer-arrival-light.png`。测试使用固定时钟与小屏配置，截图外侧留白不是页面新增内容。未改共享主题或其他成员业务，仍只在 `dev/hcc-mvp`，未提交或推送。

## 2026-11-15 E：签到流程统一圆角

- 参照用户提供的两张乐刻截图，采用视觉近似的统一 12dp 半径，集中定义于 `CheckInStyle.kt`。涵盖 Alice / Bob 页面信息卡片、矩形按钮、输入框、历史记录卡片、筛选和评分项、确认框及成功提示；底部面板顶部两个角为相同半径。头像与二维码保持各自识别形状。
- 原公共 `InfoCard` 写死 20dp，按钮默认胶囊形，因此在签到模块内显式应用统一样式，未修改共享组件或全局主题。提示仍为 80% 不透明度，名单内查看时间交互不变，页面尺寸和内容间距不增加。
- 应用及测试包构建成功；原有签到交互、手机布局、历史评价共 **18 / 18** 设备测试通过，输出 `app/build/reports/checkin-radius12-tests.txt`。本次仅调整样式，未新增业务测试，未重跑 JVM 和 lint。
- 已检查 `participant-success-radius12.png`、`organizer-arrival-radius12.png`、`attendance-search-radius12.png`，核对卡片 / 按钮 / 提示弧度及小屏、大字体显示。签到主屏截图直接捕获组件边界，去除测试画布外侧空白。
- 仍在 `dev/hcc-mvp`，未提交、推送或修改其他分支。

## 2026-11-15 E：参与者签到页精简

- Bob 页取消状态 / 报名 / 最近签到三张大卡片，改为账号资格摘要、一处状态区域、固定扫码按钮和说明入口。成功显示活动与时间；失败显示本次原因；恢复旧记录明确标为“最近签到”。权限说明移入次要面板，保留 12dp 圆角。
- 增加明确的扫码展示阶段，避免用文案或旧记录推断本次成功。失效码、重复扫码、权限提示切换为本次提示；后台中止核验恢复可操作状态。原有签到资格、时间、持久化规则和共享接口没有修改。
- 全部 JVM **81 / 81** 通过。设备相关测试 **21 / 21** 通过，记录位于 `app/build/reports/participant-compact-tests.txt`；新增三项覆盖 320×568dp 普通 / 1.6 倍字体、横屏、权限入口、错误与旧记录分离、账号隔离。成功结果无需滚动即可显示在固定按钮上方，长内容仅中间区域滚动。
- 已检查等待扫码、签到结果与大字体截图；应用及测试包构建成功。本次未重跑 lint，未声称完成真机或跨设备同步验收。修改仍在 `dev/hcc-mvp` 本地，未提交或推送。
- 最终缩短首页提示后，重新构建并复核参与者页面 **3 / 3**，输出 `app/build/reports/participant-compact-final-tests.txt`；截图在按钮点击前采集，避免触摸水波纹影响视觉核对。

## 2026-11-15 E：围绕活动组织 Bob 参与页

- 增加活动名称、组织者、时间、集合位置、同行人员与签到状态；底部固定操作随活动进度进入扫码、对应签到记录或评价表。详细说明按需打开，保持 12dp 圆角，不修改公共主题、B/C 接口或其他分支。
- E 内部展示资料提供“羽毛球交流”的名称与介绍，沿用 A/C 模拟用户。时间与坐标取自已读取的本机场次；首次无场次显示待扫码获取。正式上线需 B 提供真实活动资料和地点名称，C 提供报名数据，服务端同步记录。
- 当前参与场次与组织者托管场次分开保存于页面状态。扫码成功、定位失败、离开前台后都保留已识别的活动；只展示匹配当前活动与当前用户的回执。历史入口可直接选中本场记录，避免结束后再次扫码才可评价。
- 位置面板提供外部地图入口与未安装地图提示；签到说明保留“扫描其他活动”，已结束或取消的旧场次不会阻止用户发起下一场扫码。地图坐标呈现及实体双手机仍待实测。
- 应用与测试包构建成功，JVM **82 / 82** 通过。设备回归首轮 **21 / 21**（`participant-event-tests.txt`），补充活动归属、评价入口、位置入口、关闭状态和资格校验后，参与者专项 **7 / 7**（`participant-event-final-tests.txt`）；去重共 **25** 项界面测试。包含 320×568dp、1.6 倍字号、横屏与新旧回执隔离。
- 已检查首次进入、已读取活动、签到成功三种截图。测试截图日期来自固定测试时钟。未重跑 lint，未声明真实后端或跨手机同步完成。修改留在 `dev/hcc-mvp` 本地，未提交或推送。

## 2026-11-16 E：统一入口、活动绑定与账号隔离

- 新增 `CheckInEntry` / `CheckInEntryIntent` 指定活动与通用入口，以及 E 的只读身份、报名来源接口。默认仍用模拟报名数据；未修改其他成员模块、公共导航、共享模型或仓库。
- 请求相机前捕获活动目标；误扫其他活动、同编号但时间 / 集合点不同均拒绝，不定位、不关联错误活动或保存签到。旧相机请求在切换活动后失效，明确选择“扫描其他活动”才解除本场绑定。历史记录可以进入本场签到。
- 账号变化使旧页面实例失效。身份监听覆盖相机遮盖页面的后台阶段；定位返回、扫码回调和提交前也校验身份。清除展示中的旧活动 / 记录 / 名单，取消异步操作，避免旧结果落到新账号。
- 初始化和切换活动时只选中匹配用户 / 活动资料的回执。其他用户不会继承本机 Alice 的托管场次；Alice 选择无法在本机托管的旧活动时也不会错误显示当前另一场的二维码。
- 新旧 E 评价表统一主动选分，旧表的草稿按活动和用户隔离；不修改既定评价资格。旧 Demo 的存储与导航仍留待协作接入，不声称已替换 B 的活动详情入口。
- 最终 `assembleDebug`、`assembleDebugAndroidTest`、`testDebugUnitTest` 成功。JVM **95 / 95**，其中新增 `CheckInEntryBindingTest` **13 / 13**，覆盖误扫、正确重试、相同编号资料冲突、显式通用扫码、过期回调、账号切换、定位期间退出、恢复旧记录、跨用户托管隔离、报名资格变化和评价提交期间切换账号。
- 首轮设备回归 **37 / 37**，日志 `app/build/reports/checkin-entry-ui-tests.txt`。最终包相关复核 **12 / 12**，日志 `app/build/reports/checkin-entry-final-tests.txt`；包含新增实际 Activity 的后台账号切换用例，去重共 **38** 项相关界面 / 生命周期用例。本次未重跑 lint，未进行实体双手机或真实后端验证。
- `git diff --check` 通过。只在 `dev/hcc-mvp` 本地修改，未提交或推送；原有其他模块的未提交改动保持保留。

## 2026-11-16 E：独立出席刷新与历史名单

- 名单从二维码定位 / 续码中分离，新增 E 内可挂起的 `AttendanceSource` 模拟读取接口。前台现场页独立更新，历史页按进入 / 手动刷新读取；两类页面均有加载、失败重试和上次成功结果保留。
- Alice 可以查看已结束或取消活动的出席名单、人数和签到时间，保留按名字查找与原位详情。旧活动不会替换当前出码活动；未签到不自动认定缺席，正式历史名单冻结 / 退出报名处理仍待 C 与小组确认。
- `assembleDebug`、`assembleDebugAndroidTest`、`testDebugUnitTest` 成功；JVM **102 / 102**。新增 `AttendanceRefreshTest` **7 / 7**，覆盖首次加载、失败重试、二维码 / 定位独立性、人员及签到变化、空名单、首次与重复提醒、过期异步响应、账号变化、前后台取消与恢复、历史活动隔离和查看权限。
- 设备回归 **26 / 26**，日志 `app/build/reports/attendance-refresh-ui-tests.txt`；另补存储重建读取测试 **1 / 1**，日志 `app/build/reports/attendance-history-persistence-test.txt`。合计 **27** 项，包含新增 4 项以及小屏、横屏、大字号、姓名查找、签到详情、评价历史、活动入口、真实 Activity 后台账号切换回归。
- 检查模拟器实际截图 `attendance-refresh-failure.png` 与 `attendance-ended-record.png`；刷新提示和重试留在名单区域，二维码位置不变，名单内部滚动。测试时间使用固定时钟，不代表真实活动日期。
- 仅修改 E 原型源码、E 测试及文档；原有其他模块改动保留。仍在 `dev/hcc-mvp`，没有提交 / 推送。未重跑 lint；未进行实体双手机、真实服务端或网络同步验收。

## 2026-11-16 E：活动评价内容与排版

- 活动摘要、五星评分、可选评论与底部固定提交区构成评价表；保存后显示只读星级、评论与提交时间。继续使用紫色和 12dp 圆角，没有改动评价资格、数据结构或其他成员模块。
- 新增 `FeedbackLayoutTest` 两项，覆盖 320×568dp 普通 / 1.6 倍字号、主动选星、评分说明、固定提交、500 / 501 字边界、输入键盘、保存中禁用和取消活动。原有失败草稿保留、账号隔离、重复提交与持久化测试继续通过。
- 首轮 21 项界面测试发现失败提示可落在滚动区域外，已移到固定提交区并将断言加强为实际可见。最终相关设备测试 **21 / 21** 通过，日志 `app/build/reports/feedback-redesign-final-tests.txt`；JVM **102 / 102**，失败 / 错误 / 跳过均为 0。应用与测试包构建成功。
- 已检查空表、填写后、已保存和大字号截图；普通字号小屏完整显示评分、评论计数与提交按钮，大字号内容可滚动且提交按钮保留。截图来自固定测试活动时间，不代表当前真实活动。
- 已记录用户偏好的授权弹窗蓝色 #4E5E8B（当前截图取色），目前仍保留紫色；配色来源与协作条件见 `CHECKIN_PROTOTYPE.md`。未重跑 lint，未声明真实后端 / 双手机同步验收；只在 `dev/hcc-mvp` 本地修改，未提交或推送。

## 2026-11-16 E：补充分项评分与评价标签

- 新增三项可选评分、8 个喜欢标签和6 个改进标签；随总体评价保存并在历史回显。读取旧 JSON 记录时可选详情默认为空，原有评分、评论、资格与防重规则保留。
- JVM **103 / 103** 通过；新增验证非法分项值、未知 / 重复标签不写入，以及详情保存和重复提交拦截。设备相关回归 **19 / 19**，日志 `app/build/reports/feedback-details-tests.txt`；包括填写 / 清空分项、标签选择 / 取消、失败草稿保留、重试后的完整数据、真实存储重建、旧记录兼容、小屏、大字号与账号切换。
- 最后调整标签为浅底紫色选中态、分项标题换行适配后，应用及测试包构建成功，评价界面专项 **3 / 3** 再次通过，日志 `app/build/reports/feedback-details-final-tests.txt`。已查看最终分项与标签截图，内容分区滚动，提交区固定。
- 匿名、可见范围及图片 / 视频规则已向用户提问，尚待答复，未实现或宣称完成这些功能。仍仅修改 dev/hcc-mvp 的 E 原型、测试及文档；未提交 / 推送，未重跑 lint 或进行真实后端验证。

## 2026-11-16 E：匿名评价、可见范围与图片附件

- 按用户确认加入匿名评价（保留内部账号关联）、默认公开 / 仅组织者可见、最多 3 张图片，不做视频。匿名评价在他人及组织者视图隐藏姓名与真实头像；私密评价及图片仅作者与模拟组织者可读取。
- 使用系统照片选择器，单张源文件不超过 10MB；支持预览、移除、剩余数量校验、本机复制保存及历史回显。导入后不依赖原始相册文件；图片重新编码去除原始 EXIF 信息。提交失败保留草稿，账号与活动隔离，旧评价字段默认兼容。
- JVM **106 / 106** 通过，无失败、错误或跳过。应用及测试包构建成功。相关设备测试分批执行，去重 **27 / 27** 最终通过：原有相关用例 19 项，新增隐私界面 4 项、图片存储 4 项。覆盖真实系统选择器打开 / 取消、选图预览移除、草稿恢复、保存与重复拦截、角色可见性、非法图片、超限、原图删除后仍可读取、小屏及大字号。
- 首批日志 `app/build/reports/feedback-privacy-photos-tests.txt`；修正测试中未滚动到移除按钮、JUnit 方法返回类型后复核，另一次历史截图 PixelCopy 超时重跑通过，见 `feedback-privacy-photos-final-tests.txt`。最后修正测试清理重复删除已移除的原图，单项 **1 / 1** 通过，见 `feedback-photo-copy-final-test.txt`。这些测试修正没有放宽产品权限或校验。
- 已查看最终图片 / 隐私表单、保存结果、组织者评价列表及真实系统选择器截图。保持紫色与既有圆角，评价内容可滚动、提交区固定。截图使用测试活动与图片，不代表真实用户数据。
- 仍是本机原型，跨设备同步及可信身份、可见范围和附件访问控制需服务端接入。本次没有重跑 lint 或进行实体手机测试；仅在 dev/hcc-mvp 修改 E 原型、测试与说明，未提交或推送。

## 2026-11-16 E：持久评价草稿与无引用图片清理

- 新增完整本机草稿保存，按账号 / 活动隔离；退出页面与结束进程后可以恢复总体及分项评分、标签、评论、匿名、可见范围、图片引用。保存失败保留输入；正式评价与移除草稿同次提交，旧编辑不能复活已提交草稿。
- 图片移除受草稿和正式评价引用保护；提交后清理未提交附件。进入对应记录时回收超过 24 小时、属于当前账号和活动且没有引用的图片 / 可识别归属的中断文件；不删除其他账号、其他活动、新导入图片及有效草稿图片。
- `assembleDebug`、`assembleDebugAndroidTest`、`testDebugUnitTest` 成功，JVM **106 / 106**，无失败、错误或跳过。
- 新增 `FeedbackDraftStoreTest` 5 项、`FeedbackDraftUiTest` 2 项、`FeedbackDraftRestartTest` 2 项。包括完整字段与超长评论草稿、特殊账号键、退出重进、两账号 / 两活动隔离、失败 / 成功提交、提交后旧写入、精确范围清理、图片引用保护、损坏导入残留和重复清理。
- 相关设备首轮 23 项中发现快速连续点击标签时使用了旧选择状态，已修正为读取最新草稿。最终相关 UI 复核 **9 / 9** 通过，日志 `app/build/reports/feedback-draft-final-ui-tests.txt`；其他首轮通过项见 `feedback-draft-tests.txt`。
- 单独执行 `FeedbackDraftRestartTest#aWriteBeforeProcessExit`，然后 `adb shell am force-stop com.gogether.app`，再执行 `#bReadAfterProcessRestart`，两项均通过；新进程恢复了全部字段和可读取的图片副本。日志 `feedback-draft-restart-write.txt`、`feedback-draft-restart-read.txt`。该测试类按方法名顺序排列，整类运行仍可验证写读；验证跨进程时应按上述方式分开运行。
- 本轮去重共 **25** 项相关设备测试最终通过；覆盖 320×568dp、1.6 倍字号、固定提交区、系统选图、隐私、图片及历史回归。原有测试增加仅针对自身活动的草稿清理，避免测试数据残留。
- 只修改 E 原型源码、E 测试及文档；分支仍为 dev/hcc-mvp，未提交或推送。没有增加页面卡片，没有新增一键放弃草稿入口，也没有接入真实后端；未重跑 lint 或进行实体手机验证。

## 2026-11-16 E：多活动、多组织者模拟对接

- 新增 E 内严格 B 活动目录与按活动区分的 C 模拟名单：Alice 组织两场、Carol 组织一场，人员部分重叠且报名 / 候补状态不同。通过已有指定活动入口注入，不改共享导航、B/C 模块或真实后端。
- 修正全局 Alice 权限假设：出码、取消、出席名单、私密评价和图片均按本场组织者判断；页面角色、名称、集合说明与组织者同步切换。清空跨活动旧评价结果，保留已有相机回调、名单异步响应及账号隔离校验。
- E 私有存储保存模拟组织者资料和报名快照；重建默认本地服务后仍能识别 Carol 的权限及 Bob 在另一场的候补资格，不回退到全局 Alice / 固定名单。有活动资料但缺报名快照时不授予资格。
- 新增 `MultiActivityIsolationTest` 5 项、`MultiActivityViewModelTest` 4 项，涵盖两组织者三活动出码 / 名单权限、跨场签到及防重、未知 ID / 冲突资料 / 错误地点、匿名私密评价、结束与取消隔离、角色切换、误扫、过期名单响应、旧评价结果清理。
- 新增设备 `MultiActivityIntegrationTest` 4 项：真实本机持久化与重建后的私密图片 / 组织者 / 候补权限、两账号多活动历史入口和标题、组织者 / 参与者页面切换、匿名私密评价列表隔离。测试数据按三场固定测试 ID 清理，不向普通用户自动植入模拟活动。
- `testDebugUnitTest` **115 / 115** 通过（无失败 / 错误 / 跳过）；`assembleDebug` 与 `assembleDebugAndroidTest` 成功。设备相关回归 **44 / 44** 通过，日志 `app/build/reports/multi-activity-isolation-tests.txt`；覆盖新增场景及名单加载 / 重试、签到页面、小屏 / 大字号 / 横屏、选图及图片授权、评价草稿、历史记录和统一入口。
- `git diff --check` 通过。只在 dev/hcc-mvp 修改 E 源码、测试和说明，未提交或推送。未运行真实后端、跨手机同步或实体手机现场验收，未重跑 lint；旧离线兼容适配器仍仅供演示。

## 2026-11-16 E：定位重试与扫码返回

- 新增 CheckInRecoveryTest 8 项：有效码重定位与连点防重、到期拒绝、活动取消、报名失效、账号 / 活动 / 前后台切换隔离、迟到定位失败、主动退出相机保留记录、无效码及提交失败不能冒充定位重试。
- ParticipantCheckInPanelTest 新增 320×568dp 验证：有效码显示“重新定位”，到期后原位置变为“重新扫码”，两个操作分别调用正确入口。退出相机恢复原状态，不弹失败或新的成功提示。
- testDebugUnitTest 共 123 / 123 通过；assembleDebug、assembleDebugAndroidTest 成功。相关设备回归 27 / 27 通过，日志 app/build/reports/checkin-recovery-ui-tests.txt；包括小屏、大字号、横屏、统一入口、账号生命周期及多活动 / 多组织者隔离。
- 首轮界面回归的一项旧测试受已有评价草稿影响；改用独立测试活动 ID 后完整重跑 27 项通过，不删除用户或其他测试的数据。
- 已检查定位重试与码过期两张截图；没有新增卡片或增加页面高度。重试只用于位置获取失败，原始凭据仍按原到期时间失效；不绕过后端接口校验，也不把本地结果当作跨设备可信同步。
- 本轮仅修改 E 原型源码、E 测试及 E 文档；分支 dev/hcc-mvp，未提交或推送。未运行实体手机 GPS / 相机现场测试或真实后端测试。

## 2026-11-16 E：签到提交结果恢复

- 新增 CheckInSubmissionRecoveryTest 11 项：保存后回复丢失、提交超时与明确未提交、查询未知 / 失败 / 超时、明确业务拒绝、错误账号 / 活动回执、进度保存失败、页面重建、账号 / 活动切换后的迟到回复及两活动待确认记录隔离。等待使用虚拟测试时钟，不依赖真实网络。
- 新增 PendingCheckInStoreTest：真实本机存储重建后恢复请求标识，账号 / 活动隔离、旧响应不能清除新请求、未解决请求不能被覆盖。
- ParticipantCheckInPanelTest 增加结果核对状态、小屏完整提示、固定按钮位置，以及结束 / 取消活动的历史核对入口。核对允许在原签到窗口结束后进行，不重新获取位置，不重发签到。
- testDebugUnitTest 共 134 / 134 通过；assembleDebug 与 assembleDebugAndroidTest 成功。相关设备测试去重 37 项最终通过：完整回归先通过 36 / 37，旧历史评价测试因输入后未收起键盘而未触发提交；补充收键盘、按钮可用及提交回调断言，并显式滚动检查保存内容后，PrototypeHistoryTest 5 / 5 复测通过。
- 日志：app/build/reports/checkin-submission-recovery-ui-tests.txt、app/build/reports/checkin-submission-history-retest.txt；小屏专项另见 checkin-submission-compact-ui-test.txt。已目视检查最终“签到结果待确认”截图，说明完整显示，保留紫色与原有布局。
- 新增异步提交来源、明确结果查询协议与待核对请求本机持久化；真实服务端仍需提供请求幂等、权威终态和唯一约束，不能用普通 404 / 缓存未命中代替“已确认未提交”。当前仅本地来源及测试注入，未接入真实网络或跨设备同步。
- 本轮只改 dev/hcc-mvp 中的 E 原型、E 测试及文档；未修改其他成员模块、未提交或推送。未运行实体手机现场验收或 lint。

## 2026-11-18 E：评价图片预览

- FeedbackPhotoPreviewTest 新增 5 项设备测试：从选中图片打开、左右翻页 / 首尾边界 / 张数、真实双指放大与单指平移、双击还原后恢复翻页、按钮换图后缩放重置、单图边界，以及账号 / 活动 / 图片列表变化关闭旧预览并阻止越权读取草稿图片。
- assembleDebug 与 assembleDebugAndroidTest 成功。首轮相关 19 项通过 17 项，两个真实双指测试发现图片与父级翻页的手势冲突；改为优先识别双指并保留普通单指翻页后，图片预览 5 项复测通过。相关测试去重 19 项最终通过，包括 FeedbackPrivacyUiTest、FeedbackPhotoStoreTest、MultiActivityIntegrationTest、FeedbackDraftUiTest 的 14 项回归。
- 日志：app/build/reports/feedback-photo-preview-tests.txt、app/build/reports/feedback-photo-preview-gesture-retest.txt。已检查全屏查看截图的返回、张数及前后切换按钮，主页面缩略图区域未增加内容。
- 图片读取沿用原有权限检查，最多三张及公开 / 仅组织者可见规则不变；切换账号 / 活动 / 图片集合会关闭旧预览。此次仅修改 E 图片组件、E 测试及文档，仍在 dev/hcc-mvp，未提交或推送；未进行实体手机手势验收，未重新运行单元测试或 lint。
