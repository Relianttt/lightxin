---
doc_type: exploration
category: protocol
date: 2026-10-13
slug: sports-h5-protocol-evidence
status: current
area: 智慧运动 / 跑步上传
tags: [running, sports, coordinate, bd09, h5, protocol-evidence]
---

## 为什么值得单独记

`docs/接口分析/跑步接口深度分析报告.md` 与 `HAR/` 都不在当前工作副本里（被 `.gitignore` 主动排除），所以跑步上传链路的若干关键假设长期只有 `running-overview.md` 里一句"来源：docs/..."的转述，无法复核。

本次找到了另一条不依赖抓包的取证路径：**智慧运动模块本身是 H5**，其 JS 明文由服务端直接下发。

## 取证方法

```
curl http://sports.aiit.edu.cn:8082/dist/            # 入口页，标题「智慧体育管理平台」
# 从 app.<hash>.js 的 webpack runtime 里解出全部 56 个 chunk 的 hash 名，逐个下载
# 在 chunk 内 grep 接口名 / 字段名 / 地图 SDK
```

无需登录、无需凭证 —— 静态资源公开。入口页 HTML 只列首屏 chunk，按需加载的 chunk 名字要从 `app.js` 里 `"chunk-<id>":"<hash>"` 映射表还原，否则只能拿到一半。

## 结论

### 1. 服务端坐标系确认是 BD-09

详情页 `RunDetail` 使用 **`BMap`（百度地图 JS SDK）** 绘制轨迹，并在初始化时以 `BMap.Convertor.translate(points, 1, 5, cb)` 做转换 —— 百度的类型码 `1` = GPS(WGS-84)、`5` = BD-09。

这证实了 `CoordinateConverter.wgs84ToBd09()` 的**方向**是对的。`RunningCampus` 中心常量的坐标系归属仍无一手出处，但已不再是纯猜测。

#### 2026-10-14 追加：显示侧是 BD-09 已被实测坐实

一次 Studio 路径模拟器的跑步留下了一个可量化的偏移：喂进去的轨迹本身**已经是 GCJ-02**，而上传链路按 WGS-84 处理、又做了一遍 WGS→GCJ→BD，结果在学校 App 地图上**朝东南偏约 530 m**。同一位置本地算出来的一步 WGS→GCJ 正是南偏 253 m + 东偏 468 m ≈ **531 m、方向东南**——量级和方位都对上了。

结论：

- 服务端存的就是**它显示用的坐标系 = BD-09**，我们真实跑步那条 `wgs84ToBd09` 链路是对的，不用改。
- 偏移来自测试数据，不是代码：拿 GCJ-02 的轨迹喂"原生定位"，等于把 GCJ 这一步做两遍。以后用路径模拟器，**要喂原始 WGS-84**（GPX 原值、谷歌地球读数），别喂转换过的。
- 反过来也确认了 `RunningCampus` 中心常量属于 WGS-84（用户说明来源是 GPX / 谷歌地球），所以内置模拟环线此前标 `isBd09 = true`（原样上传）是错的，会整整偏掉一步 WGS→BD（本地约 1159 m）。已改为 `false`。

#### 端到端复核（同日，已结案）

拿一份**原始 WGS-84 的 GPX 路径**在 Studio 里喂定位、录成模板再模拟提交，官方 App 里轨迹正常落回校内操场。坐标系这条链路到此为止：采集 WGS-84 → 上传 BD-09 → 显示 BD-09，无需再动。

### 2. 轨迹点确实不带时间戳

`initMap()` 解析方式：

```js
JSON.parse(this.basicInfo.runningRoute)
  .map(p => ({ lng: parseFloat(p.longitude), lat: parseFloat(p.latitude) }))
```

只有 `longitude` / `latitude` 两个字段。我们上传的点位结构**没有问题**，"无轨迹"不能归因于缺时间戳。

### 3. 有效性判据是 `state`，理由在 `remark`

记录详情来自 `POST /api/mobile/extra/getExtraCheck.do`，参数 `{checkId}`，响应 `data.extraInfo` + `data.applyInfo`。前端提示逻辑：

```js
"1" === state ? "辛苦了，请继续加油！"
: ("0" === state || "2" === state) ? remark : ""
```

即 **`state=1` 有效；`0`/`2` 无效，且服务端为每条记录存了一句人类可读的 `remark` 说明原因**。这条接口我们尚未实现 —— 它是唯一能直接读到"服务端为什么判无效"的通道。

### 4. 官方 H5 的里程累计规则与我们不同

H5 的 `startTracking`：

```js
if (positions.length > 0) {
  const d = map.getDistance(last, point) / 1000;   // 单位 km
  if (d > 3) basicInfo.mile += d;                  // 注意：>3 米才计
}
positions.push(point);                             // 点一律保留
```

- **相邻点 ≤3 m：不计里程，但点仍然入列**
- 我们（`RunningTracker`）：**<1.5 m 或 >120 m 的段，点直接丢弃**

两种点集分布不同。若服务端按官方口径校验相邻点间距或点数密度，我们的稀疏点集可能被判定异常。

- 速度取的是 **GPS 瞬时速度** `coords.speed * 3.6`，不是 `里程/时长`；我们上传的是平均速度。

### 5. 另有一个明文上传接口，字段名是复数

H5 自己的跑步上传走 `POST /api/mobile/sportApp/addExtractCheckRecord.do`，字段 `extractCheckRecord`，载荷：

```js
{ extraMemberId, startDate, endDate, mile, speed, time, runningRoutes }
```

**轨迹字段是 `runningRoutes`（复数）**，而我们发给 `addExtraCheckNew.do` 的是 `runningRoute`（单数）。两个接口不同，不能据此断定我们写错了 —— 但这是"服务端存不下轨迹"时第一个该排除的名字。H5 全程明文、不做 RSA；RSA 的 `list=` 载荷只出现在原生 App 那条链路上。

### 6. 前端侧单次有效阈值 0.8 km

H5 的 `end()` 里 `isValid = parseFloat(basicInfo.mile) > .8`，与服务端下发的 `mixOnceMile: "1"` 不一致，说明 `mixOnceMile` 是任务要求、0.8 是前端兜底。

## 未能证实的部分

- ~~`RunningCampus.CENTER_LATITUDE/LONGITUDE` 究竟是不是 BD-09。~~ 2026-10-14 已结：用户确认来源是 GPX / 谷歌地球，即 **WGS-84**；显示侧 BD-09 另有一步 531 m 东南偏移的实测佐证（见 §1 追加）。
- 原生 App 的 `addExtraCheckNew.do` 明文载荷字段清单（RSA 公钥加密，无私钥不可解；H5 不走这条）。
- 服务端判"运动轨迹异常"的完整规则。

## 一次没做成的验证

试图在模拟器上用 mock 位置复现"转换后落围栏外"。数学上是成立的：模拟器注入点距校园中心 287 m，经 `wgs84ToBd09` 后变成 987 m（单次转换偏移 1167 m），已出 `RouteQualityChecker` 的 ±600 m 框。

但**模拟器不是有效试验场**：Android 17 模拟器上 `adb emu geo fix` 返回 OK 却不再更新位置，须改用 `cmd location providers add-test-provider gps` + `set-test-provider-enabled true` + `set-test-provider-location gps --location <lat>,<lng>`；即便如此，经我们 3 s / 2 m 的采样过滤后，喂 420 个 3.5 m 步进的点只累计到 0.26 km、均速 1.2 km/h —— 本身已低于服务端 `minSpeed: 2`，结果无法归因。**结论：这条链路必须真机验证。** 测试会话已放弃，未向服务端上传任何数据。

## 现场进展（2026-10-14）

### 已排除的方向

| 假设 | 证据 | 结论 |
|---|---|---|
| 日限额用尽 | 官方 App 回读 `remark = "运动轨迹异常，无效"` | 不是主因 |
| 轨迹点缺时间戳 | H5 `initMap()` 只读 `longitude`/`latitude` | 官方也不带时间戳 |
| 点太稀 / 速度越界 | `mile 1.02`、`speed 6.0–9.51`，全在服务端自己下发的 `2–15` 限值内 | 排除 |
| 坐标系（WGS-84 vs BD-09） | 同一账号分别上传 BD-09 与原样 WGS-84，**两次都是 `state=0 / 运动轨迹异常`** | 排除 |
| 轨迹没存下来 | 回读里 `runningRoute` 是**密文**，说明存下来了 | 排除（此前"地图空白=没存下"是我误判：H5 的 `initMap()` 不套视野，轨迹在 1.2 km 外就整条落在屏幕外） |

### `/api` 上下文走不通，H5 的载荷形态不可照搬

H5 的大一大二上传是 `POST /api/mobile/sportApp/addExtractCheckRecord.do`，明文 JSON、字段 `extractCheckRecord`、轨迹字段复数 `runningRoutes`、成员标识是学号。

但 **`/api/*` 由 `in.aiit.edu.cn/uaac-server` 的 CAS 单点登录保护**（未登录直接 302 跳登录），而我们用的 `/mobile/*` 是另一个靠表单身份字段 + 多 header 鉴权的上下文。实测：

```
POST /mobile/sportApp/addExtractCheckRecord.do   → 404
POST /api/sportApp/addExtractCheckRecord.do      → 302 → uaac-server/login
POST /mobile/extra/addExtraCheckNew.do           → 200
POST /mobile/extra/addExtraCheck.do              → 200（兄弟端点，我们从未调用）
```

`addExtractCheckRecord.do` 在 `/mobile` 下**不存在**，所以 H5 那套明文载荷形态不能直接搬到我们的端点上 —— 之前据此做的 A/B 臂已回退。要用它得先实现 sports `/api` 的 CAS 登录（项目里 `FifSessionManager` 有同类先例）。

### 结案：分段 RSA 的框架不匹配（已证实）

```
mile / speed / time  → 明文 <117 字节 → 单个 RSA 块 → 服务端解得开 → 记录里数值全对
runningRoute         → 明文几 KB → 几十个 128 字节块拼成一个 Base64 串
                     → 服务端解不开 → 无轨迹 + 判「运动轨迹异常」
```

实测确认：同一份数据，`runningRoute` 加密时 `state=0`、改明文后 `state=1`。修复方式与该字段现在的实现见 → [`2026-10-14-decision-running-route-plaintext.md`](2026-10-14-decision-running-route-plaintext.md)。

验证期间临时加过的「跑步轨迹字段不加密」调试开关（`DeveloperPrefs.uploadRouteInPlaintext`）与 H5 端点 A/B 臂都已删除，代码里只留下最终的明文实现。

## 相关文档

- `codestable/architecture/running-overview.md` §6
- `codestable/architecture/location-overview.md`
- `codestable/compound/2026-04-22-decision-no-map-sdk.md`
- `codestable/compound/2026-04-22-decision-running-dual-rsa-encryption.md`
