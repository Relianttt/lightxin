---
doc_type: learning
track: pitfall
date: 2026-09-28
slug: running-upload-ok-but-not-credited
component: feature/running
severity: high
tags: [running, sports-api, anti-cheat, upload, verification, pitfall]
---

# 跑步上传返回 OK 却不计入里程

## 问题

2026-09 下旬起，跑步数据上传后服务端不再计入有效公里数。安小信（原版 App）和轻小信（本项目）同时出现，
校内多名同学反映"骑车跑"也不计——说明**不是客户端单方面的问题**，而是服务端判定层收紧。

## 症状

上传接口返回成功，但里程不涨：

| 抓包 | `addExtraCheckNew.do` 响应 |
|---|---|
| 安小信 2026-09-28 18:05 | `{"flag":true,"result":"OK","data":{"id":"e77975287fd9440e8c0524e36c38ef3d"}}` |
| 轻小信 2026-09-28 18:12 | `{"flag":true,"result":"OK","data":{"id":"9fa22fba74e7447787fd47772e9dae3c"}}` |
| 安小信 18:01（同数据重试） | `{"flag":false,"result":"跑步数据重复调用"}` |

**HTTP 层与写入层都成功（还生成了记录 id），失败的是"有效性判定"**——记录被标记为无效，
所以有效公里数不增加。客户端侧对应我们 `RunningRepository` 里那条提示：
`服务器未生成有效跑步记录（id=…），本次里程可能未入账`。

## 没用的做法

1. **只比对请求体结构**：三次上传（5/28 有效、9/28 两次无效）的字段结构完全一致——
   都是 **10 个字段**：1 个空值 + 8 个单块密文(172) + 1 个整串密文。结构一致 ⇒ 结构不是原因。
2. **想用仓库里的 `publicKey2` 解密**：对不上。原版 App 原生侧用的公钥**不是仓库里那两个**，
   页面里也没有下发公钥（HAR 全量搜 `MIGf` / `publicKey` / `JSEncrypt` 均无命中）。
   所以无法直接读出原版 payload 的明文字段名。
3. **假设是速率超标**：曾怀疑 9/28 那条 `1.02km / 14.62km/h` 撞上了新增的 15 km/h 上限，
   于是构造了一条完全合规的记录去验（见下），**结论被推翻**。

## 解法（已确认的部分）

### 1. 有效性阈值是服务端下发的，可以从这里读出来

`POST /mobile/extra/extraDetailInfo.do`（同账号，只读）返回 `data.extraDetail`：

```
mixOnceMile: "1"      单次不少于 1 公里
minSpeed:    "2"      速率下限 2 km/h
maxSpeed:    "15"     速率上限 15 km/h
maxMile:     "10.1"   历史最长
todayMile:   "0"
todayDate:   "2026-09-28"
```

另 `POST /mobile/sportApp/startRunning.do` 返回 `validSettingLJ: "3"`（语义未解出）。

### 2. 控制变量实测：合规记录同样不入账，且列表里查不到

**第一轮**（几何未校准，`tmp/controlled-upload.json`）：
`mile=1.20`、`time=430s`、`speed=10.05km/h`、110 点、`memberId=FREE_RUN`。

- 上传响应：`{"flag":true,"result":"OK","data":{"id":"415199bdb5b244d6823898502cdb1854"}}`
- 上传前后 `todayMile` 都是 `"0"`；`totalEveryDay` 9 月仍只有 9/22、9/13

**自查发现该轮有缺陷**：轨迹是 110 点、段长 1~11.6 米的合成闭合环，**哈弗辛实测几何长度只有 0.706 km**，
而上报 `mile=1.20`——既是"轨迹实际不足 `mixOnceMile=1` 公里"，又与上报里程自相矛盾。

**第二轮**（几何精确校准，`tmp/controlled-upload2.json`）：
先把轨迹按哈弗辛长度**缩放校准到 1200.0 m（与上报 1.20km 偏差 0%）**，其余参数同上，
起点取服务器时间当天 21:44。

- 上传响应：`{"flag":true,"result":"OK","data":{"id":"d0f1d615a3ad4b4bbe1392fcca5a6b2c"}}`
- 上传前后 `todayMile` 仍是 `"0"`；`totalEveryDay` 9 月仍只有 9/22、9/13

**并且经用户在自己的客户端核对：列表里根本看不到这条 1.2km 记录。**

⇒ 两个结论：

1. **上传接口返回的 `id` 不等于记录已入账**，甚至不等于记录已存在——服务端会
   `flag:true` + 返回 id，然后把记录丢掉。
2. **无效判定不由公布的三条阈值决定**（第二轮各项均合规且几何自洽，仍被丢弃），
   判定依据在服务端内部，客户端不可见。


### 3. 一处可疑的体积差异

| | 上传 body | 轨迹字段密文 |
|---|---|---|
| 5/28 那次**有效** | 23456 B | **17752 B** |
| 9/28 安小信**无效** | 10030 B | 5804 B |
| 9/28 轻小信**无效** | 12661 B | ≈6600 B |

按我们 6 位小数坐标格式折算：5804 B ≈ 100 点，17752 B ≈ 320 点。3 公里 100 点 = **约 30 米/点**，
320 点 = 约 10 米/点。**轨迹密度差 3 倍**——若服务端做轨迹合理性校验，这是首要嫌疑。

## 为什么有效

固定所有已知变量（账号、端点、字段结构、速率、里程、时长、认证方式）后仍然无效，
即可判定"公布的阈值"不是判定依据；把"是否入账"作为**唯一可信的成功信号**，
就不会再被上传接口的 `flag:true` 误导。

## 预防

1. **上传成功 ≠ 记录存在 ≠ 里程入账**：验收跑步上传必须核对 `todayMile` / `totalEveryDay`，
   并在客户端界面确认记录可见；绝不能只看 `addExtraCheckNew.do` 的 `flag/result`——
   实测出现过 `flag:true` + 返回 id，但记录既不入账也不可见的情况。
2. **阈值来自服务端，不要硬编码**：`mixOnceMile` / `minSpeed` / `maxSpeed` / `maxDayMile`
   从 `extraDetailInfo.do` 读，服务端随时可改。
3. **轨迹密度对齐原版量级**：`PolylineSampler` 默认 `sampleStepMeters = 30.0` → 3km 仅 101 点，
   正好落在"无效"那两次的量级；原版有效记录约 10 米/点。
4. **轨迹不要重复播同一组点**：`TrajectoryGenerator` 老路径 `baseRoute[index % baseRoute.size]`
   会让 3 公里重复同一组 8 个坐标约 10 遍、点距恒定 40 米，是反作弊校验的首要嫌疑。

## 尚未定位

- 服务端究竟依据什么丢弃记录（轨迹点密度？路径形状/场地白名单？时间分布？`validSettingLJ`？）
  ——**未坐实**。已排除：字段结构、速率区间、里程区间、轨迹几何自洽性。
- **场地维度是当前最大嫌疑**：客户端 JS 中存在"任务有效时间 + `venueList`（场地列表）"的展示分支，
  且 `checkState`（考勤）与场地强相关；若服务端要求轨迹落在注册场地内，合成环线会被丢弃。
- 原版 App 原生侧使用的 RSA 公钥（与仓库 `publicKey`/`publicKey2` 均不同）——**未取得**，
  因此无法解密对照原版 payload 的明文字段。
- `checkList` / `extraCheckList` 类接口在本账号下始终返回空（`total: 0`），
  `extractExerciseDetail.do` 无参调用会挂起（>2 分钟无响应），**无法直接读到记录的 `exerciseState`**，
  只能靠 `todayMile` / `totalEveryDay` 与用户端界面交叉判断。


## 相关

- `docs/接口分析/跑步接口深度分析报告.md` §8（跑步模块接口）
- `codestable/compound/2026-04-22-decision-running-dual-rsa-encryption.md`（双 RSA 约束）
- `feature/running/data/RunningRepository.kt:253`（`uploadSnapshot`，含"未生成有效记录"分支）
- `feature/running/domain/PolylineSampler.kt:27`（`sampleStepMeters = 30.0`）
- `feature/running/domain/TrajectoryGenerator.kt:45,55`（40 米点距 + 循环取点）
- 证据留档：`HAR/安小信跑步无效_2026_09_28_18_05_53.har`、`HAR/轻小信跑步无效_2026_09_28_18_12_36.har`、
  `tmp/controlled-upload.json`（受控实测全量记录）、`tmp/field-diff.txt`（字段/体积对比）
