---
doc_type: decision
category: architecture
status: active
date: 2026-10-18
tags: [running, route-template, import, export, seed]
---

## 背景

路线模板原本只能在本机录制，换机或重装即丢失；同时新安装用户没有可用的默认路线。

## 决定

- **导出**：写 `cacheDir/exports/*.json`，经 `FileProvider` + `ACTION_SEND` 走系统分享；不写剪贴板。
- **导入**：`ActivityResultContracts.OpenDocument` 选择 JSON，兼容两种形态：
  - 导出信封 `{"schemaVersion":1,"app":"lightxin","exportedAtMillis":...,"templates":[...]}`
  - 裸数组（用于手工/种子文件）
- **导入重算**：距离用 `GeoDistance` 逐段累加、质量用 `RouteQualityChecker` 重新评估，不信任文件值；硬闸复用 `RouteTemplateRules`（>20 点、>1km）。不过闸条目列出但不可导入。
- **冲突**：`id` 相同或 `name`（trim）相同。逐条三选：覆盖 / 保留副本（默认）/ 跳过。
  - 覆盖：保留本地 `isDefault / createdAtMillis / lastUsedAtMillis`，替换内容，`source = IMPORTED`。
  - 保留副本：新 `id`，重名追加 ` (导入)`、` (导入 2)`……
  - 跳过：零写入。
- **系统模板**：就是普通模板，名称「系统模板」、`source = SYSTEM`、第一条 `isDefault = true`，可改名可删。仅当首次启动且模板列表为空时从 `assets/route_templates_seed.json` 注入；注入尝试后必写 `filesDir/route_templates_seeded` 标记，删除后永不复活。
- 导入/覆盖一次 `persist()`，沿用 tmp + rename 原子写。
- 新增 `RouteTemplateSource.IMPORTED / SYSTEM`；旧数据反序列化不受影响。

## 理由

- 系统分享与 SAF 不需要存储权限，也不引入依赖。
- 重算距离/质量避免被改过的文件污染默认模板，也能把旧格式兜底。
- 一次性 seed 标记避免"用户删了系统模板，重启又回来"的恼人行为。
- 冲突逐条选择把不可逆的覆盖决定交给用户，而不是静默改名或静默覆盖。

## 考虑过的替代方案

- **剪贴板导入导出**：模板 JSON 是几 KB 到几十 KB，剪贴板不适合长文本，也缺少文件名与分享目标；改用系统分享 + SAF。
- **冲突静默覆盖或静默改名**：实现简单但不可逆，用户无法预期；改为逐条三选并默认"保留副本"。
- **每次启动都注入系统模板**：删除后重启会复活，违背"系统模板就是普通模板"的承诺；用一次性标记文件阻断。
- **导入时信任文件里的距离 / 质量**：被改过的文件会污染默认模板，也可能绕过硬闸；一律按点位重算。

## 后果

- `RouteTemplateCodec` / `TemplateImportResolver` / `RouteTemplateSeed` 均为纯逻辑，单测覆盖。
- 导入页面需要列表页/详情页共用 `RouteTemplateViewModel`（挂在 `RUNNING_ROUTE_SETTINGS` entry）。
- `cacheDir/exports/` 已加入 `res/xml/file_paths.xml`。

## 相关文档

- `codestable/architecture/running-overview.md`
- 代码：`feature/running/domain/RouteTemplateCodec.kt`、`feature/running/data/RouteTemplateStore.kt`、`feature/running/ui/RouteTemplateImportScreen.kt`
