---
doc_type: architecture
slug: repair-overview
scope: 报修模块，覆盖 repair.aiit.edu.cn:8081 手机端 bxjlSjd 家族鉴权、列表/详情/流程条、三级地址与类型级联、base64 图片上传、撤回
summary: repair 以 RepairRepository + RepairCascade 为核心；提交字段与抓包一致，图片转 data URL base64 后拿 file.id；状态只靠下拉刷新与进详情重拉，不做实况通知/轮询
status: current
last_reviewed: 2026-10-18
tags: [repair, bxjl, retrofit, cascade, image-upload]
depends_on: [network-overview, navigation-overview]
---

# 报修模块总览

## 0. 术语

- **bxjlSjd 家族** — 手机端报修接口前缀 `bxjlSjd/*`，服务端为 `repair.aiit.edu.cn:8081`
- **地址树** — `getBxddForSjd.do` 返回扁平 `{name,parent,value}`；`parent="0"` 为根，按 parent 组装校区→楼栋→房间
- **类型树** — `getBxlxForSjd.do` 同结构，提交时取末级 `value`
- **tpId** — 已上传图片 file.id 的逗号拼接
- **已读标记** — `updateBxdById.do { id, qtYqbz:1 }`，进详情前静默调用，失败忽略
- **撤回** — `addLcbz.do { bxdh, cz:8, wxry:"", userCode }`

## 1. 定位与受众

`feature/repair/` 提供：报修列表、新建（地址/类型级联 + 图片）、详情（图片 + 流程条 + 只读评价）、撤回。

读者：要改报修协议、级联逻辑、图片上传或状态展示的人。

## 2. 结构与交互

### 2.1 鉴权

- `ApiConstants.BASE_REPAIR = http://repair.aiit.edu.cn:8081`（抓包确认；`network_security_config` 放行 cleartext）
- `NetworkModule.@RepairRetrofit` 复用主 `OkHttpClient`
- `AuthInterceptor` 的 `repair.aiit.edu.cn` 分支：FormBody 追加 `userCode / xh / userType / userName / accessToken`，补 `X-Requested-With / Origin / Referer: .../dist-app/`；不加 `appId / access_token`

### 2.2 接口面

| 用途 | 端点 |
|---|---|
| 我的报修 | `bxjlSjd/getWdbxListForSjd.do` |
| 详情 | `bxjlSjd/getBxdXqForSjd.do {bxdh}` |
| 流程条 | `bxjlSjd/getLctByBxdhForSjd.do {bxdh}` |
| 地址 / 类型 | `bxjlSjd/getBxddForSjd.do` / `bxjlSjd/getBxlxForSjd.do` |
| 默认校区 / 联系人 | `bxjlSjd/getXqByXhForSjd.do {xh}` / `bxjlSjd/getSjhForSjd.do {userCode}` |
| 提交 | `bxjlSjd/saveOrUpdateBxdForSjd.do` |
| 图片 | `bxjlSjd/uploadForSjd.do?timestap=<ms>`（拼写原样保留） |
| 已读 / 撤回 | `bxjlSjd/updateBxdByIdForSjd.do` / `bxjlSjd/addLcbzForSjd.do` |

响应统一双包 `{ data: { data: ... } }`，字段名照抄服务端（列表 `dqztMc`，详情 `dqztmc`）。

### 2.3 提交装配

`RepairSubmitBuilder`（纯逻辑）：

- 必填/格式：地点三项、`xxdz`、`sjh ^1\d{10}$`、`bxlx`、`gzms`
- 字段：`xqdm / lyId / qyfjh / xxdz / sjh / xm / gzms / bxlx / tpId`
- `kssj/jssj` 未选不传；身份字段由拦截器注入

图片：相册 `PickVisualMedia`、拍照 `FileProvider`；压缩到 ≤2MB 后转 `data:image/jpeg;base64,...`，顺序上传拿 `data.file.id`，本地限 3 张。真机验证响应为 `{data:{file:{id,fileUrl,fileFullName}}}`。

图片 URL：`fileList[].fileUrl` 是相对路径，展示优先 `fileFullName`，否则补 `https://fs.aiit.edu.cn/`。Coil 3 需额外依赖 `coil-network-okhttp` 才能加载网络图。

### 2.4 UI 与状态新鲜度

- 列表：下拉刷新；行 = 类型图标/首图 + 故障描述 + 类型/地址/时间 + 状态点；点击先静默已读再进详情。默认停在“进行中”（0 条也保留），空态给“新建报修”入口
- 新建：只自动带唯一校区，**不预填楼栋 / 房间**；楼栋→房间两段选择、详细地址（必填）、手机号、类型、故障描述、图片最多 3 张
- 详情：状态块 + 现场图 + 故障描述 + 粗值信息网格 + 流程 + 只读评价 + 撤回
- 页头与内容统一 24dp 缩进，`LxPageHeader` 不自带水平 padding

**不申请通知权限、不接 `LiveActivityNotifier`、不轮询**。状态只在「下拉刷新」和「进入详情重拉」时更新。

拍照入口目前对 `ActivityNotFoundException` 做兜底（提示从相册选择）；本机 Pixel 4 / Android 13 仍无法拉起系统相机，属已知问题，见 `.agents/notes/2026-10-18-learning-camera-intent-not-found.md`。

## 3. 数据与状态

| 类型 | 含义 | 位置 |
|---|---|---|
| `RepairSummary` | 列表项 | `data/RepairRepository.kt` |
| `RepairDetail` | 详情 + 流程 + 图片 | 同上 |
| `RepairNode` | 地址/类型扁平节点 | `domain/RepairModels.kt` |
| `RepairDraft` | 提交草稿 | 同上 |
| `RepairCascade` | 根/子/路径组装 | 同上 |
| `RepairSubmitBuilder` | 装配 + 校验 | 同上 |

## 4. 关键决策

见 `.agents/notes/2026-10-18-decision-repair-mobile-family-auth.md`：8081 基址、字段级鉴权、`timestap` 原样、无实况通知、评价只读。

## 5. 已知约束 / 边界

- 地址树按 3 级使用；若服务端改成 4 级，需要调整 `xqdm/lyId/qyfjh` 的映射
- 图片 base64 表单体积较大，本地收紧到 3 张 × ≤2MB
- 撤回仅在 `dqzt ∈ {1,9}`（未接单 / 待处理）显示；`4` 是维修完成，不可撤回
- 没有评价写接口：`yjfk` 存在时只读展示

## 6. 相关文档

- 鉴权：`codestable/architecture/network-overview.md`
- 导航与共享元素：`codestable/architecture/navigation-overview.md`
