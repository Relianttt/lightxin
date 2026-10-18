---
doc_type: decision
category: architecture
status: active
date: 2026-10-18
tags: [repair, bxjl, auth, notification]
---

## 背景

报修入口来自手机端 `bxjlSjd/*` 家族。抓包确认实际域名是 `http://repair.aiit.edu.cn:8081`，身份字段以 Form 参数与头混合传递；报修状态由后勤端按天级节奏推进。

## 决定

- **基址用抓包确认的 `http://repair.aiit.edu.cn:8081`**（`network_security_config` 放行 cleartext），不再猜 443。
- `AuthInterceptor` 新增 `repair.aiit.edu.cn` 分支：FormBody 追加 `userCode/xh/userType/userName/accessToken`，**不加** `appId`、`access_token`；补 `X-Requested-With`、`Origin`、`Referer: http://repair.aiit.edu.cn:8081/dist-app/`。
- 端点：`getWdbxListForSjd / getBxdXqForSjd / getLctByBxdhForSjd / getBxddForSjd / getBxlxForSjd / getXqByXhForSjd / getSjhForSjd / saveOrUpdateBxdForSjd / updateBxdByIdForSjd / addLcbzForSjd / uploadForSjd`。
- **`timestap` 拼写原样保留**，不改。
- 提交字段：`xqdm / lyId / qyfjh（房间 ID）/ xxdz / sjh / xm / gzms / bxlx（末级 value）/ tpId（逗号拼接）`；`kssj/jssj` 未选不传；身份字段由拦截器注入。
- 图片：相册 `PickVisualMedia`、拍照 `FileProvider`；压缩到 ≤2MB 后转 `data:image/jpeg;base64,...`，顺序上传拿 `data.file.id`，本地限 3 张。上传响应已用真实请求验证为 `{data:{file:{id,fileUrl,fileFullName}}}`。
- 详情/列表的 `fileList[].fileUrl` 是相对路径（`group2/M00/...`），展示优先用 `fileFullName`，否则补 `https://fs.aiit.edu.cn/`。
- Coil 3 只有 `coil-compose` 不会加载网络图，必须同时依赖 `coil-network-okhttp`（本次已补）。
- **不做实况通知 / 角标 / 轮询 / 后台任务**：报修是异步天级任务，状态新鲜度只由「下拉刷新」和「进入详情重拉」驱动。
- 评价区只读展示 `yjfk`，不提供提交入口（未找到闭合写接口）；撤回在 `dqzt ∈ {1,9}`（未接单 / 待处理）时提供，`addLcbz cz=8`；真机抓包确认 `4` 是维修完成，不能撤回。

## 理由

- HAR 里所有报修请求都走 8081，443 没有证据；直接用抓包值减少一次真机试错。
- 天级状态变化不值得常驻通知与轮询，反而增加耗电与权限面。
- 评价写接口缺失时硬做一个本地评价会与服务端不一致，只读更诚实。

## 考虑过的替代方案

- **主用 443、失败再切 8081**：抓包里所有报修请求都走 8081，443 没有证据；直接用抓包值，减少一次真机试错与超时等待。
- **实况通知 / 角标 / 轮询**：报修是异步天级任务，常驻通知与轮询只会增加耗电和权限面，不采用。
- **本地提交评价**：没有找到闭合写接口，本地评价会与服务端不一致；只读展示 `yjfk`。
- **自己拼图片 URL 或跳过上传**：服务端要求 `tpId`，必须拿 `file.id`；按抓包顺序上传再拼接。

## 后果

- 报修图片 base64 走表单字段，单请求体较大；本地已限 3 张 × ≤2MB。
- 若 8081 后续下线，只需改 `BASE_REPAIR` 与 `network_security_config`。
- 列表页/详情页不申请通知权限，不接 `LiveActivityNotifier`。

## 相关文档

- `codestable/architecture/repair-overview.md`
- 代码：`feature/repair/data/RepairApi.kt`、`feature/repair/data/RepairRepository.kt`
