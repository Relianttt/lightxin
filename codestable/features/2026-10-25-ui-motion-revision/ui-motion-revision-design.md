---
doc_type: feature-design
feature: 2026-10-25-ui-motion-revision
status: approved
summary: 全局底色与组件统一到 Claude token、登录失效呈现收敛、劳动教育/素质学分/考试成绩改版与快照缓存、签到结果弹层、查寝分区、跑步轨迹显示与动效 v3
tags: [ui, motion, labor, credit, exam, checkin, running, cache]
---

# 实现报告：UI 统一、模块修订与动效 v3（2026-10-25）

> 读者：负责实现的工程代理。本报告基于 2026-10-25 的 `main`（HEAD `9c025d1`）代码与 `tmp/应用截图/` 下的截图整理。
> 所有"现状"都附了文件与行号；行号以该提交为准，改动前请先打开文件核对。
> 报告里的数值（时长、尺寸、颜色）是**定稿参数**，不要自行"优化"；确需偏离时先停下来问用户。

---

## 0. 开工前必读

### 0.1 必须遵守的项目规则
- 仓库根目录 `AGENTS.md` 全文，尤其是 **Hard Constraints**：
  - 禁止引入任何地图 / 定位 SDK（本报告的跑步轨迹**只画形状**，不拉瓦片、不联网）。
  - 所有外部请求必须与原 App 抓包协议一致；本报告里涉及协议的只有 T1 的 `pageSize`，其余不改协议字段。
  - 跑步数据 RSA 规则、查寝 7 个身份字段规则不得触碰。
- `.agents/notes/` 中与本次相关的决策（本报告会修订其中几篇，见第 14 节）：
  - `2026-10-18-decision-claude-ui-language.md`（新旧两套 token；§11 计划全局迁移）
  - `2026-10-18-decision-lx-motion-tokens.md`、`2026-10-18-decision-gentle-tab-paging.md`（动效）
  - `2026-10-17-decision-login-expiry-surfaces.md`、`2026-10-18-decision-relogin-without-dialog.md`（登录失效）
  - `2026-10-21-decision-offline-schedule-ai-prewarm.md`（Room 缓存与账号代次隔离的既有做法）
  - `2026-10-18-decision-repair-mobile-family-auth.md`（报修鉴权）
- `.agents/verify.md`（验证对照表）。

### 0.2 构建命令（Linux）
```bash
JAVA_HOME=/opt/android-studio/jbr ./gradlew -Dorg.gradle.java.home=/opt/android-studio/jbr <任务>
```
常用任务：`testDebugUnitTest`、`lintDebug`、`assembleDebug`、`assembleDebugAndroidTest`、`connectedDebugAndroidTest`（需设备）、`assembleRelease`（动效手感必须在 release 包上看）。

### 0.3 提交规则
- 每个阶段（第 1 节的 P1…P7）至少一个提交，一个提交只做一件事。
- 中文主题 + 正文；正文只写"改了什么 + 跑了什么命令 + 结果"，简短条目，不写长篇理由。
- 多行提交信息用 `git commit -F <文件>`。
- 不提交 `local.properties`、HAR、token、设备数据、调试日志代码。

### 0.4 已与用户确认的决策（不要再讨论）
1. **底色与组件统一到新 UI（Claude token 层）**。加载态：首屏用骨架屏；按钮 / 局部动作用小转圈；加载更多用文字"正在加载…"。
2. **登录失效**：首页横幅底色与其他卡片一致；模块内的恢复卡片精简文案、统一结构。
3. **动效 v3**：推翻旧 motion note 的"单一曲线 / 不错峰 / 共享元素只做 bounds / 不做圆角过渡"。
   - **转场动画不做 overshoot**；
   - 有固定时长的动画，除计数动画外不超过 450ms；
   - 手势驱动的物理动画（惯性滑动、滑到边缘、弹层松手落定、下拉回位）不受时长上限约束；
   - **滑到边缘保留 Android 系统的拉伸效果**，不做 iOS 橡皮筋。
4. **跑步页只画轨迹形状**，不做地图底图。
5. **查寝不加历史记录卡片**，改为列表分区。
6. **签到结果从 Snackbar 改为底部弹层**。
7. **考试成绩**：接口只返回每门课绩点（`hdjd`），学期汇总是本地算的；汇总移到顶部并修正算法。

---

## 1. 阶段与顺序

| 阶段 | 内容 | 依赖 |
|---|---|---|
| P1 | T1 劳动教育分页、T2 滑到底回弹延迟（先诊断）、T3 报修接入登录失效 | 无 |
| P2 | T4 全局底色与加载态统一；T12-A 动效 token、页面转场、分页吸附、按压反馈 | 无 |
| P3 | T5 登录失效 UI 统一 | P2 |
| P4 | T6 劳动教育 / 素质学分 / 考试成绩改版；T7 劳动教育、素质学分持久化 | P2、P3 |
| P5 | T8 模板页按钮、T9 签到结果底部弹层、T10 查寝列表分区 | P2 |
| P6 | T11 跑步轨迹显示 | P2 |
| P7 | T12-B 列表编排、计数动画、触感、预测性返回；T12-C 容器展开（可选） | P2–P6 |

T2 若诊断结论指向 `HorizontalPager`，**先停下来把结论报给用户再改**（跟手分页是用户拍板的交互）。

---

## T1 劳动教育：拉到底转圈后没有新数据

### 现状
- `feature/labor/data/LaborApi.kt`：`queryTimesDetailsPage.do` 发 `pageNo`、`pageSize`，外加 `userCode`、`xh`、`accessToken` 三个 `@Field`。
- `LaborRepository.kt:56`：`getActivities(page, pageSize = 10)`，**丢弃了响应里的 `total`**（`LaborResponse.kt:29` 有这个字段）。
- `LaborViewModel.kt:79`：首屏 `hasMore = list.size >= 10`。
- `LaborViewModel.kt:107-113`：只按 `id` 去重，`hasMore = newRecords.isNotEmpty() && list.size >= 10`。
- `LaborViewModel.kt:117-119`：加载更多失败时只把 `isLoadingMore` 置回 false，用户看不到错误。
- 原 App 抓包（`docs/接口分析/跑步接口深度分析报告.md:1902-1915`）：`pageNo=1`、`pageSize=100`，返回 `total=28`。文档还写明"同一活动可能在不同分类下重复出现"。
- **附带观察（本次不改）**：`core/network/AuthInterceptor.kt:146-152` 已经为 `ldjy` 追加了 `userCode / xh / userName / accessToken`，而 `LaborApi` 又以 `@Field` 传了 `userCode / xh / accessToken`，同名字段会被发送两次。文档的请求体里还有 `userType`（不带下划线），我们只发 `_userType`。工时总览目前能正常返回，所以本次不动；在 note 里记一条"待抓包确认"。

### 根因推断（未在真机验证）
`pageSize` 与原 App 不一致（10 vs 100），而且是否还有下一页只靠"本页去重后有没有新记录"判断。下面任一情况都会出现"转圈一次后停住"：
- 服务端不认 `pageNo`，每次都返回同一批；
- 同一个 `id` 在不同 `type` 下重复。

### 步骤
1. **先诊断**：在 `LaborViewModel` 的首屏和 `loadMore` 成功分支临时加一行 `Log.d("LaborPaging", ...)`，输出 `pageNo`、`rows.size`、`total`，以及首尾两条记录的 `type:id`。**不要打印 token**。在设备上拉到底，记下日志，写进提交正文，然后删除日志代码。
2. **修复**（不论诊断结论如何都做）：
   - `LaborRepository.getActivities(page: Int, pageSize: Int = 100)`，默认值改成 100，与原 App 一致。
   - 返回值改为 `Result<ActivityPage>`，在 `feature/labor/domain` 新增 `data class ActivityPage(val records: List<ActivityRecord>, val total: Int?)`，`total` 取自 `response.total`。
   - 新增去重键：`ActivityRecord` 加计算属性 `val key: String get() = "$type:$id"`。去重、`LazyColumn` 的 key、详情选中态都改用 `key`。
   - `hasMore` 的判定写成纯函数（放在 `LaborViewModel.kt` 文件内，`internal`，便于测试）：
     ```kotlin
     internal fun laborHasMore(loaded: Int, pageSize: Int, lastPageSize: Int, newCount: Int, total: Int?): Boolean =
         when {
             newCount == 0 -> false                 // 服务端重复返回同一页时立刻停
             total != null -> loaded < total
             else -> lastPageSize >= pageSize
         }
     ```
   - `LaborUiState` 新增 `loadMoreError: String? = null`。失败时写入该字段；底部显示"加载失败，点此重试"（组件见 T4 的 `LxLoadMoreFooter`），点击后清空错误并重新 `loadMore()`。
   - 活动列表按 `createDate` 倒序排列（格式 `yyyy-MM-dd HH:mm:ss`；解析失败的排在最后）。服务端返回的顺序是乱的（截图里 2025-03、2025-04-30、2025-04-02、2026-03 混在一起）。T6 的按月分组依赖这个排序。
3. 修复后在设备上确认：列表条数与 `total` 一致，底部显示"没有更多了"。

### 测试
新增 `app/src/test/java/com/lightxin/feature/labor/LaborPagingTest.kt`（纯 JVM）：
- `laborHasMore`：`total=28` 一页拉完时返回 false；`total=null` 且整页满时返回 true；`newCount=0` 时返回 false。
- 同一 `id` 不同 `type` 的两条记录都保留。

### 不要做
- 不改 `AuthInterceptor` 对 `ldjy` 的字段注入；
- 不改 `LaborApi` 除 `pageSize` 以外的字段。

---

## T2 请假 / 报修 / 学工在线：滚到底后回弹延迟出现

### 现状
- 三个页面都用 `core/designsystem/component/LxTabPager.kt`（`HorizontalPager`），各页里是纵向 `LazyColumn`。
- 请假（`LeaveListScreen.kt:126`）和报修（`RepairListScreen.kt:129`）外面还包了 `PullToRefreshBox`；**查寝（`CheckinListScreen.kt:117-140`）没有包**。
- 请假和查寝有"加载更多"底部项；**报修没有分页**。
- 所以三页唯一的共同点是 `LxTabPager`。

### 步骤（只诊断，不先改）
1. 用 release 包，在设备上录屏（60fps 以上）。每个场景各做 3 次快速上滑甩到底：
   - A：请假"全部"页；
   - B：报修"全部"页（需要有数据）；
   - C：查寝页；
   - 对照组 D：路线模板列表（`RouteTemplateListScreen`，新 UI、没有 Pager）。
2. 实验 1：把 `LeaveListScreen` 的 `LxTabPager { pageIndex -> ... }` 临时替换成 `Box(Modifier.padding(padding).fillMaxSize()) { /* 只渲染 pageIndex = 1 的内容 */ }`，重复 A。
3. 实验 2：恢复 Pager，把请假的 `PullToRefreshBox` 临时去掉，重复 A。
4. 实验 3：恢复 Pager 和下拉刷新，把 `if (uiState.isLoadingMore) item(key = "more")` 临时注释掉，重复 A。
5. 把四组结果（有延迟 / 无延迟）和录屏文件名整理成表格**报给用户**。实验代码全部回滚，不提交。
6. 只有当原因是"加载更多底部项让内容高度变化"（实验 3 消除了延迟）时才可以直接修。修法：`hasMore == true` 时底部项一直存在且高度固定（48dp，显示"正在加载…"或留空），避免内容高度来回变化。其他原因都先汇报。

---

## T3 报修接入登录失效

### 现状
- `RepairRepository.kt:233-245`：只把 HTTP 401 映射成一句文字；服务端在 200 响应体里表达的鉴权失败没有识别，界面只显示"获取报修列表失败"（截图 `S61022-21461596`）。
- 加载失败时 tab 上仍显示"进行中 0 / 全部 0"，看起来像"确实没有报修"。

### 步骤
1. **探测失效形态**：仿照劳动教育 note 的做法，用 `curl` 调用 `bxjlSjd/getWdbxListForSjd.do`。字段按 `AuthInterceptor.kt:99-120` 的报修分支构造，`accessToken` 填一个无效值，`Origin` / `Referer` / `X-Requested-With` 照抄。记录完整响应体（脱敏后）。
2. 根据响应写判定函数。放在 `RepairRepository.kt` 文件底部，`internal`，参照 `LaborRepository.kt:130` 的写法：
   ```kotlin
   internal fun isRepairAuthRejection(root: JsonObject): Boolean = /* 按实测形态判断 */
   internal fun repairAuthRejected(action: String, root: JsonObject): AuthExpiredException =
       AuthExpiredException(technicalDetail = "REPAIR_REJECTED\n操作：$action\n阶段：repair 身份校验\n响应：${/* code/msg 原文 */""}")
   ```
   在 `ensureSuccess` 之前判断，命中就 `throw`；`runCatchingApi` 不要把 `AuthExpiredException` 再包成普通 `Exception`，要原样透传（改成 `catch (e: AuthExpiredException) { Result.failure(e) }` 放在通用 catch 之前）。
3. **只有判定条件在实测中确认无歧义时才归为"登录已失效"**。如果和普通业务错误分不开，就归为"可能是登录问题"（T5 的 `Retry` 主操作版本），与查寝一致。
4. 报修列表 ViewModel（`RepairViewModels.kt`）的 UI 状态新增 `authExpiredDetail: String?`。`RepairListScreen` 在该字段非空时渲染 T5 的统一卡片，重新登录走与劳动教育相同的 `onSessionRelogin` 路径（参考 `NavGraph` 中劳动教育页的 `onRelogin` 接线）。
5. 加载失败（任何错误）且列表为空时，`LxTabItem` 的 `count` 传 `null`，不显示 0。
6. 新增 `RepairAuthRejectionTest.kt`：分别用实测的拒绝响应和一条普通失败响应作为输入，断言判定结果。

> 请假（`ywlz`）同样没接登录失效，**不在本次范围**；如果探测顺手，可以在 note 里记一笔响应形态。

---

## T4 全局底色与加载态统一

### 目标
所有页面都用新 token（`core/designsystem/theme/ClaudeTokens.kt`）：背景 `#FAF9F5`、卡片 `#FFFFFF` 加 0.5dp 细边、强调色 `#C6613F`，深色模式对应 Claude 的深色值。旧页面不逐页重写，靠"旧 token 改成别名"一次切换。

### 步骤
1. `ClaudeTokens.kt`：把第 13–44 行的 `private val XxxLight / XxxDark` 改成 `internal val`，供 `Theme.kt` 使用。
2. `Color.kt` 第 99–131 行的旧语义 token **改为指向新 token**（保留名字，调用点不动）：

   | 旧 token | 改为 |
   |---|---|
   | `LxParchment` | `LxCanvas` |
   | `LxCream` | `LxTint` |
   | `LxCard` | `LxSurface` |
   | `LxSand` | `LxTintStrong` |
   | `LxSandDeep` | `LxHairline` |
   | `LxCardBorder` | `LxHairline` |
   | `LxInk` | `LxText` |
   | `LxInkSoft` | `LxTextSecondary` |
   | `LxInkMuted` | `LxTextMuted` |
   | `LxInkFaint` | `LxTextFaint` |
   | `LxInkGhost` | `LxTextFaint.copy(alpha = 0.6f)` |
   | `LxTerra` | `LxAccent` |
   | `LxTerraSoft` | `LxAccent.copy(alpha = 0.12f)` |
   | `LxTerraGlow` | `LxAccent.copy(alpha = 0.10f)` |

   **不改**：`LxSage / LxAmber / LxPlum / LxSlate / LxRose` 及其 Soft 版本、`LxCategoryColors`（课表和图表配色）、所有 `*Raw` 常量（`LxCategoryColorsLight` 在用）。
3. `Theme.kt` 的 `LightColorScheme` / `DarkColorScheme` 改用新 token 的 raw 值：
   - 基础色：
     - `primary = AccentLight`、`onPrimary = Color(0xFFFFFDF9)`；
     - `primaryContainer = AccentLight.copy(alpha = 0.12f)`、`onPrimaryContainer = AccentTextLight`；
     - `background = CanvasLight`、`onBackground = TextLight`；
     - `surface = SurfaceLight`、`onSurface = TextLight`；
     - `surfaceVariant = TintStrongLight`、`onSurfaceVariant = TextMutedLight`；
     - `outline = HairlineLight`、`outlineVariant = HairlineLight`；
     - `error = DangerLight`、`onError = Color.White`、`errorContainer = DangerSurfaceLight`、`onErrorContainer = DangerOnSurfaceLight`。
   - 必须显式设置 surface 容器色，避免 M3 默认色调混进来：
     - `surfaceContainerLowest = SurfaceLight`
     - `surfaceContainerLow = SurfaceLight`
     - `surfaceContainer = CanvasLight`
     - `surfaceContainerHigh = TintLight`
     - `surfaceContainerHighest = TintStrongLight`
     - `surfaceBright = SurfaceLight`
     - `surfaceDim = TintStrongLight`
   - `secondary` / `tertiary` 保持现状（Sage / Amber）。
   - 深色模式用对应的 `*Dark` 值，其中 `onPrimary = Color(0xFF1F1E1D)`（与 `LxOnAccent` 深色一致）。
4. `LxCard.kt`：两个 `Card` 分支都加 `border = BorderStroke(0.5.dp, LxHairline)`。白底卡片放在 `#FAF9F5` 上对比太弱，必须靠细边区分。圆角保持 16dp。
5. `HomeScreen.kt:78`：`NavigationBar(containerColor = LxParchment.copy(alpha = 0.92f))` 保持写法不变（别名会自动切成新底色）。
6. `res/values/colors.xml`：`splash_background` 改为 `#FFFAF9F5`；`res/values-night/colors.xml`：改为 `#FF262624`。两处注释同步改成"与 LxCanvas 一致"。
7. **加载态统一**：
   - `LxLoadingState.kt` 里的 `LxLoading(modifier)` 改为渲染骨架：`LxSkeletonList(count = 5, modifier = modifier)`，保留 `fillMaxSize` 的外层 `Box`，顶部对齐。所有首屏 `LxLoading()` 调用点因此一起切换。
   - 弹层里的加载（劳动教育、素质学分的详情弹层，T6 会重写）用 `LxSkeletonBlock` 画 3 行。
   - `LxProgressIndicator` **保留**，只用于按钮内、恢复卡片的"正在重试"、登录按钮、扫码等局部场景。
   - 新增 `LxLoadMoreFooter`（放 `ClaudeComponents.kt`，`LxSkeletonList` 之后）：
     ```kotlin
     @Composable
     fun LxLoadMoreFooter(loading: Boolean, hasMore: Boolean, error: String?, onRetry: () -> Unit, showEnd: Boolean)
     ```
     渲染规则：
     - `error != null`：显示文字"加载失败，点此重试"（12sp，`LxTextMuted`，整行可点，触发 `onRetry`）；
     - `loading`：显示"正在加载…"（12sp，`LxTextMuted`）；
     - `!hasMore && showEnd`：显示"没有更多了"（11.5sp，`LxTextFaint`）；
     - 其余情况不渲染。
     - 内边距与 `LeaveListScreen.kt:178-197` 现有写法一致（加载 vertical 16dp，结束 24dp）。
     - 请假、查寝、劳动教育的底部项都改用它，删除 `CheckinListScreen.kt:181-190`、`LaborSummaryScreen.kt:181-195` 里的 `CircularProgressIndicator`。
8. 下拉刷新指示器：`PullToRefreshBox` 用 `indicator = { PullToRefreshDefaults.Indicator(state, isRefreshing, Modifier.align(Alignment.TopCenter), containerColor = LxSurface, color = LxAccent) }`。T12-B 会把 state 抽成公共函数，这里先统一颜色。

### 验收
- 截图对比（浅色和深色各一套）：首页、课表、我的、跑步首页、AI 课堂首页、登录页、引导页、更多功能、关于。重点检查：卡片在新底色上是否清晰、课表色块是否未变、文字层级是否正常。
- `connectedDebugAndroidTest` 中的 `RecoveryUiInstrumentedTest` 深色模式用例仍然通过（T5 会改它，这一阶段先保证不坏）。

### 不要做
- 不重写旧页面的布局；
- 不改 Typography；
- 不删除旧 token 名字（调用点太多，别名过渡即可）。

---

## T5 登录失效 UI 统一

### 现状
- 首页"登录状态已失效"横幅：`feature/home/ui/MainSessionExpiredBanner.kt`，`LxSand` 底、16dp 圆角、两行正文、右下实心按钮（截图 `S61022-21454676`）。
- 首页"AI 课堂被顶号"横幅：`HomeDashboard.kt:255-287`，琥珀色底、单行，和上面那条风格完全不同。
- 模块内恢复卡片：`core/designsystem/component/ServiceErrorCard.kt`，76dp 图标圆、标题、两段正文、三行操作列表加脚注，整卡 `LxSand` 底（截图 `S61022-21455439`、`S61022-21462429`）。
- 调用点：
  - `LaborSummaryScreen.kt:78`；
  - `CheckinListScreen.kt:154`；
  - `FifMappingErrorCard.kt:34`，被 AI 课堂三个页面使用。
- 测试 `app/src/androidTest/.../RecoveryUiInstrumentedTest.kt` 依赖以下文案：`暂时无法连接 AI 课堂`、`重新尝试连接`、`正在重新连接…`、`重新登录`、`查看错误详情`、`关闭`、`登录状态已失效`；还要求重试中时"重新登录"不可点。

### 5.1 首页横幅 → `LxSessionBanner`
新建 `core/designsystem/component/LxSessionBanner.kt`：
```kotlin
@Composable
fun LxSessionBanner(
    title: String,
    body: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
    actionText: String = "重新登录",
)
```
- 容器：`LxCard`（与首页其他卡片完全一致：白底、0.5dp 细边、16dp 圆角），内边距 horizontal 16dp、vertical 14dp。
- 布局：一个 `Row`，垂直居中：
  - 左侧 `Icons.Outlined.Info`，18dp，`LxAccent`；
  - 间距 12dp；
  - 中间 `Column(weight 1f)`：标题 15sp SemiBold `LxText`，正文 13sp `LxTextMuted`、上间距 2dp、`maxLines = 2`；
  - 间距 8dp；
  - 右侧 `LxTextAction(actionText, onAction)`。
- **不用实心按钮，不用有色底。**

使用：
- `MainSessionExpiredBanner(onRelogin, modifier)` 保留函数名（测试在用），函数体改为调用 `LxSessionBanner(title = "登录状态已失效", body = "部分功能已失效，重新登录后恢复", onAction = onRelogin)`。
- `HomeDashboard.kt` 的 `AiClassBlockedBanner` 改为调用 `LxSessionBanner(title = "AI 课堂已在其他设备登录", body = "AI 课堂签到暂不可用", onAction = onRelogin)`，删除原来的私有实现。
- 两处在 `HomeDashboard.kt:141-155` 的上间距保持 16dp 不变。

### 5.2 模块内卡片 → 重写 `ServiceErrorCard`
**保留函数名和文件**（调用点少改），签名精简为：
```kotlin
enum class RecoveryPrimary { Retry, Relogin }

@Composable
fun ServiceErrorCard(
    title: String,
    message: String,
    technicalDetail: String,
    isRetrying: Boolean,
    onRetry: () -> Unit,
    onRelogin: () -> Unit,
    modifier: Modifier = Modifier,
    primary: RecoveryPrimary = RecoveryPrimary.Retry,
)
```
删除参数：`retryLabel`、`retryingLabel`、`reloginLabel`、`detailDialogTitle`、`footer`、`icon`。文案固定为：
- 重试：`重试`，进行中显示 `正在重试…`
- 重新登录：`重新登录`
- 详情入口：`查看错误详情`，详情弹窗标题 `错误详情`，按钮 `关闭`

版式（**没有有色底**，直接放在页面底色上，居中）：
- `Column(fillMaxWidth, padding horizontal 32dp, vertical 48dp, horizontalAlignment = CenterHorizontally)`
- 标题：17sp SemiBold `LxText`，居中（与 `LxEmptyState` 同级）
- 正文：13.5sp、lineHeight 22sp、`LxTextMuted`，居中，上间距 8dp。**只能一句话，不超过 2 行**
- 上间距 24dp 后放主按钮：
  - `primary == Relogin`：`LxPrimaryButton("重新登录")`；
  - `primary == Retry`：`LxPrimaryButton(if (isRetrying) "正在重试…" else "重试", enabled = !isRetrying)`；
  - 宽度 `fillMaxWidth(0.72f)`。
- 上间距 8dp 后放次要操作：`LxTextAction`，文字是另一个操作（"重试" / "正在重试…" 或 "重新登录"）；重试中时两个操作都 `enabled = false`。
- 上间距 4dp 后放 `LxTextAction("查看错误详情", color = LxTextFaint)`，点开 `LxDialog` 显示 `technicalDetail`（保持现有可滚动写法）。

### 5.3 文案表（写死在调用点，不要再自由发挥）

| 场景 | title | message | primary |
|---|---|---|---|
| 劳动教育（`ldjy` 非法访问，确定是 token 失效） | `登录已失效` | `重新登录后即可查看劳动教育。` | `Relogin` |
| 查寝（`fdygl` 非法访问，可能是登录问题） | `暂时无法加载查寝` | `可能是登录已过期，先重试一次。` | `Retry` |
| 报修（T3，按实测结论二选一） | `登录已失效` / `暂时无法加载报修` | `重新登录后即可查看报修。` / `可能是登录已过期，先重试一次。` | `Relogin` / `Retry` |
| AI 课堂（`FifMappingErrorCard`） | `暂时无法连接 AI 课堂` | `FifMappingReason` 的现有文案（`FifUserMappingException.kt:22-26`，均为一句话） | `Retry` |

- `FifMappingErrorCard.kt` 只保留 `title` 默认值，删除已不存在的参数传递。
- 查寝原来的脚注"离校登记仍可使用。"删除（tab 本身就说明了）。

### 5.4 测试同步
`RecoveryUiInstrumentedTest.kt`：
- 文案替换：`重新尝试连接` → `重试`，`正在重新连接…` → `正在重试…`；
- 其余断言保持不变，包括重试中时"重新登录"不可点、`查看错误详情` → 详情 → `关闭`；
- 第三个用例 `登录状态已失效` 不变（标题没改）；
- 新增一个用例：`ServiceErrorCard(primary = RecoveryPrimary.Relogin)` 时，主按钮文字是"重新登录"，次要操作是"重试"，两者点击各回调一次。

### 5.5 有缓存时的降级（与 T7 配合）
劳动教育、素质学分有缓存数据时，登录失效**不替换整页**：在列表最上方（`LxPageHeader` 下方，间距 12dp，左右 24dp）插入 `LxSessionBanner(title = "登录已失效", body = "显示的是上次保存的数据", onAction = onRelogin)`，下面照常显示缓存内容。

---

## T6 劳动教育 / 素质学分 / 考试成绩改版

### 共同骨架（三页一致，参考请假列表）
- `Scaffold(containerColor = LxCanvas, topBar = { LxNavBar(onBack = onBack, title = <页名>, collapsed = listState.lxHeaderCollapsed()) })`
- 内容是一个 `LazyColumn(state = listState, contentPadding = PaddingValues(bottom = 32.dp))`：
  1. `item("header")`：`LxPageHeader(title = <页名>, subtitle = <更新时间，见 T7；无缓存时为 null>)`，`padding(horizontal = 24.dp)`；
  2. `item("hero")`：总览面板，`LxPanel(radius = LxRadiusLg, padding = 20.dp)`，`Modifier.padding(start = 24.dp, end = 24.dp, top = 18.dp)`；
  3. `item("section")`：`LxSectionLabel("<分节名> · <条数>")`，`padding(start = 24.dp, end = 24.dp, top = 26.dp, bottom = 4.dp)`；
  4. 记录行：**不再每条一张卡片**，直接排在页面底色上，样式参考 `LeaveListScreen.kt:217-248` 的 `LeaveRow`（左右 24dp，上下 14dp，不画分割线）。
- 旧的 `LxTopBar`、`LxCard` 列表、`MaterialTheme.typography` 文本在这三页全部替换成新组件和显式字号。
- 数字统一用等宽数字：`style = TextStyle(fontFeatureSettings = "tnum")`。
- **公共组件抽取**：把 `LeaveListScreen.kt:204-214` 的 `MonthHeader` 和 `:250-274` 的 `DateBlock`（连同 `WeekLabels`）移到 `ClaudeComponents.kt`，分别命名为 `LxMonthHeader(month: YearMonth)` 和 `LxDateBlock(parsed: LocalDateTime?)`，`public`。请假页改为调用公共版本，视觉不变。

### 6.1 劳动教育（`feature/labor/ui/LaborSummaryScreen.kt`）
**总览面板 `HoursHero(summary)`**：
1. `LxSectionLabel("累计时长")`
2. 上间距 6dp 后放一个 `Row(verticalAlignment = Bottom)`：数值用 34sp SemiBold `LxText` 等宽，内容为 `"%.1f".format(计数动画值)`（T12-B，P7 之前直接显示终值）；后跟 `" 小时"`，15sp `LxTextMuted`，底部对齐。
3. 上间距 16dp 后放**分段条** `LxSegmentBar`（新组件，放 `ClaudeComponents.kt`）：
   ```kotlin
   data class LxSegment(val label: String, val value: Double, val color: Color)
   @Composable fun LxSegmentBar(segments: List<LxSegment>, modifier: Modifier = Modifier, fill: Float = 1f)
   ```
   - 高 8dp、圆角 4dp；
   - 只画 `value > 0` 的段，宽度按 `value / sum` 比例，段间留 2dp 空隙；
   - 首段左侧、末段右侧圆角，中间段直角；
   - 总和为 0 时整条画 `LxTint`；
   - `fill` 是入场填充进度 0..1（T12-B），默认 1。
4. 上间距 14dp 后放**图例**：两列网格（`Row` 套两个 `Column(weight 1f)`，按奇偶索引分配），行间距 10dp。每项是一个 `Row`：
   - 6dp 圆点（类别色）；
   - 间距 8dp，标签 13sp `LxTextSecondary`、`maxLines = 1`；
   - `Spacer(weight 1f)`；
   - 数值 13sp Medium `LxText` 等宽，右侧留 12dp。
   - 值为 0 的项，标签和数值都用 `LxTextFaint`。
   - 类别与颜色沿用现有映射（`LaborSummaryScreen.kt:201-207`：志愿服务、暑期实践、劳动实践、社区服务、其他，依次取 `LxCategoryColors[0..4]`）。

**记录区**：
- 分节标签：`活动记录 · ${activities.size}`。
- 按 `createDate` 的年月分组（列表已在 T1 倒序），组头用 `LxMonthHeader`。
- 行 `ActivityRow(record, onClick)`：
  - 左侧 `LxDateBlock(createDate 解析结果)`；
  - 中间 `Column(weight 1f, padding start 14dp)`：
    - 标题：`activityName`，15.5sp SemiBold `LxText`，`maxLines = 2`；
    - 元信息：`"${projectTypeName} · HH:mm"`，12.5sp `LxTextMuted`，上间距 5dp，`maxLines = 1`；
  - 右侧 `Column(horizontalAlignment = End, padding start 12dp)`：
    - `"+%.1f".format(serviceTimes)`，15sp SemiBold `LxText` 等宽；
    - `小时`，11sp `LxTextFaint`。
  - 点击反馈用 T12-A 的 `lxPressable`。
- 底部：`LxLoadMoreFooter(..., showEnd = activities.size >= 4)`。

**详情弹层**：
- 用 `LxBottomSheet` 替换 `ModalBottomSheet`（`LaborSummaryScreen.kt:105-118`）。
- **点击后立刻打开弹层**，加载期间显示骨架。需要修改 `LaborViewModel.onRecordClick`：先 `update { it.copy(showDetailSheet = true, selectedRecord = record, isDetailLoading = true, selectedDetail = null, detailError = null) }`，再发请求。
- 弹层内容：
  - 标题 `activityName`，18sp SemiBold；
  - 下面 `LxMetaRow` 依次为：活动类型、活动级别、主办方、志愿时长（`"%.1f 小时"`）、日期；
  - 加载中显示 3 个 `LxSkeletonBlock(height = 14.dp)`，间距 12dp；
  - 出错显示 `LxErrorState`。

### 6.2 素质学分（`feature/credit/ui/CreditScreen.kt`）
- 接口只返回 `countCredit`、`pass`、模块列表（`CreditResponse.kt:13-23`），**没有达标线数值**，不要编造目标值。
- **总览面板 `CreditHero(overview)`**：
  - 第一行 `Row`：`LxSectionLabel("素质学分")`、`Spacer(weight 1f)`、`LxStatus(text = if (pass) "已达标" else "未达标", tone = if (pass) LxTone.Success else LxTone.Warning)`；
  - 第二行：数值 34sp SemiBold 等宽 `"%.1f"`，后跟 `" 学分"` 15sp `LxTextMuted`；
  - 再往下是 `LxSegmentBar`（模块按 `LxCategoryColors[index % size]` 取色）和两列图例，规格同 6.1。
- **记录区**：
  - 分节标签 `学分记录 · ${records.size}`；
  - 记录没有日期，不分组，按服务端顺序排列；
  - 行 `CreditRow`：
    - 左侧 `Column(weight 1f)`：名称 15.5sp SemiBold `maxLines = 2`；下面是 `LxStatusLine(status = statusName, tone = creditTone(statusName), meta = "")`，上间距 5dp；
    - 右侧：`"+%.1f".format(score)`，15sp SemiBold 等宽；下面 `学分`，11sp `LxTextFaint`。
  - `creditTone` 规则：
    - 含 `有效` / `通过` → `Success`
    - 含 `驳回` / `无效` / `不通过` → `Danger`
    - 含 `审核` / `待` → `Warning`
    - 其余 → `Neutral`
- 详情弹层：处理方式同 6.1（`LxBottomSheet`，立即打开，骨架占位）。`LxMetaRow` 依次为：获奖级别、获奖等级、最高级别、获得学分、获得时间、所属模块、子类别、审核状态。
- **错误态**：
  - 总览成功、记录失败：记录区显示一行 `LxErrorState(message, onRetry)`；
  - 两者都失败且没有缓存：整页 `LxErrorState`。

### 6.3 考试成绩（`feature/exam/ui/ExamScreen.kt`）
**学期选择**：删除两个 `ExposedDropdownMenuBox`（`:89-175`），换成一个选择胶囊：
- `Row(height 36dp, clip RoundedCornerShape(LxRadiusSm), background LxTint, padding horizontal 12dp)`；
- 文字 `"${年份显示} ${学期名}"`，14sp Medium `LxText`；
- 后跟 `Icons.Outlined.KeyboardArrowDown`，18dp，`LxTextMuted`；
- 放在 `LxPageHeader` 下方，`padding(start = 24.dp, top = 14.dp)`。
- 点击打开 `LxOptionSheet(title = "选择学期", options = …)`：
  - 选项是学年 × {第一学期, 第二学期} 的笛卡尔积，学年按 `schoolYears` 顺序；
  - label 为 `"2025-2026 第一学期"`，value 为 `"$year|$semester"`；
  - `onSelect` 时拆分 value，调用 `ExamViewModel` 新增的 `onTermSelected(year, semester)`。这个方法设置两个字段后只发**一次**请求，删除原来分开的 `onYearSelected` / `onSemesterSelected`。

**汇总面板（移到顶部，`ExamHero`）**：
- 把计算抽成 `feature/exam/domain/ExamSummary.kt` 中的纯函数：
  ```kotlin
  data class ExamSummary(val weightedGpa: Double?, val totalCredit: Double, val courseCount: Int)
  fun summarize(scores: List<ExamScore>): ExamSummary
  ```
  - `totalCredit`：所有课程 `credit.toDoubleOrNull() ?: 0.0` 之和；
  - `weightedGpa`：只计入"学分 > 0 且绩点能解析成数字"的课程，结果为 Σ(学分 × 绩点) / Σ学分。**绩点无效的课，学分不进分母**（修正 `ExamScreen.kt:251-257` 的旧算法）。没有符合条件的课程时返回 `null`，界面显示 `--`；
  - `courseCount = scores.size`。
- 面板内是一个 `Row`，三列等宽，复用 `RouteTemplateDetailScreen.kt` 中 `StatCell` 的样式（值 21sp SemiBold、标签 11sp `LxTextMuted`）；建议把 `StatCell` 提为公共组件 `LxStatCell`：
  - `"%.2f"` 或 `--`，标签 `学期绩点`；
  - `"%.1f"`，标签 `总学分`；
  - 课程数，标签 `课程`。
- 面板底部上间距 12dp 放脚注，11sp `LxTextFaint`：`绩点按学分加权，仅计入有绩点的课程`。

**成绩行 `ScoreRow`**：
- 左侧 `Column(weight 1f)`：
  - 课程名，15.5sp SemiBold `maxLines = 2`；
  - 下面一行，12.5sp `LxTextMuted`，上间距 5dp：`"${credit} 学分 · 绩点 ${gpa} · ${category}"`，空字段跳过，用 ` · ` 连接。
- 右侧：成绩 17sp SemiBold 等宽，`padding start 12dp`。数字和"优秀/良好"等文字**同字号同颜色**：
  - 不及格（数字 < 60，或文字含 `不及格` / `不合格`）用 `LxDanger`；
  - 其他用 `LxText`；
  - 不再整列用强调色。
- 原底部 `SummaryCard`（`:249-292`）删除。

**测试**：新增 `ExamSummaryTest.kt`，覆盖以下情况：
- 正常加权；
- 0 学分课程不影响结果；
- 绩点为空的课程不进分母；
- 没有有效课程时返回 `null`。

---

## T7 劳动教育、素质学分持久化

### 设计
- 在现有 Room 库（`core/database/LightXinDatabase.kt`）里加一张**通用快照表**，存整份 JSON，供劳动教育、素质学分使用（以后考试成绩也能复用）。
- 先显示缓存，同时后台刷新。按 `userCode` 隔离，并检查账号代次。这与课表缓存的做法一致：`ScheduleRepository.kt` 用 `session.isCurrent(lease)` 和 `requireCurrent`，退出登录不清表、靠 `userCode` 隔离。

### 步骤
1. 新建 `core/database/RemoteSnapshot.kt`：
   ```kotlin
   @Entity(tableName = "remote_snapshot", primaryKeys = ["userCode", "snapshotKey"])
   data class RemoteSnapshotEntity(
       val userCode: String,
       val snapshotKey: String,   // 不用 key 作列名，KEY 是 SQLite 关键字
       val json: String,
       val fetchedAt: Long,
   )

   @Dao
   interface RemoteSnapshotDao {
       @Query("SELECT * FROM remote_snapshot WHERE userCode = :user AND snapshotKey = :key")
       suspend fun get(user: String, key: String): RemoteSnapshotEntity?
       @Upsert
       suspend fun put(entity: RemoteSnapshotEntity)
   }
   ```
2. `LightXinDatabase`：
   - `entities` 加 `RemoteSnapshotEntity::class`；
   - `version = 2`；
   - `autoMigrations = [AutoMigration(from = 1, to = 2)]`（只是新增一张表，自动迁移即可，不需要 spec）；
   - 新增 `abstract fun remoteSnapshotDao(): RemoteSnapshotDao`。
   - 数据库文件名 `lightxin_schedule.db` **不改**，改名会丢掉现有课表缓存。
   - 编译后提交生成的 `app/schemas/com.lightxin.core.database.LightXinDatabase/2.json`。
3. 新建 `core/database/SnapshotStore.kt`（`@Singleton`，注入 `LightXinDatabase`、`AccountSession`、`Gson`；如果没有 Gson 的 Hilt 绑定就内部 `Gson()`）：
   ```kotlin
   data class Snapshot<T>(val value: T, val fetchedAt: Long)
   suspend fun <T> read(account: AccountContext, key: String, type: Type): Snapshot<T>?
   suspend fun <T> write(account: AccountContext, key: String, value: T)
   ```
   - 两个方法都在 `Dispatchers.IO` 上执行，执行前调用 `session.requireCurrent(account.lease)`。
   - 解析失败（例如字段变化）时返回 `null`，不抛异常。
4. key 常量：`labor.summary`、`labor.activities`、`credit.overview`、`credit.records`。值直接存 domain 对象（`HoursSummary`、`List<ActivityRecord>`、`CreditOverview`、`List<CreditRecord>`）。
5. **写入时机**：只在网络请求**成功**后写入。劳动教育的 summary 和第一页（T1 后一页即全部）都成功后才写；失败或被拒绝时不覆盖旧快照。素质学分的 overview 与 records 各自成功各自写。
6. **ViewModel 流程**（劳动教育、素质学分相同）：
   1. `init`：取 `tokenManager.account()`，读缓存。有缓存时直接填充数据，`isLoading = false`，`isRefreshing = true`，`fetchedAt = 缓存时间`。
   2. 发起网络请求。成功后替换数据、写缓存、`fetchedAt = now`、`isRefreshing = false`。
   3. 失败时：
      - 有缓存：保留数据。登录失效显示 T5.5 的横幅；其他错误在 header 下方显示一行 `更新失败 · 点此重试`（12.5sp `LxTextMuted`，整行可点）。
      - 无缓存：走现有整页错误或 T5 卡片。
   4. 每次发布到 UI 前检查 `session.isCurrent(account.lease)`。
7. `LxPageHeader` 的 subtitle 显示 `更新于 M月d日 HH:mm`，仅在当前显示的数据来自缓存且本次刷新尚未成功时显示；刷新成功后去掉。

### 测试
仿照 `ScheduleCacheTest`（Robolectric + 内存数据库）新增 `SnapshotStoreTest.kt`：
- 写入后能读回；
- 不同 `userCode` 互不可见；
- 账号代次失效后读写抛 `CancellationException`；
- JSON 损坏时返回 `null`。

---

## T8 模板页按钮

### 现状
`feature/running/ui/RouteTemplateDetailScreen.kt:176-225`：
- "导出模板 / 重命名"是两个 `LxGhostButton` 并排；
- 下方隔 36dp 是满宽实心红色 `LxDangerButton("删除模板")`，再加一行提示。
- 删除按钮成了页面上最显眼的元素（截图 `S61025-18242204`）。

### 步骤
1. 在 `ClaudeComponents.kt` 新增：
   ```kotlin
   @Composable
   fun LxActionRow(icon: ImageVector, text: String, onClick: () -> Unit, modifier: Modifier = Modifier, tint: Color = Color.Unspecified)
   ```
   - 最小高度 52dp，padding horizontal 15dp、vertical 12dp；
   - 左侧图标 20dp，`LxTextSecondary`；间距 12dp；
   - 文字 15sp `LxText`（`tint` 指定时文字和图标都用 `tint`），占 `weight(1f)`；
   - 右侧 `KeyboardArrowRight` 18dp，`LxTextFaint`；
   - 点击反馈用 `lxPressable`（T12-A）。
2. 替换第 197–223 行：
   ```
   LxGroupCard {
       LxActionRow(Icons.Outlined.IosShare, "导出模板") { RouteTemplateExport.shareSingle(context, template) }
       LxGroupDivider()
       LxActionRow(Icons.Outlined.Edit, "重命名") { renameInput = template.name; renameVisible = true }
   }
   Spacer(24.dp)
   LxTextAction("删除模板", onClick = { deleteVisible = true }, color = LxDanger,
       modifier = Modifier.align(CenterHorizontally))   // 外层需要是能居中的 Column
   Spacer(28.dp)
   ```
   - "删除后不可恢复，建议先导出备份。"这行从页面删除，确认弹层的 message 已经包含这句。
   - 如果 `Icons.Outlined.IosShare` 不在 `material-icons-extended` 里，改用 `Icons.Outlined.Share`。
3. "设为默认"的 `LxPrimaryButton` 及其说明保持在操作区最上方，不变。
4. `RouteTemplateImportScreen.kt:182`：页面上唯一的操作"选择 JSON 文件"由 `LxGhostButton` 改为 `LxPrimaryButton`。

---

## T9 签到结果改为底部弹层

### 现状
`feature/checkin/ui/CheckinDetailScreen.kt:71-95`：成功后 `showSnackbar("签到成功！")`。这个调用会**挂起到 Snackbar 消失**（约 4 秒）才返回，期间 Snackbar 挡住了提交按钮（截图 `S61022-21465641`）。失败时也用 Snackbar。

### 步骤
1. `CheckinDetailViewModel`：
   - UI 状态新增 `signedAt: LocalTime? = null`，成功时（`:146`）设置为 `LocalTime.now()`；
   - `submitError` 保持现有语义。
2. `CheckinDetailScreen`：删除 `SnackbarHostState`、`SnackbarHost` 和两个 `LaunchedEffect`。
3. **成功弹层**：`if (uiState.submitSuccess)` 时渲染 `LxBottomSheet(onDismiss = finish)`，其中 `finish = { onSubmitSuccess(); onBack() }`。内容：
   - 48dp 圆形背景（`LxStateSuccess.copy(alpha = 0.12f)`），中间 `Icons.Outlined.Check` 26dp `LxStateSuccess`，水平居中；
   - 上间距 14dp 后放标题 `签到成功`，18sp SemiBold，居中；
   - 上间距 16dp 后放 `LxMetaRow("时间", signedAt 格式化为 HH:mm)`；
   - 再放 `LxMetaRow("地点", detail.address.ifBlank { detail.signinPlace })`；
   - 上间距 20dp 后放 `LxPrimaryButton("完成", onClick = finish)`。
4. **失败弹层**：`if (uiState.submitError != null)` 时渲染 `LxBottomSheet(onDismiss = viewModel::clearSubmitError)`。内容：
   - 48dp 圆（`LxDangerSurface`），中间 `Icons.Outlined.ErrorOutline` 26dp `LxDanger`；
   - 标题 `签到没有成功`；
   - 正文 `submitError`，13.5sp `LxTextMuted`，居中，`maxLines = 3`；
   - `LxPrimaryButton("重试")`：先 `clearSubmitError()`，再用**同一张照片文件**调用提交（复用屏幕里现有的提交 lambda）；
   - 下面 `LxTextAction("返回修改", color = LxTextMuted)` → `clearSubmitError()`。
5. 触感反馈（T12-B 实施时补上）：成功弹层出现时 `Confirm`，失败时 `Reject`。

---

## T10 查寝列表分区（不加历史卡片）

### 现状
`CheckinListScreen.kt:177-180`：服务端分页返回的任务按原顺序平铺，已签到和未签到混在一起。`CheckinTask.isInOpenWindow`（`CheckinModels.kt:20`）可以判断当前是否在开放时间内。

### 步骤
在 `checkinContent` 的 `else` 分支里：
1. `val open = uiState.tasks.filter { !it.isSigned && it.isInOpenWindow }`，`val rest = uiState.tasks - open.toSet()`。
2. `open` 非空时：
   - 先放 `item("open_label")`：`LxSectionLabel("现在可以签到")`，padding start 24dp、top 18dp、bottom 2dp；
   - 再放 `open` 的行，状态文字 `可签到`，tone `LxTone.Accent`。
3. `rest` 非空且 `open` 非空时，放 `item("rest_label")`：`LxSectionLabel("全部记录")`，同样的 padding，top 改为 22dp。`open` 为空时不放任何分节标签，直接平铺 `rest`。
4. `rest` 的行：
   - 已签到 → `已签到` / `Success`；
   - 未签到 → `未签到` / `Warning`（保持现状）。
   - **不要写"已错过"**，服务端可能允许补签，我们无法断定。
5. `CheckinRow` 新增参数 `status: String, tone: LxTone`，由调用方传入。可点击条件保持 `!task.isSigned` 不变。
6. 加载更多改用 `LxLoadMoreFooter`。

---

## T11 跑步轨迹显示（只画形状）

### 现状
- `feature/running/ui/RouteTrackCanvas.kt:32-67` 的 `TrackCanvas` 把经纬度直接等比投影。**没有对经度乘 cos(纬度)**：在纬度 31° 时，形状会被横向拉宽约 17%。
- 跑步中页（`RunningActiveScreen.kt`）只显示"已记录 N 个轨迹点"，不画轨迹。
- 结果页（`RunningResultScreen.kt`）没有轨迹，`RunningResult`（`RunningModels.kt:69-83`）也不带点集。
- 模拟页（`RunningSimScreen.kt:94-130`）只用文字描述默认模板。

### 步骤
1. **修正投影**：在 `TrackCanvas` 里用 `val k = cos(Math.toRadians((minLat + maxLat) / 2))` 修正，经度跨度和投影 x 都乘 `k`：
   ```kotlin
   val lngSpanAdj = (lngSpan * k).coerceAtLeast(1e-6)
   val scale = minOf(w / lngSpanAdj.toFloat(), h / latSpan.toFloat())
   x = offsetX + ((p.longitude - minLng) * k * scale).toFloat()
   ```
   把投影计算抽成 `internal fun projectTrack(points, width, height): List<Offset>`，便于单测。模板列表缩略图和详情会同步变准，属于预期变化。
2. `TrackCanvas` 新增参数 `live: Boolean = false`。`live = true` 时：
   - 终点不画红点，改为 `LxAccent` 实心 4dp 加外圈 9dp 半透明光晕（`LxAccent.copy(alpha = 0.2f)`）；
   - 起点仍为绿色。
3. **跑步中页**：在"当前距离"卡片和三格指标之间插入一个 `item`：
   - `LxCard { Box(Modifier.fillMaxWidth().height(200.dp).padding(16.dp)) { … } }`；
   - 点数 ≥ 2 时 `TrackCanvas(trackerState.points, Modifier.fillMaxSize(), live = true)`；
   - 否则居中显示 `等待定位…`，13sp `LxTextMuted`。
   - 点集每次更新都会重绘，数量级在几千以内，不需要做抽稀。
4. **结果页**：
   - `RunningResult` 新增字段 `points: List<TrackPoint> = emptyList()`。
   - 在构造 `RunningResult` 的位置填入点集：
     - `RunningRepository.kt:311` 一带：成功分支用 `snapshot.points`；
     - `RunningViewModel.buildFailedResult`（`:459-494`）：新增参数并传入 `snapshot.points`（`:389-394` 调用处）；
     - 模拟上传分支（`:265-308`）：传入 `RouteReplayPlanner` 生成的点集。
   - 结果页第一项改为轨迹卡：`LxCard`，高 180dp，padding 16dp，内容 `TrackCanvas(result.points)`；点数 < 2 时不显示这张卡。
   - 不同来源的点可能是 BD-09 或 WGS-84（`RunningSnapshot.pointsAreBd09`），坐标系差异在这个尺度上只是整体平移，**不影响形状，不需要转换**。
5. **模拟页**：在"路线来源"卡片中，`defaultTemplate != null` 时，在文字下方加一块 `Box(height 140dp, background LxTint, 圆角 12dp, padding 12dp)`，里面放 `TrackCanvas(template.points)`。
6. 测试 `TrackProjectionTest.kt`：
   - 一个正方形（以米计）的点集投影后宽高比约为 1（误差 < 2%）；
   - 所有点都落在画布内。

### 不要做
不引入地图 SDK，不拉任何瓦片或静态地图，不做额外的坐标转换。

---

## T12 动效 v3

### 原则（写进新 note）
- 进场用减速段很长的曲线，退场用加速曲线，屏内往返用对称曲线；
- 一次动作组合多个属性（透明度 + 小位移 + 小缩放），每个只动一点；
- 先出容器、后出内容；进场比退场慢；
- 转场不做 overshoot；唯一允许的极轻回弹是**按压松开**（可单独回退）；
- 有固定时长的动画，除计数外不超过 450ms；手势驱动的物理动画不受此限；
- 滑到边缘保留系统拉伸效果；
- 系统关闭动画时直接落到终值（Compose `durationScale`）；
- 所有数值只写在 `LxMotion`，`MotionTokenGateTest` 继续生效。

### T12-A（P2 阶段）

#### A1. 重写 `core/designsystem/theme/LxMotion.kt`
完整替换 `object LxMotion` 部分（文件头注释同步改写，指向新 note）：
```kotlin
object LxMotion {
    // ── 曲线 ──
    /** 进场 / 落定：起步快、减速段很长。 */
    val EaseEnter = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)
    /** 退场：加速离开，不拖尾。 */
    val EaseExit = CubicBezierEasing(0.4f, 0f, 1f, 1f)
    /** 屏内往返（展开 / 收起、颜色）。 */
    val EaseInOut = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)

    // ── 时长 ──
    const val PressInMs = 90
    const val PressOutMs = 200
    const val StateMs = 220
    const val StateExitMs = 160
    const val EnterMs = 400          // 内容 / 列表项入场
    const val OverlayEnterMs = 320   // 对话框、菜单
    const val OverlayExitMs = 180
    const val PageEnterMs = 400
    const val PageFadeInMs = 280
    const val PageFadeDelayMs = 80
    const val PageFadeOutMs = 120
    const val PageExitMs = 240
    const val TabFadeOutMs = 90      // 底部 tab 淡出淡入
    const val TabFadeInMs = 220
    const val ContentDelayMs = 60
    const val StaggerMs = 30
    const val StaggerMaxItems = 5
    const val StaggerWindowMs = 600  // 首屏错峰窗口：StaggerMs * StaggerMaxItems + EnterMs 再留余量
    const val CountUpMs = 700
    const val QuickFadeMs = 160
    const val CollapseMs = 180
    const val BannerMs = 200
    const val BannerDelayMs = 200
    /** 首页卡片入场：用户 2026-10 指定的手感，数值不改，只换曲线。 */
    const val CardEnterMs = 350
    const val CardStaggerMs = 60
    const val CardShiftFraction = 0.3333f

    // ── 加载 / 引导（持续动画例外）──
    const val SkeletonMs = 2200
    const val ShimmerMs = 1200
    const val PulseMs = 2600
    const val OnboardingEnterMs = 450
    const val OnboardingStaggerBaseMs = 80
    const val OnboardingStaggerStepMs = 90
    const val OnboardingShiftFraction = 0.25f

    // ── 位移 / 缩放 ──
    val EnterShiftDp = 10.dp
    const val PageShiftFraction = 0.08f       // 新页面从 8% 处滑入
    const val PageExitShiftFraction = 0.04f   // 旧页面反向退 4%
    const val TabInitialScale = 0.98f
    const val OverlayInitialScale = 0.96f
    const val OverlayExitScale = 0.98f
    const val PressedScaleButton = 0.97f
    const val PressedScaleCard = 0.985f
    const val PressOverlayAlpha = 0.05f

    // ── 弹簧 ──
    const val MoveStiffness = 380f
    const val SharedBoundsStiffness = 300f
    val PressIn: SpringSpec<Float> = spring(dampingRatio = 1f, stiffness = 1500f)
    /** 唯一允许的极轻回弹：按压松开。觉得多余时把 dampingRatio 改回 1f 即可整体回退。 */
    val PressRelease: SpringSpec<Float> = spring(dampingRatio = 0.75f, stiffness = 500f)
    val ColorSpring: SpringSpec<Color> = spring(dampingRatio = 1f, stiffness = 600f)
    fun pagerSnap(): SpringSpec<Float> = spring(dampingRatio = 1f, stiffness = MoveStiffness)
    fun placement(): SpringSpec<IntOffset> =
        spring(dampingRatio = 1f, stiffness = MoveStiffness, visibilityThreshold = IntOffset.VisibilityThreshold)

    // ── 常用 tween ──
    fun <T> enter(durationMs: Int = EnterMs, delayMs: Int = 0) = tween<T>(durationMs, delayMs, EaseEnter)
    fun <T> exit(durationMs: Int = StateExitMs) = tween<T>(durationMs, easing = EaseExit)
}
```
- 删除旧的 `EaseOut`、`PageMs`、`PagePopExitMs`、`TabMs`、`DialogExitMs`、`PagePopShiftFraction`、`TabShiftFraction`、`SoftDp`、`PressScale`、`tab()`、`dialogExit()`，然后**逐个修复编译错误**。对应关系：
  - `EaseOut`：进场改用 `EaseEnter`，退场改用 `EaseExit`；
  - `tab()`：改用 `pagerSnap()`；
  - `PressScale`：改用 `PressIn` / `PressRelease`；
  - `SoftDp`：`lxEnterShift` 改为用 `tween(EnterMs, easing = EaseEnter)` 驱动 Dp。
- `animatedPressScale`：`animationSpec = if (pressed) LxMotion.PressIn else LxMotion.PressRelease`。
- `lxEnterShift`：位移和透明度都用 `LxMotion.enter()`，位移从 `EnterShiftDp` 开始。
- 注意 `MotionTokenGateTest` 只拦截 `tween(数字` / `durationMillis = 数字` / `spring(...stiffness = 数字`。上面的数字都在 `LxMotion.kt` 内部，没有问题；调用点只能引用 token。

#### A2. 页面转场（`navigation/NavGraph.kt:201-227`）
保留 `suppressTransitions` 判断，四个转场改为：
```kotlin
enterTransition = {
    fadeIn(tween(LxMotion.PageFadeInMs, LxMotion.PageFadeDelayMs, LxMotion.EaseEnter)) +
        slideInHorizontally(tween(LxMotion.PageEnterMs, easing = LxMotion.EaseEnter)) { (it * LxMotion.PageShiftFraction).toInt() }
},
exitTransition = {
    fadeOut(tween(LxMotion.PageFadeOutMs, easing = LxMotion.EaseExit)) +
        slideOutHorizontally(tween(LxMotion.PageEnterMs, easing = LxMotion.EaseEnter)) { -(it * LxMotion.PageExitShiftFraction).toInt() }
},
popEnterTransition = {
    fadeIn(tween(LxMotion.PageFadeInMs, LxMotion.PageFadeDelayMs, LxMotion.EaseEnter)) +
        slideInHorizontally(tween(LxMotion.PageEnterMs, easing = LxMotion.EaseEnter)) { -(it * LxMotion.PageExitShiftFraction).toInt() }
},
popExitTransition = {
    fadeOut(tween(LxMotion.PageFadeOutMs, easing = LxMotion.EaseExit)) +
        slideOutHorizontally(tween(LxMotion.PageExitMs, easing = LxMotion.EaseExit)) { (it * LxMotion.PageShiftFraction).toInt() }
},
```
引导页的单独转场（`:229-233`）改为 `fadeIn(LxMotion.enter())` / `fadeOut(LxMotion.exit())`。

#### A3. 底部 tab 切换（`feature/home/ui/HomeScreen.kt:105-121`）
改为淡出淡入（不再横移）：
```kotlin
transitionSpec = {
    (fadeIn(tween(LxMotion.TabFadeInMs, LxMotion.TabFadeOutMs, LxMotion.EaseEnter)) +
        scaleIn(tween(LxMotion.TabFadeInMs, LxMotion.TabFadeOutMs, LxMotion.EaseEnter), initialScale = LxMotion.TabInitialScale))
        .togetherWith(fadeOut(tween(LxMotion.TabFadeOutMs, easing = LxMotion.EaseExit)))
}
```

#### A4. 分页吸附（`LxTabPager.kt`）
- `snapAnimationSpec = LxMotion.pagerSnap()`；
- 点 tab 时 `state.animateScrollToPage(target, animationSpec = LxMotion.pagerSnap())`。
- 临界阻尼弹簧会承接松手时的速度，不会冲过页边界。如果用户觉得比原来的 420ms 快，只调 `MoveStiffness`（例如 300）。

#### A5. 对话框（`LxDialog.kt:63-79`）
- 进场 `progress.animateTo(1f, tween(LxMotion.OverlayEnterMs, easing = LxMotion.EaseEnter))`；
- 出场 `progress.animateTo(0f, tween(LxMotion.OverlayExitMs, easing = LxMotion.EaseExit))`；
- 缩放：进场时 `OverlayInitialScale + (1 - OverlayInitialScale) * progress`；出场时 `OverlayExitScale + (1 - OverlayExitScale) * progress`。用一个 `var exiting` 标记区分两种情况；
- 透明度 = `progress`。

#### A6. 底部弹层
`LxBottomSheet` 使用 M3 `ModalBottomSheet`，其进出动画和拖动速度承接**保持原样**，不要手写弹层。只调整：遮罩改为 `Color.Black.copy(alpha = 0.32f)`；弹层内容外包一层 `Modifier.lxEnterShift()`，让内容比容器晚一点上浮。

#### A7. 按压反馈（新组件替代 ripple）
新建 `core/designsystem/theme/LxPressable.kt`：
```kotlin
@Composable
fun Modifier.lxPressable(
    onClick: () -> Unit,
    enabled: Boolean = true,
    role: Role = Role.Button,
    pressedScale: Float = 1f,
    shape: Shape = RectangleShape,
): Modifier {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val active = pressed && enabled
    val scale by animateFloatAsState(
        targetValue = if (active) pressedScale else 1f,
        animationSpec = if (active) LxMotion.PressIn else LxMotion.PressRelease,
        label = "lxPressableScale",
    )
    val overlay by animateFloatAsState(
        targetValue = if (active) 1f else 0f,
        animationSpec = tween(if (active) LxMotion.PressInMs else LxMotion.PressOutMs, easing = LxMotion.EaseInOut),
        label = "lxPressableOverlay",
    )
    val tint = LxText
    return this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .clip(shape)
        .drawWithContent {
            drawContent()
            if (overlay > 0f) drawRect(tint.copy(alpha = LxMotion.PressOverlayAlpha * overlay))
        }
        .clickable(interactionSource = interaction, indication = null, enabled = enabled, role = role, onClick = onClick)
}
```
替换以下组件里的 `clickable`（保留原有 `clip`，把 `shape` 传进去）：
- `LxPrimaryButton` / `LxGhostButton` / `LxDangerButton`：`pressedScale = LxMotion.PressedScaleButton`；
- `LxFloatingAction`：`PressedScaleButton`；
- `LxGroupRow`、`LxSelectRow`、`LxActionRow`（T8）、`LxOptionSheet` 选项行：`pressedScale = 1f`（整行只变暗，不缩放）；
- 列表行 `LeaveRow`、`RepairRow`、`CheckinRow`、`HolidayRow`、模板列表行，以及 T6 的三种新行：`pressedScale = 1f`；
- `LxTextAction`：不加遮罩，改为按下时文字 alpha 降到 0.6（单独用 `animateFloatAsState`，规格同上）。
- `LxFilterTabs` 不动（已经是 `indication = null`，靠白块跟手）。

### T12-B（P7 阶段）

#### B1. 列表项增删
请假、报修、查寝、离校登记、劳动教育、素质学分、考试成绩、模板列表的记录行统一加：
```kotlin
Modifier.animateItem(
    fadeInSpec = LxMotion.enter(LxMotion.StateMs),
    placementSpec = LxMotion.placement(),
    fadeOutSpec = LxMotion.exit(),
)
```
骨架项（`item(key = "loading")`）也加，使骨架淡出与内容淡入重叠，形成交叉淡入。

#### B2. 首屏错峰入场
在 `LxMotion.kt` 同文件新增：
```kotlin
@Composable
fun Modifier.lxStaggerEnter(index: Int, play: Boolean): Modifier {
    val start = play && index < LxMotion.StaggerMaxItems
    val progress = remember { Animatable(if (start) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (progress.value < 1f) {
            progress.animateTo(1f, tween(LxMotion.EnterMs, index * LxMotion.StaggerMs, LxMotion.EaseEnter))
        }
    }
    val shift = with(LocalDensity.current) { LxMotion.EnterShiftDp.toPx() }
    return graphicsLayer {
        alpha = progress.value
        translationY = (1f - progress.value) * shift
    }
}
```
页面侧：
```kotlin
var staggerDone by rememberSaveable { mutableStateOf(false) }
LaunchedEffect(hasContent) { if (hasContent && !staggerDone) { delay(LxMotion.StaggerWindowMs.toLong()); staggerDone = true } }
// 行：Modifier.lxStaggerEnter(index = 行在首屏中的序号, play = !staggerDone)
```
- 只用在首次进入页面时的记录行（上面 B1 列出的列表）；
- 总览面板用 `index = 0`，第一条记录从 `index = 1` 开始；
- 返回页面不重播（`rememberSaveable`）；
- 刷新后新增的行只走 B1 的淡入。
- 首页卡片保留现有 `CardEnter*` 数值，只把 `HomeDashboard.kt:430-431` 的曲线从 `EaseOut` 换成 `EaseEnter`。

#### B3. 计数与填充
新增：
```kotlin
@Composable
fun rememberLxCountUp(target: Float): State<Float> {
    val anim = remember { Animatable(0f) }
    var first by remember { mutableStateOf(true) }
    LaunchedEffect(target) {
        anim.animateTo(target, tween(if (first) LxMotion.CountUpMs else LxMotion.StateMs, easing = LxMotion.EaseEnter))
        first = false
    }
    return anim.asState()
}
```
- 用于劳动教育总时长、素质学分总分、考试学期绩点和总学分；
- 分段条的 `fill` 用同样的方式从 0 到 1，时长 `CountUpMs`，延迟 `ContentDelayMs`；
- 首页运动进度环如果已有动画就不改。

#### B4. 触感反馈
- **分页吸附**：在 `LxTabPager` 内加：
  ```kotlin
  val haptic = LocalHapticFeedback.current
  LaunchedEffect(state) { snapshotFlow { state.settledPage }.drop(1).collect { haptic.performHapticFeedback(HapticFeedbackType.SegmentTick) } }
  ```
- **下拉刷新越过阈值**：在 `core/designsystem/component` 新增
  ```kotlin
  @Composable
  fun rememberLxPullToRefreshState(): PullToRefreshState
  ```
  内部创建 `rememberPullToRefreshState()`，并用 `snapshotFlow { state.distanceFraction >= 1f }.distinctUntilChanged().filter { it }` 触发 `GestureThresholdActivate`。请假、报修、首页的 `PullToRefreshBox(state = …)` 改用这个函数。
- **签到结果**（T9）：成功 `Confirm`，失败 `Reject`。
- 应用里如果有开关（Switch），切换时用 `ToggleOn` / `ToggleOff`；没有就跳过。
- 系统设置关闭触感时，平台会自动不震，不需要额外判断。

#### B5. 预测性返回
- `AndroidManifest.xml` 的 `<application>` 加 `android:enableOnBackInvokedCallback="true"`。
- 现有代码没有 `BackHandler` 或 `onBackPressed`，不需要迁移。Navigation 2.9 会按手势进度驱动 `popEnter` / `popExit`；M3 的 `ModalBottomSheet` 自带预测性返回。
- 在 Android 14 及以上的设备上验证：返回手势拖动时页面跟手，松手完成或取消都正常；弹层的返回手势正常。

### T12-C（可选，P7 最后做；做之前先给用户看录屏）
列表进详情的容器展开：
- 范围只限请假列表 → 请假详情、报修列表 → 报修详情；
- 用 `sharedBounds`（而不是 `sharedElement`），`resizeMode = RemeasureToBounds`，`clipInOverlayDuringTransition` 配合从 0dp 到 16dp 的圆角过渡，bounds 用 `spring(1f, SharedBoundsStiffness)`；
- 报修现有的照片共享元素（`RepairListScreen.kt:194`、`RepairDetailScreen.kt:201`）保留。
- 如果与页面转场叠加后出现闪烁或跳动，**放弃本项**，在 note 里记录原因。

### T12 验收
- release 包录屏，每段 60fps 以上：页面前进 / 返回、底部 tab 切换、tab 分页拖动和点击、对话框、弹层、按钮和列表行按压、首屏列表入场、劳动教育计数。给用户看。
- 系统"动画时长缩放"设为关闭时，所有界面直接到终态，不残留半透明或偏移。
- `MotionTokenGateTest`、`TabSelectionWeightTest`、`TabPagerInstrumentedTest` 通过（`TabPagerInstrumentedTest` 当前没有写死时长，换成 spring 后应能直接通过）。

---

## 13. 每阶段验证

| 阶段 | 命令 | 额外证据 |
|---|---|---|
| P1 | `testDebugUnitTest`、`lintDebug`、`assembleDebug` | T1 分页日志摘要；T2 实验表；T3 脱敏后的响应样本 |
| P2 | `testDebugUnitTest`、`lintDebug`、`assembleDebug`、`assembleDebugAndroidTest`、`assembleRelease` | 浅色 / 深色截图集；release 动效录屏 |
| P3 | 同上，加 `connectedDebugAndroidTest`（`RecoveryUiInstrumentedTest`） | 首页、查寝、劳动教育、AI 课堂错误卡截图 |
| P4 | 同 P2，加 `SnapshotStoreTest`、`ExamSummaryTest` | 三页截图；断网后打开有缓存的页面截图 |
| P5 | 同 P2 | 模板详情、签到成功和失败弹层、查寝分区截图 |
| P6 | 同 P2，加 `TrackProjectionTest` | 跑步中、结果页、模拟页截图 |
| P7 | 同 P2，加 `connectedDebugAndroidTest` | release 录屏；Android 14+ 预测性返回录屏 |

提交正文必须写明跑了哪些命令、各自结果；设备测试没跑就写"未跑：无设备"。

---

## 14. 需要同步的 notes

| 操作 | 文件 | 内容 |
|---|---|---|
| 新建 | `.agents/notes/2026-10-25-decision-motion-v3.md` | T12 原则、token 表、与旧 note 的差异；用户要的是"细腻"而不是"更快"；滑到边缘保留系统拉伸；按压松开是唯一回弹点 |
| 修订 | `2026-10-18-decision-lx-motion-tokens.md`、`2026-10-18-decision-gentle-tab-paging.md` | 文末"后续修订"指向新 note，标明被推翻的条款（单一曲线、不错峰、共享元素只做 bounds、不做圆角过渡、420ms tween 吸附） |
| 修订 | `2026-10-18-decision-claude-ui-language.md` | §11 执行情况：旧 token 改为别名、ColorScheme 全局切换、`LxCard` 加细边、splash 色 |
| 修订 | `2026-10-17-decision-login-expiry-surfaces.md` | §6 / §7 的卡片版式与文案改为 T5 的版本；新增报修判定（T3 实测形态） |
| 修订 | `2026-10-18-decision-relogin-without-dialog.md` | 横幅正文改为"部分功能已失效，重新登录后恢复"，原因是用户反馈字太多 |
| 新建 | `.agents/notes/2026-10-25-decision-remote-snapshot-cache.md` | T7 设计：通用快照表、写入时机、账号隔离、不随退出清除（与课表一致） |
| 新建或并入 | 劳动教育分页 | `pageSize=100` 与原 App 对齐；`total` 判定；`type:id` 去重；待确认项：`ldjy` 字段重复发送、`userType` 缺失 |

写完运行 `python ~/.agents/skills/dsh-workflow/scripts/check_notes.py .agents/notes --agents AGENTS.md`（见 `.agents/verify.md`）。

---

## 15. 全局"不要做"
- 不加地图或定位 SDK，不拉瓦片，不联网取任何底图；
- 除 T1 的 `pageSize` 外，不改任何请求字段、请求头、鉴权或加密；
- 不新增第三方依赖（本报告所需能力都在现有 Compose、Room、M3 里）；
- 不实现 iOS 橡皮筋回弹，不自定义 `OverscrollEffect`；
- 不手写底部弹层的动画；
- 不删除 FIF 恢复流程的语义（重试中时禁用"重新登录"）；
- 不把服务端原文（"非法访问"、"-100"）放进卡片正文；
- 遇到报告里没覆盖的取舍，停下来问用户。
