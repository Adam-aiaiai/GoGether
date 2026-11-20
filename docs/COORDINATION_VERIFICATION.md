# D 模块验证记录 — 2026-11-15

分支：feature/coordination。Android Studio 自带 JDK，API 37 Medium_Phone 模拟器。

| 验证 | 实际结果 |
| --- | --- |
| Debug 构建及安装 | 成功；最终 APK 已安装到 emulator-5554 |
| JVM 单元测试 | 39/39 通过；其中 CoordinationTest 8 个，含重建恢复、收件人权限、保存失败回滚、保存中取消一致性 |
| Android 页面流程 | 6/6 通过；包含发布公告、My Events 查看、通知标记已读及原有报名/签到流程 |
| Android 私有文件读写 | 单独运行 CoordinationPersistenceTest，1/1 通过 |
| Lint | 成功，0 error；保留依赖版本等 warning |
| 后端输入及请求指纹单元测试 | 3/3 通过 |
| Firestore 本地模拟器集成测试 | 6/6 通过；权限、两个独立客户端重连、事务发布及幂等、集合更新 |
| git diff --check | 通过 |

Android 界面及设备文件测试先执行，随后最终保存取消修复通过 JVM 回归测试、构建和 Lint。没有把模拟器数据库客户端测试当作两台 Android 手机的真实验收。

云端函数通过本地 Firestore 模拟器直接执行处理器验证；尚未部署 Callable HTTP 端点。当前 App 默认仍使用本机持久化 Demo。团队 Firebase 项目、真实认证、真实 Repository 接线及两设备联调仍待完成，参见 COORDINATION_HANDOFF.md。

本地原有 AGP 9.3.3 兼容修改及 Android Studio 生成的 gradle-daemon-jvm.properties 保留；均不是 D 功能本身的要求。没有提交或推送 GitHub。

## 2026-11-18 完善后的验证

本次新增：日期/时间选择器、集合字段校验和成功关闭、My Events 通知入口、未读筛选和全部已读、近期/历史排序、空状态动作、失效活动返回、公告进度和失败保留。

- assembleDebug 成功。
- testDebugUnitTest：41/41，通过；CoordinationTest 10、RegistrationEngineTest 25、RepositoryTest 6。
- connectedDebugAndroidTest 最终整轮：12/12，通过，0 跳过。包含日期/时间原生对话框、集合更新后 Bob 在活动及签到页查看、通知筛选/批量已读、近期/历史、公告失败保留草稿/禁用重复提交、失效活动返回及已有流程。
- lintDebug 成功，0 error、18 warning（主要为版本更新等提示）。
- 首轮 1 条测试因对固定导航栏调用滚动操作失败，测试助手已修正，最终整轮通过。
- git diff --check 通过。

本次没有部署 Firebase；历史云端模拟器验证结果保留在上文。当前仍为本机持久化 Demo，真实登录和两手机联调待团队配置。

本次将此前本地 D 模块与本次改进一并提交到 feature/coordination，尚未推送 GitHub。自动生成的 gradle-daemon-jvm.properties 保留在本机且不纳入提交。
