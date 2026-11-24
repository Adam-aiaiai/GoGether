# 全项目后端接入与本地运行

## 当前交付

分支为 `feat/shared-firebase-backend`。后端覆盖 A/B/C/D/E，Android 通过统一 Repository 接入。本地使用 Firebase Emulator Suite，账号、活动、报名、消息、签到和评价均通过真实网络接口共享；不是每个客户端各保存一份模拟业务数据。

尚未创建团队 Firebase 云项目，所以没有云端部署，也没有线上服务器地址。默认直接点 Android Studio Run 仍是原有 Demo。运行共享后端版需要下面的 emulator 构建参数。

| 模块 | 服务端与 Android 已接入的内容 |
| --- | --- |
| A | 邮箱密码注册、登录、退出、会话恢复、个人资料；服务端以 Auth UID 判定身份 |
| B | 活动创建/编辑/取消、封面上传、统一列表及筛选；仅组织者可编辑 |
| C | 事务报名、容量检查、FIFO 候补、取消后自动补位、重复请求防重、补位通知 |
| D | My Events、成员公告、集合信息、问答/组织者回答、时间地点变更通知、按账号保存已读状态 |
| E | 服务端集合点与短时二维码、报名/时间/距离校验、一次签到与中断恢复、出席名单、完整评价和照片可见性 |

断网时可读取当前账号的已保存快照；写操作必须由服务器确认，失败不会伪装成本地提交成功。退出清除该账号离线快照。Demo 数据不会自动导入账号后端。

## 在这台 Windows 电脑启动

先打开 Android Studio 的 Device Manager，启动一个 Android 模拟器。Android Studio 仍用于运行 App，下面的本地服务给 App 提供共享数据。

首次准备依赖（已经安装过且 lockfile 未变可跳过）：

```powershell
cd D:\Projects\GoGether\firebase
npm.cmd ci
cd functions
npm.cmd ci
cd ..
```

打开第一个 PowerShell 窗口，保持它运行：

```powershell
cd D:\Projects\GoGether
.\firebase\start-local.ps1
```

脚本只连接 `demo-gogether` 本地项目，启动 Auth 9099、Firestore 8080、Functions 5001、Storage 9199，绑定本机回环地址。第一次会下载模拟器；需可用网络和 Java。Node 推荐 22（与 Functions 配置一致），Java 使用 Android Studio 的 JBR。脚本在本机可自动发现 `D:\app\Android\Android Studio\jbr`，其他电脑可自行设置 JAVA_HOME。

按 Ctrl+C 正常退出时导出至 `firebase/.emulator-data`，下次启动自动导入，账号和数据可保留。测试脚本不要对这份演示数据运行，见后文。

第二个 PowerShell 窗口构建并安装联网版：

```powershell
cd D:\Projects\GoGether
.\build-local-app.ps1 -Install
```

有多个设备时加 `-DeviceSerial emulator-5554`（按 adb devices 实际结果填写）。APK 在 `app/build/outputs/apk/debug/app-debug.apk`。首次启动 Android 17 时允许本地网络访问，否则会停在连接授权页面。

打开 App，注册两个不同邮箱账号，以组织者和参与者分别登录。本地 Auth 仅用于测试，不会发送真实邮件。两台 Android 模拟器可同时连接同一后端；也可在同一台切换账号验证共享数据和已读状态。

Android Studio 的直接 Run：在项目根目录未提交的 `local.properties` 不能配置 Gradle 项目参数；在个人 `%USERPROFILE%\.gradle\gradle.properties` 加 `backend=emulator` 会影响其他同名参数项目，因此建议使用本项目脚本，或在 Studio 的 Gradle 命令运行 `:app:installDebug -Pbackend=emulator`。构建后直接在模拟器打开 Gogether 即可。

## 三种构建模式

| 参数 | 数据来源 | 用途 |
| --- | --- | --- |
| `-Pbackend=demo`（默认） | 原本本机 Demo | 无服务时演示旧流程 |
| `-Pbackend=emulator` | 本机四个 Firebase 模拟器 | 本次交付的共享后端联调 |
| `-Pbackend=firebase` | 团队正式 Firebase 项目 | 配置和部署完成后使用 |

切换模式需重新构建安装；后台连接失败不会切回 Demo。模拟器默认通过 `10.0.2.2` 连接电脑。Firebase 正式构建不允许 HTTP 明文连接，不申请本地网络权限。

USB 真机联调可保持服务仅监听本机，不必开放防火墙端口：

```powershell
adb -s YOUR_DEVICE reverse tcp:9099 tcp:9099
adb -s YOUR_DEVICE reverse tcp:8080 tcp:8080
adb -s YOUR_DEVICE reverse tcp:5001 tcp:5001
adb -s YOUR_DEVICE reverse tcp:9199 tcp:9199
.\build-local-app.ps1 -EmulatorHost 127.0.0.1 -Install -DeviceSerial YOUR_DEVICE
```

adb 需在 PATH 中，或使用 Android SDK 下 platform-tools/adb.exe 的完整路径。此处是真机操作说明，本轮没有两台实体手机的 GPS/相机实测记录。

## 后续配置正式 Firebase 项目

1. 全组共用一个 Firebase 项目，注册包名 `com.gogether.app` 的 Android 应用。
2. 启用 Email/Password Auth、Firestore 和 Storage；Functions 和 Storage 的正式部署可能要求开通结算，由项目负责人确认。
3. 将下载的 google-services.json 放入 `app/google-services.json`（已忽略，不提交）。Android 构建直接读取所需字段，缺失配置会报错，不会偷偷使用 Demo。
4. 在 firebase 目录登录 Firebase CLI，明确提供真实项目 ID，再执行：

```powershell
.\node_modules\.bin\firebase.cmd login
.\node_modules\.bin\firebase.cmd deploy --project YOUR_TEAM_PROJECT_ID --only firestore:rules,firestore:indexes,storage,functions
cd ..
.\build-local-app.ps1 -Backend firebase -Install
```

云端区域默认 us-central1；改区需同步 Functions 配置与 Android 的 firebaseRegion。不要提交服务账号私钥。模拟器创建的测试账号不会自动变成正式 Auth 账号。

正式发布前仍需在团队项目上验证索引、Auth 邮件、计费/配额和两台实体设备。现有 App 没有忘记密码入口，本轮没有新增密码重置页面。签名发布包、FCM 系统推送、应用商店发布不在本轮接入范围。

## 安全与一致性

- Auth UID 为唯一身份来源，客户端提供的角色、UID 或服务器时间会被拒绝。普通用户不能直接写活动、报名、公告和签到集合。
- 关键写操作使用 Firestore 事务；最后名额和补位由服务器决定。requestId 与 UID、请求内容绑定，丢失响应可复用原 requestId 重试。
- 私密/匿名评价通过服务端投影；其他参与者不能看到仅组织者可见的评价或照片，公开匿名评价不返回作者 UID。
- 签到二维码仅由服务器签发并保存在私有集合，短时有效；检查报名状态、服务器时间、集合点版本和距离。客户端报告的 GPS 与模拟定位标记仍不能替代设备真实性证明，不能宣称能够抵抗被篡改客户端的定位伪造。
- 本轮为课程规模实现：单活动容量上限 50、有效报名加候补上限 200；单次快照集合读取上限 1,000，超过明确拒绝，后续增长需分页。全局同步通知不适合大型生产系统。
- 图片仅支持 JPEG/PNG/WebP，单张最大 2 MiB，评价最多 3 张。上传未提交的文件、历史请求和过期二维码未实现定期清理；云端长期运行需补生命周期策略。

详细数据与接口契约见 [Functions API](../firebase/functions/API.md)。

## 自动化验证

先停止占用端口的手动模拟器。下面使用临时、空的本地服务，不导入/覆盖 .emulator-data：

```powershell
cd D:\Projects\GoGether\firebase
$env:JAVA_HOME = 'D:\app\Android\Android Studio\jbr'
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
npm.cmd run test:unit
npm.cmd test
npm.cmd run test:legacy
```

`test` 启动四项服务后通过真实 Auth 与 callable HTTP 接口测试。`test:legacy` 单独验证旧 D 协作接口和规则，运行期间会清空测试项目的 Firestore，不应对演示服务执行。

JVM 回归：

```powershell
cd D:\Projects\GoGether
.\gradlew.bat :app:testDebugUnitTest -Pbackend=emulator
```

设备测试需先启动本地服务与 Android 模拟器：

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest -Pbackend=emulator
```

FirebaseBackendFlowTest 使用真实 Android SDK 注册两个不同账号，验证跨客户端刷新、问答/公告、已读状态、退出再登录及断网缓存。签到界面测试使用可控接口；扫码令牌、名额事务、照片权限由真实后端 HTTP 测试验证。完整的相机扫码与实体定位仍需现场走查。

本轮实际运行结果见 [BACKEND_VERIFICATION.md](BACKEND_VERIFICATION.md)。
