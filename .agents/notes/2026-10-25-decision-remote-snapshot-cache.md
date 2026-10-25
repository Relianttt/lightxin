---
doc_type: decision
category: architecture
status: active
date: 2026-10-25
tags: [cache, room, labor, credit, offline]
---

## 背景

劳动教育与素质学分的接口都是“整份列表”语义，没有增量；断网或登录失效时整页空白。课表已有成熟做法：按 `userCode` 隔离、用 `AccountSession` 代次判断请求是否还属于当前账号、退出登录不清表。本次给这类“整份快照”页面一个通用缓存层，先显示缓存再后台刷新。

## 决定

- 在既有 Room 库（文件名不变，`version = 2`，`AutoMigration(1, 2)`）新增通用快照表 `remote_snapshot`，主键 `(userCode, snapshotKey)`，值存整份 JSON 与 `fetchedAt`。列名用 `snapshotKey`，避免 SQLite 关键字 `KEY`。
- `SnapshotStore` 在 `Dispatchers.IO` 上读写，读写前后都调用 `AccountSession.requireCurrent`；JSON 解析失败返回 `null`，不抛异常，旧版本字段变化等价于“没有缓存”。
- key 常量为 `labor.summary`、`labor.activities`、`credit.overview`、`credit.records`；值直接存 domain 对象，不为缓存另建映射层。
- 写入时机：只在网络成功后写。劳动教育的总览与第一页都成功才一起写；素质学分的总览与记录各自成功各自写。失败或被拒绝不覆盖旧快照。
- ViewModel 流程：先读缓存填充并标记 `isRefreshing`，再请求；成功后替换、写缓存、清空 `fetchedAt`；失败时有缓存就保留数据并在 header 下显示“更新失败 · 点此重试”，登录失效改挂 `LxSessionBanner`，无缓存才走整页错误。
- `LxPageHeader` 的副标题“更新于 M月d日 HH:mm”只在当前展示的是缓存且本次刷新尚未完全成功时出现。
- 账号代次变化（退出 / 切换账号）时网络结果直接丢弃，不发布到界面、不写缓存。退出登录不清快照表，与课表缓存一致。

## 考虑过的替代方案

- 每页各建一张缓存表：字段贴近页面，但新增页面就要迁移；通用 JSON 快照一个表覆盖所有“整份数据”接口。
- 先网络后缓存：首屏仍然空白，违背“缓存先显”的目的。
- 在缓存里存原始响应体：省一次映射，但接口字段变化会污染展示层；存 domain 对象更稳。

## 后果

劳动教育、素质学分支持离线查看与刷新失败降级。素质学分接口目前没有可靠的登录失效信号（HTTP 401 只映射成一句文案），因此只显示“更新失败”，没有登录失效横幅；这是已知差异，待接口实测形态后再补。

验证：`testDebugUnitTest`（含 `SnapshotStoreTest`、`DatabaseMigrationTest`、`ExamSummaryTest`、`LaborPagingTest`、`TrackProjectionTest`）通过，`lintDebug`、`assembleDebug`、`assembleDebugAndroidTest` 通过。缓存首显、断网降级与一键重登横幅尚未在真机按步骤核验。
