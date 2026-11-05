# BIN-83：免费试用最后一天精确闹钟可行性（仅研究）

## 结论与范围

**技术上可考虑 `SCHEDULE_EXACT_ALARM` 用户授权路线；不建议 SubFlow 声明 `USE_EXACT_ALARM`。不能据此承诺 Play 一定通过，也不能断言“所有订阅跟踪应用都会被拒”或“只有日历／闹钟应用才能使用 SCHEDULE”。** Play 正文明确建议未覆盖的精确提醒用例评估 `SCHEDULE_EXACT_ALARM`，Android 文档也称其适用范围更广。[1–3]

这是可行性研究，不是调度方案批准或实现。用户已确认的产品约束是：只覆盖自动转付费且未处理的试用；全局 opt-in 默认关闭；“决定保留”只抑制新增取消检查，**保留原有试用提醒及试用零成本状态**，不能通过提前转付费实现。应用不代用户取消第三方服务。

## 已核实：权限与版本边界

以下讨论持久后台提醒所需的 **PendingIntent 版本** API，普通第三方应用，不依赖系统特权／电池白名单例外。

| 条件 | 已核实行为 |
| --- | --- |
| 设备 Android 12 / API 31 起，应用 target ≥31 | 精确闹钟引入“闹钟和提醒”特殊访问；`SCHEDULE_EXACT_ALARM` 需声明并确认访问资格。它不是普通运行时权限弹窗。[1,2,5] |
| 设备 Android 13 / API 33 起，target ≥33 | 可选择 `SCHEDULE_EXACT_ALARM` 或 `USE_EXACT_ALARM`。后者自动授予、用户不能撤销，但受严格 Play 用例审查；前者由用户授予，适用用例更广，且可撤销。[1,3] |
| **设备 Android 14 / API 34 及以上**，新安装、target ≥33、声明 SCHEDULE，且非豁免／预授予情形 | 默认拒绝。不能将此 Android 14 变更泛化成“Android 13 设备上所有新安装也默认拒绝”。[2] |
| 数据备份恢复至 Android 14 设备 | SCHEDULE 在新设备仍为拒绝；不能把业务设置恢复视为权限恢复。[2] |
| 已获 SCHEDULE 的现有应用随设备升级 Android 14 | 保留／预授予该资格；实际调度仍应检查，不能只看安装历史。[2] |

授权流程：先解释用户可见的定时需求，调用 `canScheduleExactAlarms()`；无资格且用户主动选择更准时提醒时，使用 `ACTION_REQUEST_SCHEDULE_EXACT_ALARM`（指定包名）进入系统设置；返回 `onResume()` 再检查。未获授权直接调用相关精确 API 可抛 `SecurityException`。[1,2]

**撤销不等于收到状态广播再降级：** 用户或系统撤销 SCHEDULE 会停止应用并取消未来精确闹钟。`ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED` 是**授予时**的广播，撤销时不发送；收到后仍需重新 `canScheduleExactAlarms()`，再按当前数据重建。启动／返回前台也应核对状态；不能指望撤销瞬间由接收器立即补一个降级闹钟。[1,5]

`OnAlarmListener` 不是持久后台免权限捷径：指南提到其权限例外，但 API 文档说明 listener 闹钟依赖组件／进程生命周期，Android 14 起离开生命周期会被丢弃；不适合几天后、进程已死的试用提醒。[1,5]

## 已核实：准时性、恢复与通知是不同问题

- **Doze：** `setExact()` 不能据“exact”名字推断绕过省电；需要空闲时触发的用户可见提醒，才考虑 `setExactAndAllowWhileIdle()`。官方措辞是 “nearly precise”，且 API 明确仍有频率限制、可能乱序、空闲时系统有更多调度自由度。API 文档举普通情况下约一分钟、深度空闲可能如十五分钟的间隔例子，**这不是延迟上界或准点 SLA**。同一时段多条试用应考虑合并，不能密集重试绕过配额。[1,5,8]
- **不是防扣费保证：** 关机无法送达；重启清除 AlarmManager 闹钟，需持久化业务状态，收到 `BOOT_COMPLETED` 后检查权限并重建（需 `RECEIVE_BOOT_COMPLETED`，且应用至少被用户启动过）。精确闹钟不保证用户看见通知或在第三方扣款前完成取消。[1,5]
- **日期／时区：** `RTC_WAKEUP` 使用墙上时钟 UTC 毫秒，不会自动理解“当前本地最后一天上午”。官方提供 `ACTION_TIME_CHANGED`（实际 action 为 `android.intent.action.TIME_SET`）及 `ACTION_TIMEZONE_CHANGED`。**建议**定义本地时间、DST 和跨时区语义，并在这些事件、编辑日期及启动时重新计算未来触发点；这不是说所有时间变更都会清除闹钟。[1,5,9]
- **强制停止：** 不是普通后台进程被回收／划掉最近任务。不能承诺强停后继续提醒；Android 15 明确取消 stopped 应用所有 PendingIntent，直到用户直接／间接交互解除 stopped；解除时发送 `BOOT_COMPLETED` 提供重建机会。重启或 WorkManager 也不是绕过用户强停的保证。[6]
- **通知独立：** Android 13+ 的普通提醒另需 `POST_NOTIFICATIONS`；新安装默认关闭。精确调度授权不会授予通知权限，也不会覆盖用户关闭应用通知／通知渠道的选择。提醒状态界面应分别呈现通知能力与精确调度能力。[7]
- **全屏独立：** `USE_FULL_SCREEN_INTENT` 是另一权限／Play 条款；target ≥34 的自动授予限核心闹钟／通话情形，其他用途需用户许可并符合其他政策。这不等于 SCHEDULE 也限这两类。**建议** BIN-83 只用普通通知，不申请全屏意图、不伪装闹钟应用。[3]

## 当前仓库核对（只读）

- `android/app/build.gradle.kts` 当前工作树：minSdk 26、targetSdk／compileSdk 36；因此需要按实际运行系统版本处理上述边界。
- `android/app/src/main/AndroidManifest.xml` 已声明通知、开机广播权限，未声明两种 exact-alarm 权限或全屏权限；仅有开机权限不意味着已有自建精确闹钟恢复接收器。
- `data/notifications/RenewalNotificationWorker.kt`：每日 WorkManager，首次约本地 09:00、要求电量不低；`MainActivity.kt` 启动时安排周期工作并补扫。它不是每天 09:00 准时保证。
- `domain/util/Trials.kt`：独立提前提醒默认 7／3／1 天；按试用 ID＋结束日期保存已处理档位，首次看到时静默消化已到档位；`remaining < 0` 不补发。现有提醒同时支持自动转付费和不转付费试用。新增取消检查必须独立筛选、独立状态／去重，不能让“决定保留”令 `isTrialPending` 失效而顺带停掉原提醒。

## 建议（尚未批准的后续路线）

1. **先确认“最后一天”定义和必要精度。** 现有模型以日期为主，不掌握服务商真实扣款时刻／时区。选“结束日前一天”还是“结束日期当天”、上午几点，以及编辑／延长后如何重新开启检查，仍需产品确认；不能声称精确 API 能补足这些业务信息。
2. 如果需求只是“当日尽量提醒”，优先评估一次性非精确 AlarmManager（需 Doze 时用 `setAndAllowWhileIdle()`）＋保留每日扫描／启动补扫，无需 exact 特殊访问。`setWindow()` 或 `set()` 可用于可容忍窗口且不要求穿透 Doze 的情况。不要宣传“必在一小时内”：指南的一小时描述带有**无省电限制**前提。[1,2]
3. 若产品确实确认“用户选定时刻”的必要性，再评估**可选 SCHEDULE 增强模式**：全局默认关闭；用户开启后再情境式说明及请求，不强迫授权。已授权才安排一次性 `setExactAndAllowWhileIdle()`；拒绝／返回仍无资格／恢复后丢失资格则走非精确路线并明确可能延迟。不要为此申请电池优化豁免或把 `setAlarmClock()` 当政策绕路。
4. WorkManager 继续作尽力补扫，不当作精确计时替代：周期运行时刻由约束与系统优化决定，可能延迟／跳过；`setInitialDelay()` 是最短等待，不是预约时刻；expedited 受配额和负载限制且不能预约将来精确执行。[4] 撤销可能杀进程，后续实现应考虑预先保留持久补扫兜底，而不是依赖撤销广播。
5. 精确／降级／补扫三条路径共用业务资格与去重判断，接收时重读状态，忽略已取消、已决定保留、日期已改或开关已关的**取消检查**；不改既有试用提醒与零成本统计。恢复后只安排仍有效未来事件；迟到补发边界待确认，避免错把已扣费后的提醒写成“尚可免费取消”。

**仍需 Play 核实／实机验证：** 官方公开条款没有对 SubFlow 作个案批准。SCHEDULE 的实际必要性、商店描述、授权文案及审核结论应在拟定具体方案后向 Play 支持／Console 审核核实；不能把 USE 的严格审查直接套成 SCHEDULE 的同等类别禁令。未进行 Play 提交或真机可靠性测试。未来测试至少覆盖 API 31／33／34+ 新安装、恢复、升级、拒绝／撤销／再授权、通知拒绝及渠道关闭、Doze、多试用同刻、重启、强停后恢复、时间／时区／DST、编辑结束日和“决定保留”。**本轮停在研究，不改产品代码或配置。**

## 一手证据（精确 URL 与短摘录）

本轮四个指定主来源均由 `fetch_content` 成功提取全文；补充 Android 官方页面亦成功获取。时间广播常量另用 Python urllib 直接读取官方 HTML 核对。**未发生来源抓取失败**；不等同于获得审核意见。以下保留英文原句，判断以完整正文为准，不只依据政策摘要。

1. Android — Schedule alarms：<https://developer.android.com/develop/background-work/services/alarms/schedule>
   - SCHEDULE：“Granted by the user”；“Broader set of use cases”。
   - exact idle：“Invoke an alarm at a nearly precise time in the future, even if battery-saving measures are in effect.”
   - 撤销：“your app stops, and all future exact alarms are canceled.”
   - 重启：“By default, all alarms are canceled when a device shuts down.”
2. Android 14 — Schedule exact alarms are denied by default：<https://developer.android.com/about/versions/14/changes/schedule-exact-alarms>
   - “If a device is running Android 14 or higher” 下列明 target ≥33 新安装等条件。
   - “If the user transfers app data to a device running Android 14 through a backup-and-restore operation, the permission will still be denied.”
   - “If an existing app already has this permission, it'll be pre-granted when the device upgrades to Android 14.”
3. Google Play — Permissions and APIs that Access Sensitive Information（Exact Alarm Permission／Full-Screen Intent Permission 正文）：<https://support.google.com/googleplay/android-developer/answer/13161072>；本会话独立复核的当前页面：<https://support.google.com/googleplay/android-developer/answer/16558241?hl=en>
   - “USE_EXACT_ALARM is a restricted permission”；不符合可接受用例者 “will be disallowed from publishing on Google Play”。列举 alarm/timer、calendar event notifications。
   - **关键边界：** “If you have a use case for exact alarm functionality that’s not covered above, you should evaluate if using SCHEDULE_EXACT_ALARM as an alternative is an option.”
   - 全屏：“Apps will only be automatically granted ...” 核心功能类别为 “setting an alarm”／“receiving phone or video calls”；其他情况 “must request permission from the user”。
4. Android — Define work requests：<https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started/define-work>
   - “The exact time that the worker is going to be executed depends on the constraints ... and on the optimizations performed by the system.”
   - 初始延迟：“a minimum initial delay”；expedited：“can't be scheduled for later execution”。
5. Android — AlarmManager API：<https://developer.android.com/reference/android/app/AlarmManager>
   - 状态广播：“This broadcast will not be sent when the user revokes the permission.”
   - exact idle：“when in low-power idle modes this duration may be significantly longer, such as 15 minutes”；“the OS will allow itself more flexibility for scheduling these alarms than regular exact alarms”。
   - listener：“If you need to receive the alarm even after that, use the PendingIntent based APIs.”
6. Android 15 — Changes to package stopped state：<https://developer.android.com/about/versions/15/behavior-changes-all#enhanced-stop-states>
   - “the system also cancels all pending intents when the app enters the stopped state on a device running Android 15.”
7. Android — Notification runtime permission：<https://developer.android.com/develop/ui/views/notifications/notification-permission>
   - “Android 13 (API level 33) and higher supports a runtime permission ... POST_NOTIFICATIONS”；新安装 “notifications are off by default”。
8. Android — Optimize for Doze and App Standby：<https://developer.android.com/training/monitoring-device-state/doze-standby>
   - “setAndAllowWhileIdle() and setExactAndAllowWhileIdle() ... fire even if the device is in Doze.”
9. Android — Intent 常量：<https://developer.android.com/reference/android/content/Intent#ACTION_TIME_CHANGED>；<https://developer.android.com/reference/android/content/Intent#ACTION_TIMEZONE_CHANGED>
   - “Broadcast Action: The time was set.”／“Broadcast Action: The timezone has changed.”
