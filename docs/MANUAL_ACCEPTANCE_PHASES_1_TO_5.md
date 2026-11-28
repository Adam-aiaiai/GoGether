# GoGether 五阶段手动验收清单

编写日期：2026-11-28（Asia/Shanghai）。适用版本：本机 `feat/member-ui-polish`，已完成第五阶段的 Demo APK。

团队发布分支为 `feat/event-attendance-social-ratings`，功能基线相同。组员请先按 [拉取、运行和准备数据指南](TEAM_TESTING_GUIDE.md) 准备自己的模拟器；下文固定日期及 18/54 数量是原交付设备的记录，组员数据按准备时刻生成，以活动标题、角色和状态作为验收依据。

本清单依据当前代码、阶段说明和模拟器现有数据编写。本次只新增这份文档，没有修改业务代码、重置模拟器数据或推送 GitHub。下面的勾选项尚待你手动执行；自动化通过不替代手动勾选。

## 1. 开始前：账号、入口与测试顺序

### 切换账号

从当前页面返回主界面，点底部 **Profile → 右上角三点（Account options）→ Switch to 目标账号**。切换后先确认 Profile 顶部姓名，再进入目标活动。当前已是该账号时，菜单不列出它。

| 账号 | 本清单中的用途 |
| --- | --- |
| Alice | 预置测试活动的组织者；查看签到、接收提问/评价、管理报名限制 |
| Bob | 普通参与者；提问、多轮回复、匿名公开评价、取消后重新报名 |
| Carol | 对照参与者；未签到资格检查、实名评价、举报、隐私检查 |
| David | 私密评价作者、候补及报名黑名单测试 |
| Emma | 候补转正对照、未提交评价的草稿测试 |
| Finn | 未报名用户，用于检查报名关闭等状态 |
| Project maintainer (Demo) | 项目维护者；处理举报。Alice 的组织者身份不具备此权限 |

这些是本机 Demo 账号，使用菜单切换即可，不需要输入邮箱密码。不同设备的 Demo 数据各自独立。

### 常用入口

| 要做的事 | 操作路径 |
| --- | --- |
| 找到自己参与/组织的活动 | My Events → Joined / Waitlisted / Created / History → 活动标题 |
| 找到未报名的开放活动 | Explore → 活动列表；有筛选条件时先清除，再按标题找 |
| 查看完整历史 | Profile → Activity history 右侧 More › |
| 打开提问 | 活动详情 → Questions & answers → Replies & actions |
| 打开组织者签到台 | Alice → 活动详情 → Manage → View check-in and attendance |
| 打开参与者签到 | 参与者 → 活动详情 → Check-in & meeting |
| 打开已结束活动的评价 | 活动详情 → Leave feedback。若不能评价，会显示资格说明；按钮本身不是资格凭证 |
| 查看其他人的评价 | 评价编辑器右上角三点 → View event reviews；已有评价/不可评价的记录页则点 View event reviews > |
| 查看通知 | My Events → Notifications · N unread → Interactions / Activity updates |
| 管理黑名单、查看举报 | Profile → 三点 → Privacy & reports |
| 查看签到和评价记录 | Profile → 三点 → Check-in and review records → My records / Records；或从活动签到页进入记录 |

页面较长时向下滚动找按钮；每次提交后等加载/动画结束，再判断结果。发生时间冲突提示时，确认是本清单的测试活动后点 **Join anyway / Join waitlist anyway**。

### 当前可直接使用的数据

编写时只读核对：模拟器当前账号 Alice，共 18 个活动、54 条报名记录。后续你创建活动会改变这些数量，不能一直拿 18/54 当验收标准。

| 活动标题 | 已准备的数据与用途 |
| --- | --- |
| Phase 1 - Review access | 已结束；Alice 已确认到场，Bob 已签到，Carol 正式报名但未签到；适合评价资格测试，编写时尚无评价 |
| Phase 1 - Host arrival | 已结束；Alice 尚未确认到场；适合检查组织者无到场记录不能评价 |
| Phase 1 - Rejoin test | 已于 11 月 28 日 15:45 结束，不能继续用于新的取消/重新报名测试 |
| Phase 2 - Reviews | 已结束；Bob 匿名公开 4 星、Carol 实名公开 5 星、Alice 组织者评价；综合分 4.5，2 条有效评价 |
| Phase 2 - Private feedback | Carol 的匿名私密评价，仅 Carol 和 Alice 可见 |
| Phase 3 - Conversations | Bob 的问题及 Alice/Bob 多轮回复；计划 11 月 29 日 17:36 结束，过期后只用于阅读旧对话 |
| Phase 3 - Review notifications | Bob 匿名公开、Carol 实名公开、David 匿名私密评价；两份公开参与者评价均为 4 星 |
| Phase 4 - Public ratings | Bob 匿名 5 星 + Carol 公开 3 星 → **4.0 / 2 reviews**；Alice 1 星、David 私密 1 星、Emma 已删除 2 星均不计入 |
| Phase 4 - No public ratings | 只有 Alice 组织者评价、David 私密评价 → **— / No reviews** |

如果你此前已手动修改上述评价，先记录当前值，按实际有效评分计算，不能把原始基准差异直接判为 bug。

**建议顺序：先做 P4-01～03 的只读基准 → P1 的资格与时间规则 → P2 的问答/评价互动，同时穿插 P3 通知检查 → P4 删除重评 → P2 举报/黑名单 → P5 重启回归。** P1 中 Bob 的首次评价留到 P3-03 提交。拉黑后及时解除，以免影响下一项。无需改系统时间、清空应用或重新安装。

每项勾选表示通过。未执行、条件不具备、发现异常时不要勾选，在文末记录表填写“待测 / 待补测 / 失败”。

## 2. 新建活动：需要时间窗口时再创建

所有标题加上本次验收时间，例如 `验收-Q-1128-1700`，便于区分多次测试。以下简称 Q、S、D、B、C，均由 Alice 创建。

统一操作：**Create** → 填 Event title、Description → 选择 Start date/time、End date/time → 填 Location（如 Campus hall）、City（Suzhou）、Maximum participants → 选择 Registration deadline 日期及右侧 Time → **Publish event**。封面可不选，其余可选项保持默认。日期/时间以界面显示的时区为准。

| 简称 | 何时创建与参数 | 初始报名安排 |
| --- | --- | --- |
| Q：问答与普通互动 | 开始设为明天 18:00，结束 19:00，截止明天 17:00，容量 6 | Bob、Carol 正式报名；用于问答、通知和公告，保持开放 |
| S：签到/重新报名 | 执行扫码测试前创建：开始约当前 +20 分钟，结束 +30 分钟，截止 +15 分钟，容量 4 | Bob、Carol 正式报名；保留空位，不让其他账号进入候补 |
| D：截止/开始边界 | 单独测试时创建：开始约当前 +20 分钟，结束 +25 分钟，截止 +10 分钟，容量 3 | Bob、Carol 正式报名；David 候补 |
| B：报名黑名单 | 执行 P2-11 时创建：明天的未来时段，截止仍在未来，容量 2 | Bob 正式报名；随后 David 候补、Emma 候补，确保 David 排前面 |
| C：取消活动 | 执行 P1-07 时创建：明天的未来时段，截止仍在未来，容量 4 | Bob 正式报名，再由 Alice 取消 |

容量包含 Alice：容量 3 代表 Alice 加 2 名普通正式参与者。创建 D/S 后记下真实截止、开始、结束时间；时间不够时重新创建新的测试活动，不在原有历史活动上改时间。

## 3. 第一阶段：报名、签到、定位移除、参与资格

**时间顺序提示：** S 创建后连续执行 **P1-03 → P1-08 → P1-09**，结束后再做 P1-10；不要在这三项之间等待 D 的截止/结束时间。若只有单台模拟器且未配置相机画面输入，先完成组织者出码，扫码两项记待补测即可。D 的 P1-02 → P1-06 作为另一组执行。

### P1-01 创建与定位移除

- [ ] **账号：Alice → Bob。** Alice 按上表创建 Q；Bob 打开详情并报名，再进入 Check-in & meeting 和签到说明。
  **预期：** 可以填写文字地址、城市，查看时间和集合说明；没有定位授权、经纬度、距离阈值、定位精度或地图导航入口。扫码可能申请相机权限，这是正常的。Q 尚未到签到窗口时不能签到。

### P1-02 容量、候补与组织者展示

- [ ] **账号：Alice → Bob → Carol → David → Alice。** 创建 D，依次让 Bob、Carol 报名、David 加入候补。Alice 打开 Manage 和签到台。
  **预期：** 容量 3 时，正式名额为 Alice、Bob、Carol；David 是候补。Alice 单独标为 Organizer；普通参与者签到统计只含 Bob/Carol，不能把 Alice 算成“未签到参与者”。Alice 没有单独退出自身报名的操作。

### P1-03 组织者确认到场

- [ ] **账号：Alice。** 创建 S，打开签到台，点 **Confirm arrival and show code**；第一次弹出确认时先返回/取消，再打开并点 **Confirm and show code**。成功显示后刷新二维码并退出重进。
  **预期：** 放弃确认不产生到场记录；成功出码后才显示 Arrival confirmed。刷新二维码不重复增加签到人数，也不把 Alice 放进普通参与者列表；重新进入仍保留到场状态。不要求定位权限。

### P1-04 只有签到者可以评价

- [ ] **账号：Bob → Carol。** 两人分别打开 **Phase 1 - Review access → Leave feedback**。Bob 只查看编辑器，暂不提交；Carol 尝试同一入口。
  **预期：** Bob 有评分/评价编辑器。Carol 显示未签到及不能评价的说明，没有可提交的新评价表单；即使详情按钮仍叫 Leave feedback，也不能绕过校验。Carol 在 History 中能看到该活动，但应标明 Not checked in，不能因此算作 Attended。

### P1-05 组织者评价资格与实名要求

- [ ] **账号：Alice。** 先打开 **Phase 1 - Host arrival → Leave feedback**，再打开 **Phase 1 - Review access → Leave feedback**。
  **预期：** 前者因无到场记录不能评价；后者可评价，显示 **Organizer reviews use your name**，匿名开关不能打开。若提交一份评价，应显示 Alice / Organizer；它不增加公开参与者综合分或评价数。该提交不会给 Alice 自己新增互动通知。

### P1-06 截止、开始、结束边界

- [ ] **账号：Bob、Carol、David、Finn；活动 D。** 按以下顺序观察，跨过边界后返回并重进详情以刷新状态。
  1. 截止前：确认 Bob、Carol 已正式报名，David 已候补。
  2. 到达截止时间、尚未开始：Finn 查看 D，不能新增报名/候补；Bob 点 Cancel → Yes, cancel。
  3. 切 David 看候补、切 Alice 看名单；再切 Bob 尝试重新报名。
  4. 到达开始时间：Carol 查看 D；Finn 再尝试报名。
  5. 到达结束时间：Carol 打开记录/评价入口。
  **预期：** 第 2 步 Bob 可以退出；释放名额后 David 不自动转正，Bob 不能重新报名。开始后 Carol 不能退出，Finn 不能新增报名。结束后不能报名、退出或签到；Carol 若未签到，不能评价，也不计入 Attended。边界采用“到达该时刻即关闭”，不是下一分钟才关闭。

| 活动状态 | 新报名/候补 | 取消报名 | 候补自动转正 | 新评价 |
| --- | --- | --- | --- | --- |
| 截止前且开始前 | 可以 | 普通参与者可以 | 可以，受报名黑名单限制 | 不可以 |
| 截止后、开始前 | 不可以 | 普通参与者可以 | 不可以 | 不可以 |
| 开始后、结束前 | 不可以 | 不可以 | 不可以 | 不可以 |
| 已结束且未取消 | 不可以 | 不可以 | 不可以 | 当前正式报名且有效签到者可以；组织者须确认到场 |
| 已取消 | 不可以 | 不再提供退出操作 | 不可以 | 不可以 |

### P1-07 取消活动

- [ ] **账号：Alice → Bob。** 创建 C、Bob 报名。Alice 在开始前进入 **Manage → Cancel event → Yes, cancel event**。Bob 查看该活动、History 和 Activity updates。
  **预期：** 活动及有效报名被取消；不能新增报名、签到、评价，History 保留取消状态；Bob 收到活动取消通知。取消活动不应算作已参加。

### P1-08 实际扫码与窗口【需要二维码画面输入】

- [ ] **账号：Alice → Bob；活动 S。** 前提：模拟器相机已能接收实际二维码画面，例如已配置的摄像头读取外部屏幕。当前 App 只有 **Scan QR code**，没有粘贴代码/手动签到按钮。
  **操作：** Alice 显示最新二维码，把该码画面保留在外部显示设备；在二维码签发后 **30 秒内**，切 Bob → S → Check-in & meeting → Scan QR code，允许相机并扫描。然后回看 Alice 的签到台。
  **预期：** Bob 显示 Checked in，普通参与者签到人数增加 1；重复扫描不能增加第二条当前签到。Carol 仍未签到。二维码过期后不能用旧图签到，应返回 Alice 获取新码。
  **窗口：** 开始前 30 分钟至开始后 30 分钟，端点包含；若更早结束或取消则立即关闭。
  **不能提供相机画面时：** 本项及 P1-09 标记“待补测”。同一模拟器不能直接扫描自己屏幕，也不能把另一台独立 Demo 的同名活动当作共享活动。预置签到记录只能帮助检查后续页面，不能证明本次实际扫码通过。

### P1-09 签到后取消，再报名不继承签到【依赖 P1-08】

- [ ] **账号：Bob → Alice → Bob；活动 S。** 在截止和开始之前，Bob 已成功签到后，退出签到页 → 活动详情 → Cancel → Yes, cancel → Join Event。Alice 检查名单；Bob 重进签到页，再使用 Alice 的新码完成一次签到。
  **预期：** 重新报名后 Bob 是正式报名、待签到，不能沿用上次 Checked in；Alice 看到对应的当前签到人数减少。再次有效扫码后才恢复 Checked in。S 留有空位，因此这里不会被候补转正抢走名额；若结果是候补，应先检查测试条件，不能把候补当成重报成功。

### P1-10 Attended 与记录入口

- [ ] **账号：Bob、Carol。** 在 S 结束前后分别查看 Profile 的 Attended 与 My Events → History，结束前从签到页 Records / My records 打开该活动记录。
  **预期：** 活动仍进行时，未签到的 Carol 可从记录页看到 **Open event check-in**（是否能签到仍受窗口限制）；已结束后转为未签到说明。若 Bob 在当前报名周期签到且保持正式报名，S 结束后 Attended 相对此前增加 1；Carol 未签到不增加。历史列表包含记录，不等于实际参加。若未做实际扫码，只验未签到路径，Bob 增量部分标记待补测。

## 4. 第二阶段：问答、评价互动、匿名、举报、黑名单

以下问答使用新活动 Q，避免旧活动已结束；评价互动用预置已结束活动。先做 P2-01～06，举报、删除与拉黑放后面。

### P2-01 新提问和未回复筛选【同时执行 P3-01～02】

- [ ] **账号：Bob → Alice。** Bob 在 Q → Questions & answers 输入 `验收Q1：集合需要带什么？` → Ask organizer。Alice 先看 All / Unanswered，再从 Unanswered 打开该问题 → Replies & actions → Reply → 输入 `验收A1：请带饮用水。` → Send reply。
  **预期：** 新问题进入未回复列表；Alice 回答后退出该列表，但当前 Conversation 不会突然关闭。主动返回列表后未回复数量减少，All 仍保留问题和最新组织者回答预览；可以点标签或左右滑动切换筛选。

### P2-02 二次回复与回复对象

- [ ] **账号：Bob → Alice。** Bob 打开 Q1，对 Alice 的 A1 点 Reply，发送 `验收Q2：现场有饮水机吗？`；Alice 对这条追问再次回复 `验收A2：入口处有。`。
  **预期：** 四条内容都保留；回复标明 Reply to 对应作者，次序和对象正确，界面以有限缩进展示多轮对话。All 的回答预览更新为 A2；Bob 的追问本身不应被当作“组织者已回答”。

### P2-03 提问权限与关闭状态

- [ ] **账号：Carol → Bob。** Carol 进入 Bob 的 Q1 对话；Bob 再查看一个已结束活动的 Questions & answers。
  **预期：** Carol 能阅读、举报、拉黑，但不能回复 Bob 的问答；问题作者与组织者才有继续问答的权限。已结束或取消活动显示 Questions closed，不能新提问或续答，旧对话可读。普通账号不能删除他人的问题/回复。

### P2-04 评价回复、点赞和取消点赞

- [ ] **账号：Carol → Bob。** Carol → Phase 2 - Reviews → 查看 Bob 的匿名评价 → Replies & actions，记下 Like 数，点 Like，再点 Unlike；随后发 `验收R1：同意这个建议。`。Bob 对 Carol 这条回复继续回复。
  **预期：** 点赞数先 +1 再恢复，不因重复进入而累加；支持回复评价、继续回复具体回复，目标正确。点赞入口针对评价正文；本阶段没有要求每条回复也有点赞按钮。本人回复可删除，别人的内容只能举报。点赞不改变综合星级，不产生互动通知。

### P2-05 同活动匿名名称稳定、不同用户不同名

- [ ] **账号：Alice → Bob → David → Alice。** 在 **Phase 3 - Review notifications**，Alice 记录 Bob 匿名公开评价的昵称 A、David 匿名私密评价的昵称 B；切 Bob 在自己的评价或 Carol 的公开评价下回复，再切 David 在自己的私密评价下回复，最后切 Alice 查看。
  **预期：** A 与 B 不同；Bob 在该活动的匿名评价及回复始终用 A，David 在其私密对话用 B。其他查看者不能看到匿名作者的真实姓名/个人头像；本人页面显示 You 属正常。重新打开仍一致。不同活动之间不要求沿用同一匿名名。组织者的新回复实名显示 Alice / Organizer。

### P2-06 私密评价和回复可见范围

- [ ] **账号：Carol → Alice → Bob。** Carol 在 **Phase 2 - Private feedback** 打开自己的评价并回复 `验收Private：仅组织者可见。`；Alice 查看并回复；Bob 打开同一活动的评价列表。
  **预期：** Carol/Alice 看得到私密评价和整段回复；Bob 看不到正文、回复或图片，也没有可打开该私密对话的入口。匿名和私密是不同选项：匿名公开评价仍对有权浏览的人可见。此活动的公开综合分仍为 No reviews。

### P2-07 删除自己的问题、回复和评价

- [ ] **账号：Bob → Alice → Bob。** Bob 在 Q 新发 `验收DELETE：删除占位测试`；Alice 回复一次；Bob 再追加一条自己的回复。Bob 先删除自己的回复，再删除问题正文，均点 Delete → Confirm。
  **预期：** 删除前有确认，放弃确认不改变内容；删除后显示 Content deleted 占位，Alice 的已有回复仍可读，不能再向已删除根问题续答。删除自己的评价使用 P4-05 验证；Alice 对 Bob 的内容没有直接 Delete 权限。

### P2-08 举报不会立即删除，维护者可以驳回

- [ ] **账号：Bob → Carol → Alice → Project maintainer (Demo) → Carol。** Bob 在 Q 新发 `验收REPORT-A：供驳回测试`；Carol → Replies & actions → Report → 填 `验收举报A` → Submit report。Alice 查看该问题和自己的 Privacy & reports；维护者进入 Privacy & reports，找到对应原因，点 Dismiss，填 Reason for decision → Save decision；Carol 查看 My reports。
  **预期：** 提交举报后内容仍在；重复提交同一未处理举报不新增重复记录。Alice 不能处理别人的举报。只有维护者看到 Maintainer report queue 和处理按钮；驳回后显示 DISMISSED 和处理理由，内容保留。

### P2-09 维护者移除内容

- [ ] **账号：Bob → Carol → Project maintainer (Demo) → Bob/Carol。** 另建 `验收REPORT-B：供移除测试` 问题及一条回复；Carol 举报，维护者找到该举报 → Remove content → 填理由 → Save decision。
  **预期：** 报告变成 REMOVED，正文变删除占位，已有回复不丢失、不能继续向已删除根内容回复；报告保留处理结果。使用新建的测试内容，不移除现有评分基准评价。

### P2-10 个人黑名单与恢复【同时观察 P3-06】

- [ ] **账号：Carol → Bob → Carol。** 先记录 Phase 2 - Reviews 的综合分；Carol 在 Bob 的匿名评价对话点 Block this author → Confirm，再查看评价列表、通知及自己的报名状态；切 Bob 查看与 Carol 的内容互动；最后切 Carol → Privacy & reports → 对应 PERSONAL 项 → Unblock。
  **预期：** 双方相关内容/直接回复和点赞受限制；匿名作者的拉黑项目不应泄露其真实身份。已有正式报名、历史签到不被取消，活动公开综合分仍为 4.5 / 2 reviews（前提是此前没有改评分）。解除后可重新看到未删除内容；原读状态保留，不新增一批重复通知。恢复后再做其他互动测试。

### P2-11 组织者报名黑名单

- [ ] **账号：Alice、Bob、David、Emma。** 使用新建 B，按下列步骤执行。
  1. Bob 正式报名，David 先候补，Emma 后候补。
  2. Alice → Privacy & reports → Registration → Choose user → David → Add restriction。
  3. David 尝试报名 Alice 的另一个有空位、仍开放的活动（可用 Q）；再回 B 查看原候补。
  4. Bob 退出 B，Alice 检查转正名单；然后把 B 的容量由 2 改成 3（Manage → Edit event → Maximum participants → Save changes）。
  5. Alice 解除 David 的 REGISTRATION 限制。David 查看 B，再主动报名 Q。
  6. Alice 对已正式报名 B 的 Emma 添加 Registration 限制，检查 Emma 现有名额仍在，再解除。
  **预期：** 限制覆盖 Alice 组织的所有活动，阻止 David 新报名/新候补；原有候补不会自动变正式。Bob 退出时跳过 David、让 Emma 转正；扩容也不能提升仍被限制的 David。解除限制本身不自动转正，David 可以重新主动报名其他开放活动。Emma 已有正式名额和历史不被清除。全部结束后解除本项新增限制。

## 5. 第三阶段：互动通知、My Events 红点和已读状态

为了判断增量，每次只发送一条测试消息，再切收件人检查。先记录其原有未读数 N，不假定一开始为 0。消息内容加 `验收` 前缀，按活动标题和时间定位。以下是 App 内通知，不是系统推送。

### P3-01 红点与进入页面的行为

- [ ] **账号：Alice。** 在 Bob 发 Q1 之前记下 N；Bob 发完 Q1 后切回 Alice，依次打开 My Events、Notifications、Interactions，但先不要点具体通知。
  **预期：** 新问题让 Alice 未读数 +1；My Events 红点、Notifications 数字、Interactions 的未读标记和通知行状态相互一致。只进入页面不会把通知批量设为已读。已有红点时，重点核对数字增量。

### P3-02 多轮问答通知与精确定位

- [ ] **账号：Alice ↔ Bob；配合 P2-01～02。** Alice 点 Q1 通知；Alice 发 A1 后切 Bob，点新通知；Bob 发 Q2 后切 Alice，再点新通知。
  **预期：** 每次只把所点的通知标为已读，未读数相应减 1；打开正确活动的 Conversation，并定位/突出对应问题或回复。一次动作即使同一人兼任多个接收角色，也只收到一条；发送者不收到自己的通知。Carol 不应因旁观 Bob 的问答收到这些回复通知。

### P3-03 新评价进入自己活动的 Interactions

- [ ] **账号：Alice → Bob → Alice。** 先记 Alice 未读数；Bob → **Phase 1 - Review access → Leave feedback**，选 4 星、填写 `验收新评价：通知应到组织者。`，打开 Post anonymously，Visible to 选 Public → Submit review；切 Alice 查看 Interactions。
  **预期：** Alice 收到该活动的新评价通知，未读 +1，点击进入对应评价；通知使用匿名昵称，不展示 Bob 的真实头像/姓名。Bob 不收到自发通知。该活动公开综合分为 4.0 / 1 review（若 Alice 已写组织者评价也不计入）。此步骤要求 Bob 尚未提交该活动评价；若已经提交，请把本项标为已执行，或使用另一场确有签到且尚未评价的活动。

### P3-04 评价回复的收件人和私密权限

- [ ] **账号：Alice → Bob → David → Alice → Carol。** Alice 在 Phase 3 - Review notifications 对 Bob 的公开评价发一条新回复；切 Bob 看通知并对该回复继续回复。再切 David 在自己的私密评价下直接回复自己；Alice 检查是否新增通知。随后 Alice 在 David 的私密评价下回复，切 David 点通知；Carol 检查收件箱。
  **预期：** Alice 回复 Bob 时通知 Bob；Bob 回复 Alice 时通知 Alice，去重且排除发送者。David 直接回复自己的根评价不通知无关用户，也不强制通知 Alice；Alice 实际回复 David 时，David 才收到该条回复通知。Carol 看不到私密内容或相关通知。点赞不新增通知。

### P3-05 全部已读、重启与账号隔离

- [ ] **账号：Alice → Bob → Alice。** 记录两人的未读数。Alice → Notifications → 右上角 **Mark all as read** 图标；检查两个分类、底部红点；切 Bob 检查，最后重开 App 再看 Alice。
  **预期：** Alice 当前账号所有通知变为已读、未读数 0、My Events 红点消失；Bob 原有未读不被清除。Alice 的已读状态在重启后保留。有新动作后红点才重新出现。不要把右上角图标误当成刷新。

### P3-06 已删除/被拉黑内容的通知

- [ ] **账号：Bob → Alice；并配合 P2-10。** Bob 在 Q 新发 `验收NOTICE-DELETE`，Alice 暂不点击；Bob 删除该问题；Alice 点击之前对应的通知。个人拉黑时另观察相关历史互动通知，解除后再看。
  **预期：** 删除通知仍可处理已读，跳转显示删除占位/不可用状态，不展示已删除正文或崩溃。个人拉黑期间相关互动通知被隐藏，解除后按原已读状态恢复；报名转正、活动取消等必要 Activity updates 不应一并被屏蔽。全部已读也会覆盖当前账号暂时隐藏的通知。

### P3-07 活动更新与互动分栏

- [ ] **账号：Alice → Bob。** Alice 在 Q → Manage → Organizer announcement 输入 `验收公告：请准时到场。` → Post announcement；Bob 查看两个分类。再结合 C 的取消通知、B 的候补转正通知检查。
  **预期：** 问题/评价/回复进入 Interactions；公告、活动取消、候补转正进入 Activity updates。两个分类共同影响总未读数和 My Events 红点，不出现同一通知被重复计数。

## 6. 第四阶段：综合评分与 History

P4-01～03 请在修改预置评价前先做。星级只采用有效公开参与者评价的 **Overall rating**，取等权平均并显示一位小数；匿名公开评价计入，组织者、私密、删除、草稿、无有效签到等不计入。

### P4-01 固定数据的平均分与三个历史入口

- [ ] **账号：Alice。** 找到 **Phase 4 - Public ratings**，依次查看 My Events → History、Profile 的 Activity history 预览（若该活动排在预览范围内）、Profile → More 完整列表、活动评价页。
  **预期：** 历史卡片标题右侧有星标、**4.0、2 reviews**；评价页的 Public participant reviews 使用相同总分。计算为 `(Bob 5 + Carol 3) / 2 = 4.0`。Alice 的 1 星、David 的私密 1 星、Emma 已删除的 2 星都不参与。个人 Your rating 如出现，应与活动综合分区分。

### P4-02 无有效公开评价

- [ ] **账号：Alice → David。** 打开 **Phase 4 - No public ratings** 的历史卡片与评价列表。
  **预期：** 虽然存在组织者评价和私密评价，综合评分仍为 **— / No reviews**，不显示 0.0。Alice 和 David 能看到的正文范围不同，但总分空状态相同。History 此阶段只显示综合星级和评价数，不要求点赞量。

### P4-03 跨账号、隐私与拉黑不改变公共评分

- [ ] **账号：Bob → David → Alice。** 查看 Phase 4 - Public ratings 的历史评分；David 能看到自己的私密反馈，Bob 看不到。再参考 P2-10 的拉黑前后对比。
  **预期：** 所有账号都看到同一个 4.0 / 2 reviews；不能按“我眼前能看到几条评论”重新计算。点赞/取消点赞也不能改变综合分或评价数。

### P4-04 草稿与分项评分不参与总分

- [ ] **账号：Emma。** 在 **Phase 4 - No public ratings** 打开 Leave feedback，选 Overall 5 星，More details 中选任意 Organization / Venue / Atmosphere 分数，填草稿文字；等 Draft saved 后返回，暂不提交。
  **预期：** 草稿保留，公开分仍为 No reviews，Alice 没有收到新评价通知。分项分数不是额外的评价条数，也不会单独参与 Overall 平均。此草稿保留到 P5-02 检查。

### P4-05 删除评价、即时重算与重新评价【会改变评分基准】

- [ ] **账号：Carol → Bob → Alice。** 先在 Phase 4 - Public ratings 的 Bob 5 星评价下，由 Carol 留一条测试回复并点赞，记录 Bob 匿名昵称。Bob → 该评价 → Replies & actions → Delete → Confirm；返回 History。随后 Bob 重新进入 Leave feedback，提交 **2 星公开匿名评价**，再由 Alice 检查。
  **预期：** 删除后立即变 **3.0 / 1 review**；旧评价显示删除占位，Carol 的旧回复仍可读，旧根不能继续回复。重评后为 **2.5 / 2 reviews**，不把旧 5 星一起累计；Bob 匿名昵称与之前一致。新评价不继承旧评价的点赞和回复，旧对话独立保留。各历史入口同步更新。
  **可选恢复：** 若希望继续使用原始评分基准，Bob 删除新 2 星评价，再提交 5 星公开匿名评价，应回到 4.0 / 2 reviews。这只能恢复当前总分，旧删除占位和测试通知仍会保留。

### P4-06 私密重评仍不计分【放在空状态检查之后】

- [ ] **账号：David → Alice。** David 在 **Phase 4 - No public ratings** 删除自己的私密评价，再提交任意星级的新评价，明确将 Visible to 设为 **Organizer only**；Alice 查看通知、正文和综合分。
  **预期：** Alice 可收到新私密评价通知并查看内容；公共评分始终 No reviews。不能因删除重评重新启用旧分数。Emma 未提交的草稿仍不计入。

## 7. 第五阶段：旧记录可读、持久化与完整流程回归

### P5-01 旧记录保留与重复打开

- [ ] **账号：Alice → Bob。** 记录 Phase 1 / Phase 2 的历史活动、旧问题的回答条数、匿名昵称、当前未读数；连续打开/关闭旧对话三次，并在账号之间切换。
  **预期：** 原活动、报名状态和签到状态没有凭空消失或新增；Phase 2 - Questions 的旧回答与追问仍可读，不重复生成相同回复；仅打开旧记录不补发一批新通知。现有匿名昵称、私密范围、删除占位和已读状态保持正确。

### P5-02 评价草稿隔离与重启

- [ ] **账号：Emma → Carol → Emma。** 使用 P4-04 的 Emma 草稿，记下文字、总体/分项评分、匿名开关和 Visible to；选择 Organizer only，等 Draft saved。切 Carol 查看同活动自己的页面，再切 Emma 打开另一活动确认不会混入该草稿；返回原草稿。将 App 从最近任务移除，再从桌面重新打开并返回此页。
  **预期：** 只有 Emma 在对应活动中读到这份草稿；其他账号/活动不出现它。重启后文字、评分及隐私选项保留，尚未发布，因此不计分、不通知。若添加了可用的测试照片，还应检查缩略图/预览保留并仅属于该草稿；照片步骤没有素材时单独记待测。

### P5-03 通知、举报、黑名单与业务状态持久化

- [ ] **账号：Alice、Bob、Carol、Project maintainer (Demo)。** 记录一条已读和一条未读通知、一个已处理举报及当前测试报名状态。Carol 可临时给 Finn 添加 PERSONAL 限制作为持久化样本。移除最近任务并重新打开，再分别切这些账号查看，最后 Carol 解除临时限制。
  **预期：** 账号切换后各自读状态、报名/签到、评分、举报结果都属于正确账号；未读标记不重置，举报处理结果不丢失，临时黑名单仍在。解除后没有被意外取消或自动恢复的报名。只点通知或切账号不会改变评分。

### P5-04 旧签到记录入口仍遵守新资格

- [ ] **账号：Carol → Bob。** 两人分别从 Profile → 三点 → Check-in and review records → Records / My records 查找 Phase 1 - Review access；同时与活动详情的 Leave feedback 路径对照。
  **预期：** Carol 仍不能通过旧记录入口提交评价；Bob 已签到，能查看自己提交的评价，或在尚未提交时写评价。两个入口对同一活动/账号的资格结论一致；待评价筛选不把未签到者误列为可评价。

### P5-05 问答旧功能与资料兴趣回归

- [ ] **账号：Alice / Bob。** 在 Q 复查 All/Unanswered 筛选、左右滑动、最新组织者回答预览；若问题作者有已保存的照片头像，点开查看预览；没有照片时只检查姓名首字母占位，照片预览另记待测。然后 Bob → Profile → Edit profile，记下原兴趣，改选一项 → Save，退出重进，再恢复原选择。
  **预期：** 筛选和多轮回复共存；删除某条最新组织者回答后，预览回退到仍有效的上一条，不能显示删除正文。资料兴趣选中态清楚且保存后保持；恢复后不影响报名、历史和通知。

### P5-06 页面与长内容检查

- [ ] **账号：Alice / Bob。** 在问答/回复中输入接近 100 字的测试内容，查看 Conversation、通知列表、历史卡片；打开评价 More details、Visible to 对话框和举报处理弹窗，呼出键盘并返回。
  **预期：** 长标题/文字合理换行或截断，星级和评价数不挤出卡片；按钮可滚动到达，键盘不遮住唯一提交/返回操作；超长、空白内容不能成功提交，失败不产生重复内容/通知。可选将系统字体放大一级再检查，结束后恢复。

### P5-07 最后一次整条流程

- [ ] **账号：Bob → Alice → Bob。** 在仍开放的 Q 上依次完成“打开活动 → 提问 → Alice 从通知打开并回复 → Bob 从通知打开 → 标记已读 → 回 Profile 查看历史 → 重启再查看”。最后切 Alice，停在 Profile → More 的历史列表，便于集中检查评分。
  **预期：** 各入口来回导航正确，不闪退、不串账号/活动；新回复能找到，已读状态保存，历史评分与最近的删除/重评结果一致。

## 8. 哪些项目需单独记录验证边界

| 项目 | 本次手动能检查什么 | 补充验证来源/条件 |
| --- | --- | --- |
| 摄像头扫码、30 秒过期、取消重报后再扫码 | 已配置二维码画面输入时执行 P1-08～09 | 只有单台模拟器且无相机输入时记待补测；已有关联报名周期、过期/重放的自动化测试 |
| 毫秒级截止、签到窗口端点、旧请求重放 | 手动检查可见状态和前后变化 | 精确边界由自动化覆盖，不要求手动卡到同一毫秒 |
| AB v1、共享 v2–v7、CE v1 的升级 | 旧记录可读、重启不重复/丢失 | 当前模拟器已是 v7，普通重启不能证明所有旧版本升级；第五阶段兼容测试覆盖这些格式 |
| 损坏文件、未来版本、冲突旧文件、中断写入 | 正常使用不应出现丢数据 | 用独立自动化测试验证，不在当前用户数据上破坏文件 |
| 未知可见范围、旧评价缺字段、旧签到缺报名轮次 | 当前 UI 的隐私与资格结果 | 兼容测试构造旧格式验证；不能从一个普通新评价推断全部兼容情况 |
| 评分数据未知时的 Unavailable | 若界面自然出现则检查不会伪装 0.0 / No reviews | 当前 Demo 有完整评分，无法仅靠断网稳定复现旧云缓存缺字段；相关状态由自动化覆盖 |
| Firebase 权限、双设备同步、生产系统 | 此清单验证本地 Demo 的交互 | 第五阶段已用本机 Firebase 模拟服务测试；生产环境未部署，Demo 切账号不等同于云端/实体双机验收 |

阶段规则见同目录 `PHASE1_ATTENDANCE.md` 至 `PHASE5_COMPATIBILITY.md`。自动化与数据保护证据见工作区外层 `verification/phase5-20261128/REPORT.md`、`verification.json`、`data-preservation.json`。本清单不包含提交、推送或部署操作。

## 9. 验收结果记录

每发现一个问题记一行；截图尽量包含活动标题、状态/时间和错误提示。

| 用例编号 | 结果：通过/失败/待测/待补测 | 当前账号 | 活动标题 | 实际操作与现象 | 截图/备注 |
| --- | --- | --- | --- | --- | --- |
| 例：P1-09 | 待补测 | Bob | 验收-S-… | 尚未配置模拟器相机二维码输入 | 已检查重报页面，实际扫码未执行 |
|  |  |  |  |  |  |
|  |  |  |  |  |  |
|  |  |  |  |  |  |

最终记录：通过 ___ 项；失败 ___ 项；待测 ___ 项；待补测 ___ 项。待测和待补测均不计作通过。
