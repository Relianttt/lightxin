---
doc_type: decision
category: constraint
status: active
date: 2026-10-17
tags: [running, rsa, encryption, sports-api, protocol]
---

## 背景

历史约束 [`2026-04-22-decision-running-dual-rsa-encryption.md`](../../codestable/compound/2026-04-22-decision-running-dual-rsa-encryption.md) 记的是「跑步上传时字段名和字段值**都**要用 `publicKey2` RSA 加密」，来源是原生 App 抓包，长期无法复核（`HAR/` 被 `.gitignore` 排除，docs 里只有转述）。

2026-09 下旬起跑步记录持续被判无效：上传返回 `flag:true` + `result:"OK"` + 有效 id，官方 App 里没有轨迹、`todayMile` 不涨。当时按轨迹密度、速率区间、里程区间、轨迹几何自洽性逐项排除，均未定位（[`2026-09-28-learning-running-upload-ok-but-not-credited.md`](../../codestable/compound/2026-09-28-learning-running-upload-ok-but-not-credited.md)），并留下一条可疑线索：「8 个单块密文(172) + 1 个整串密文」。

2026-10-14，同源的 fork 仓库（`SDX8/lightxin`，协议层与本仓库完全一致）用控制变量实测把原因定位到这条加密规则本身：**同一账号、同一份轨迹，只改 `runningRoute` 一个值的加密方式** —— 密文判无效，明文判有效。

## 决定

- **`runningRoute` 的字段值以明文上传，不做 RSA 加密。** 全部字段名、以及除轨迹外的全部字段值，仍走 `RSAUtils.encryptSportData()`。
- 不为这条规则保留运行期开关或 A/B 臂：验证阶段的一方性证据已足够，留在发布包里只会给其他同学开出误操作面。
- 上传后回读服务端判定（`mobile/extra/getExtraCheck.do` 的 `state` / `remark`）作为唯一可信的成功信号；`addExtraCheckNew.do` 的 `flag/result/id` 只代表记录建了。
- 上传成功后的回读与收尾必须挂在 `viewModelScope`，不能留在界面的 `rememberCoroutineScope` 里 —— 跳结果页会取消该作用域。

载荷形态：

```json
[{"<enc \"exerciseId\">":"<enc value>", ..., "<enc \"runningRoute\">":"[{\"latitude\":\"31.138930\",\"longitude\":\"118.628131\"}, ...]", ...}]
```

## 理由

### 证据（fork 仓库实测，2026-10-14）

| `runningRoute` 值 | 服务端结果 |
|---|---|
| RSA 密文 | 记录建成、返回有效 id、`mile`/`speed`/`time` 全部正确入库，但 `state=0`、`remark="运动轨迹异常，无效"`、官方 App 无轨迹 |
| 明文 | **`state=1`，判有效** |

### 机理

`RSA/None/PKCS1Padding` + 1024 位公钥：每块最多 117 字节明文 → 128 字节密文 → Base64 后 **172 字符/块**，多块直接拼接成一个 Base64 串。

- `mile` / `speed` / `time` / `memberId` 明文都只有几十字节 → **单个 RSA 块**（正好 172 字符）→ 服务端按单块解，一切正常。这解释了为什么这些字段的值在记录里始终是对的，也和 09-28 那份抓包里的「8 个单块密文(172)」完全对上。
- `runningRoute` 是几 KB（127–488 个点，每点约 46 字节）→ 几十上百个块拼成一个 Base64 串。服务端若按「整串 = 一个块」或不同的分块约定处理，解出来就是垃圾 → 轨迹解析失败 → 判「运动轨迹异常」、地图画不出来。

也就是说：**服务端能解开短字段、解不开长字段**，症状恰好是「数值都对、只有轨迹不行」。

### 与 05-28 旧证据的张力（未解，但仍不改结论）

09-28 的 note 里有反例：5 月 28 日那次**判有效**的官方 App 上传，其轨迹字段密文是 17752 B，按 172 字符/块折算约 103 块 ≈ 12 KB 明文 ≈ 260–320 点 —— 说明**当时服务端是能解开多块拼接密文的**。

因此更准确的说法不是「这条规则一直是错的」，而是：**服务端在 9 月前后改了轨迹字段的解码方式或校验口径，从此只认单块/明文轨迹**。这同时解释了为什么官方 App 自己也从 9 月底开始不计里程（同学反馈「骑车跑也不计」）。该推断无法从客户端求证，作为观察记在此处；客户端侧要做的仍然是明文。

### 我们自己的验证状态

代码已落地，单测覆盖加密边界与回读解析，但**本仓库自己还没有跑过一次真机上传并读到 `state=1`**。回读通道已随本次改动补上，一次真实跑步上传即可闭环：结果页出现「服务端判定：有效」即证实。

已有的一手证据：用户用同源 fork 的安装包（同一套载荷实现）完成过真实跑步，服务端确认有效、轨迹可显示。因此本条结论不是纸上推理，剩下的是确认本仓库的序列化产物与之完全一致（JSON 组装代码未改动，键顺序与 org.json 调用与改动前逐行相同）。

## 考虑过的替代方案

- **换 H5 的明文端点**（`mobile/sportApp/addExtractCheckRecord.do`，轨迹字段是复数 `runningRoutes`）：该端点只在 `/api` 上下文存在，而 `/api/*` 受 `in.aiit.edu.cn/uaac-server` 的 CAS 单点登录保护；我们的 `/mobile` 上下文里它是 404。要用得先实现 sports 侧的 CAS 登录，成本远高于改一个字段的加密方式，不采用。
- **换坐标系**：同一账号分别上传 BD-09 与原样 WGS-84，两次都判无效，已排除。坐标系只决定轨迹画在哪，不影响有效性。
- **把长字段拆成多个 RSA 块并改变传输结构**：需要服务端配合改解码方式，客户端改不了。
- **保留「轨迹字段不加密」的调试开关**：能继续做 A/B，但开关留在发布包里等于把协议差异交给用户误触，验证完成后即删除。

## 后果

- 上传链路里的轨迹坐标**明文可读**。它不含身份证信息，只有校园跑步的位置，但这是一处隐私面：改动服务端协议时应重新评估，不要把其他字段也顺手放明文。
- `AGENTS.md` 的硬约束已由「字段名和字段值都加密」改写为「键名全加密、值除 `runningRoute` 外全加密」。
- 上传路径不再做校园围栏质量检查（fork 仓库实测：轨迹明显在校外仍判有效，服务端不做围栏校验，那条告警只制造噪音）。模板录制的 `RouteQualityChecker` 保留，那是本地体验。
- 结果页会多出一行「服务端判定」。回读失败时静默降级为不显示，不影响上传结果本身。
- 今后「上传成功」的判定标准是三段：`flag/result` 正常 → 有有效 id → **回读 `state=1`**。只看前两段就会重现 09-28 那次误判。
- 剩余风险：09-28 的 note 记录过轨迹密度疑似被校验（官方有效记录约 10 米/点，`PolylineSampler` 默认 30 米/点）。真实 GPS 采集点是密的，不受影响；模拟提交若仍被判无效，下一个要回看的就是点密度，而不是加密方式。

## 相关文档

- 被部分修正：[`2026-04-22-decision-running-dual-rsa-encryption.md`](../../codestable/compound/2026-04-22-decision-running-dual-rsa-encryption.md)
- 现象与排除过程：[`2026-09-28-learning-running-upload-ok-but-not-credited.md`](../../codestable/compound/2026-09-28-learning-running-upload-ok-but-not-credited.md)
- 本仓库既有约束：[`2026-10-17-decision-template-ping-pong-replay.md`](2026-10-17-decision-template-ping-pong-replay.md)（其中「RSA 字段名/字段值双加密保持不变」一句已被本 note 修正为轨迹除外）
- 代码：`feature/running/data/RunningEncryption.kt`、`feature/running/data/SportsGradeMapper.parseCheckRecord()`
- 来源：`SDX8/lightxin` 提交 `d6dbe37`（同源 fork 的实测与取证文档）
