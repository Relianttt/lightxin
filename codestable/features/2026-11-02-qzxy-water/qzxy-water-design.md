---
doc_type: feature-design
feature: qzxy-water
slug: qzxy-water
requirement: null
status: approved
summary: 接入趣智校园账号，用经典蓝牙 SPP 控制本校饮水机（bigTypeId=5）与公寓热水表（bigTypeId=4），完成开阀、结束、结算全流程。
tags: [qzxy, bluetooth, water, network]
created: 2026-11-02
---

# 趣智校园 饮水 / 热水 执行方案

**接口依据**：[`docs/接口分析/趣智校园分析报告.md`](../../接口分析/趣智校园分析报告.md)（下文简称「报告」），第 9 节为真机实证，**以第 9 节为准**。
**抓包**：`HAR/趣智校园饮水抓包.har`、`HAR/趣智校园搜索设备.har`（本地文件，已被 git 忽略，**禁止提交**，也不要把其中的 loginCode / 手机号写进代码、测试或文档）。
**进度**：同目录 `qzxy-water-checklist.yaml`，每完成一步把 `status` 改为 `done` 并填写 `evidence`。
**验收**：同目录 `qzxy-water-acceptance.md`（真机九项留待第二天实测）。

---

## 0. 执行者须知（必读）

0. **先做 §10（主题合并与死资源清理）并单独提交**：当前 `lintDebug` 因 `values-night-v29` 的 `NewApi` 报错而失败，不先修，后面每个阶段的 lint 验证都过不了。
1. 这是**会真实扣钱**的功能。`rateOrder` 成功即产生预扣；任何「开阀成功但没结算」的路径都必须留下可恢复的记录（见 §5.2 不变量、§5.3）。
2. **协议必须与官方 App 一致**（AGENTS.md 铁律）：字段名、字段集合、签名白名单、大小写一个都不能自作主张。下文给出的字段表来自 HAR 逐条核对。
3. **不得**复用现有主 `OkHttpClient`：其 `AuthInterceptor` 会往 Form 里注入学校接口的 `access_token` 等字段，会污染趣智请求。
4. Gson 响应类**不要用泛型 `BaseResponse<T>`**，每个接口写具体类（R8 会擦除 suspend 泛型签名，见 `.agents/notes/2026-10-25-learning-gson-typetoken-r8.md`）。响应类放 `feature/water/data/`，需要 Gson 落盘的本地对象放 `feature/water/domain/`（这两个包已被 `proguard-rules.pro` keep）。
5. 每个阶段结束都要跑该阶段写明的验证命令，结果写进 checklist 的 `evidence`。构建命令：
   `JAVA_HOME=/opt/android-studio/jbr ./gradlew -Dorg.gradle.java.home=/opt/android-studio/jbr <任务>`
6. 遇到本文没写、报告也没写的协议细节：**停下来记录问题**，不要猜。标记为「待真机验证」的点不要在代码里写死成"已确认"的注释。

---

## 1. 范围

### 做

| 功能 | 说明 |
|---|---|
| 趣智账号登录 | 独立于学校账号；密码登录 + 短信验证码登录 |
| 余额 | `GET /account/wallet` |
| 找设备 | 扫二维码 / 手动输入设备号 / 最近使用设备列表 |
| 设备控制 | `bigTypeId ∈ {4, 5}` 且 `communicationTypeId == 0` 的离线表，经典蓝牙 SPP |
| 结算 | 结束用水 → 上传消费数据 → 写回设备 → 展示本次扣费 |
| 未结算恢复 | App 被杀 / 断连后，再次进入可继续结算 |

### 不做（v1）

- 蓝牙搜索附近设备（`SearchDeviceListActivity` 那套；本校设备的蓝牙广播名未知，后续再加）
- 4G `tcpDevice/*` 通道、BLE GATT 通道、MQTT
- 优惠券、广告、账单、充值、修改密码
- 前台服务 / 后台保活

### 默认产品决定（用户未另行指定时按此执行）

- 入口放在「更多功能」页，新增一行「趣智校园」。
- 趣智账号与学校账号完全独立；退出学校账号**不**清趣智会话。
- 登录会把官方 App 的会话顶掉（报告 §3.4），登录页不额外解释（UI 约定：少写说明），但在 README/note 里记录。

---

## 2. 包结构与文件清单

```
core/network/
  ApiConstants.kt          + BASE_QZXY、QZXY_VERSION、QZXY_CONFIG_KEYS
  NetworkModule.kt         + @QzxyOkHttpClient / @QzxyRetrofit
  QzxyInterceptor.kt       新增：注入公共参数与请求头
core/auth/
  QzxySessionStore.kt      新增：趣智会话（DataStore，独立文件名 "qzxy_session"）
feature/water/
  data/
    QzxyCrypto.kt          密码摘要 / 短信 secret / 签名
    QzxyApi.kt             Retrofit 接口
    QzxyResponses.kt       各接口具体响应类（无泛型）
    QzxyRepository.kt      HTTP 封装、错误映射
    WaterLocalStore.kt     最近设备 + 未结算订单（DataStore，文件名 "qzxy_water"）
    bt/QzxyFrame.kt        帧编解码、状态解析、0x86 载荷（纯函数）
    bt/SppTransport.kt     RFCOMM 连接与收发
  domain/
    WaterModels.kt         WaterDevice / DeviceStatus / ActiveOrder / SettleResult / RecentDevice
    WaterOrderRunner.kt    开阀 / 结算编排（只依赖接口，可单测）
    WaterFormat.kt         金额 / 设备名 / 时段格式化（§7.2）
    DeviceCode.kt          parseDeviceCode（§7.8）
  ui/
    QzxyLoginScreen.kt
    WaterHomeScreen.kt
    WaterDeviceScreen.kt
    WaterScanScreen.kt
    WaterViewModels.kt
core/designsystem/component/
  QrCodeScanner.kt         从 AiClassScanScreen 抽出的通用扫码组件（见 §7.6）
navigation/
  Routes.kt / NavGraph.kt  新路由
app/src/test/java/com/lightxin/feature/water/
  QzxyCryptoTest.kt / QzxyHarReplayTest.kt / QzxyFrameTest.kt / WaterOrderRunnerTest.kt
  QzxyInterceptorTest.kt / WaterFormatTest.kt / DeviceCodeTest.kt
```

---

## 3. 阶段一：协议纯函数（无 Android 依赖）

### 3.1 `QzxyCrypto`

```kotlin
object QzxyCrypto {
    fun md5Hex(s: String): String                       // 32 位小写
    fun passwordDigest(plain: String): String           // md5Hex(plain).takeLast(10).uppercase()
    fun smsSecret(phone: String): String                // md5Hex(phone.take(3) + phone.takeLast(4) + "klcx")
    fun sign(loginCode: String, fields: Map<String, String>): String
}
```

`sign` 算法（报告 §2.2，HAR 6/6 已复核）：

```
raw  = fields 按 key 字典序升序拼接 k1v1k2v2…（无分隔符）
sign = md5Hex( md5Hex(raw + "&key=" + loginCode) + "&key=sign-kailu-855c74a88b8b494187c99b08b8c9a744" )
```

**签名白名单是硬编码的，不是"全部参数"**：

| 接口 | 参与签名的字段（只有这些） |
|---|---|
| `bluetooth/rateOrder` | `deviceId`, `randomNumber`, `telephone`, `xfModel` |
| `bluetooth/data` | `loginCode`, `telephone`, `xfData` |
| `bluetooth/fail` | `consumeDate`, `loginCode`, `telephone`（等级 B，未抓包） |

`macType / bigTypeId / smallTypeId / protocolType / accountId` 不参与签名。

### 3.2 `QzxyCryptoTest` 固定向量（已用 Python 独立算出）

| 输入 | 期望 |
|---|---|
| `passwordDigest("mypassword")` | `4BC85B3E44` |
| `smsSecret("13800000000")` | `md5Hex("1380000klcx")` = `18cd336d35b00ab76974f7052d6732a6` |
| `sign("a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6", {deviceId=915, randomNumber=69020000, telephone=13800001111, xfModel=0})` | `310b0d8ea38113dee45b71a38700d490` |

### 3.3 `QzxyHarReplayTest`（本地回放，CI 自动跳过）

- 读取 `../HAR/趣智校园饮水抓包.har` 与 `../HAR/趣智校园搜索设备.har`（JVM 测试工作目录是 `app/`）；文件不存在时 `Assume.assumeTrue(false)` 跳过。
- 遍历 `v3-api.china-qzxy.cn` 的 `rateOrder` 与 `upload/bluetooth/data` POST，解析 form body，按 §3.1 白名单重算签名，断言等于请求里的 `signature`（共 6 条，必须全部命中）。
- 同时断言请求的字段集合等于 §4.4 末尾列出的集合（防止以后改丢字段）。
- 测试代码里**不要**出现任何 HAR 中的真实值。

### 3.4 `QzxyFrame`（帧协议，报告 §9.4–9.7）

线上文本：`'#' + HEX + '\n'`（ASCII）。**发送用小写 hex**，设备回大写，解析要大小写都接受。

```
请求帧: 60 00 len 80 cmd 00 data… crc 16
应答帧: 60 00 len 81 cmd 00 data… crc 16
len   = data.size + 3
总长  = len + 5
crc   = (Σdata + frame[3] + cmd) and 0xFF      // frame[3] 请求 0x80、应答 0x81
```

```kotlin
object QzxyFrame {
    const val CMD_STATUS = 0x23; const val CMD_RATE = 0x21; const val CMD_CLOSE = 0x22
    const val CMD_COLLECT = 0x85; const val CMD_ACK = 0x86
    fun encode(cmd: Int, data: ByteArray): ByteArray          // 返回含 '#' 和 '\n' 的 ASCII 字节
    fun decodeLine(line: String): Frame?                       // 去 '#'、trim、hex 解码、校验头尾/长度/crc；失败返回 null
    fun parseStatus(payload: ByteArray): DeviceStatus          // 见下
    fun ackPayload(xfData: ByteArray): ByteArray               // 见下
}
data class Frame(val direction: Int, val cmd: Int, val data: ByteArray)
```

各命令载荷：

| cmd | 请求 data | 应答成功判定 |
|---|---|---|
| `0x23` 查状态 | `[0x00]` | `data[0] == 0x80`，`data.size >= 31` |
| `0x21` 写费率 | `hexToBytes(rateOrder.downData)` | `data[0] == 0x80` |
| `0x22` 关阀 | `[0x00]` | `data[0] == 0x80` |
| `0x85` 采集 | `[0x00]` | `data[0] == 0x80`；**整个 data 就是 xfData**（以 `80` 开头，42 字节） |
| `0x86` 写回 | `ackPayload(xfData)` | `data[0] == 0x80` |

`parseStatus(p)`（只支持 ≥31 字节布局；<31 抛 `UnsupportedProtocol`，v1 不实现 28 字节布局）：

| 字段 | 取法 | HTTP 传值 |
|---|---|---|
| `protocolType` | `p[19]` | 2 位小写 hex，如 `"10"` |
| `randomNumber` | `p[24..27]` | 8 位小写 hex，如 `"69020000"` |
| `state` | `p[28]` | 0 空闲 / 1 使用中 / 3、6 待结算 |
| `devType`, `a1` | `p[29]`, `p[30]` | `macType = "%02x%02x".format(devType, a1)` |
| `acct` | `p[11..12]` 大端 u16 | 与 `accountId and 0xFFFF` 比较判断是否本人 |

`ackPayload(xf)` = `xf[1..18]`（18 字节）+ `xf[19..22]` **字节反转**（xfData 里是小端 u32，写回为大端），共 22 字节。

### 3.5 `QzxyFrameTest`

- `encode(0x23, byteArrayOf(0x00))` 转成字符串后 == `"#60000480230000a316\n"`（帧字节 `60 00 04 80 23 00 00 a3 16`，共 9 字节）。
- `decodeLine` 能解大写、小写、带 `\r`；crc 错、尾字节错、长度错都返回 `null`。
- 用合成的 48 字节状态载荷测 `parseStatus`：`p[19]=0x10, p[24..27]=69 02 00 00, p[28]=0, p[29]=0x00, p[30]=0x39` → `protocolType="10"`, `randomNumber="69020000"`, `macType="0039"`。
- `ackPayload` 用这条 xfData（结构来自报告 §9.10，金额/设备号是公开示例值）：
  `80261101233444000001fa0000039300002de50200000002000013880000003200000032c47f0e269cb1`
  期望：`261101233444000001fa0000039300002de500000002`。

**验证**：`./gradlew testDebugUnitTest --tests "com.lightxin.feature.water.*"`

---

## 4. 阶段二：网络层

### 4.1 常量

```kotlin
const val BASE_QZXY = "https://v3-api.china-qzxy.cn"
const val QZXY_VERSION = "6.5.28"
const val QZXY_CONFIG_KEYS = "module_list,advertise_type,question_list,service_phone_list,banner_list_app,activity_list_app,aliCard_popup_config"
```

### 4.2 `QzxySessionStore`（`core/auth/`）

字段：`telephone, loginCode, userId, accountId, projectId, name`。提供 `suspend fun current(): QzxySession?`、`save(...)`、`clear()`、`val sessionFlow`。
`loginCode` 取登录响应的 `v3LoginCode ?: loginCode`。不存密码。

### 4.3 `QzxyInterceptor` 与独立 OkHttpClient

- `@QzxyOkHttpClient`：新 `OkHttpClient.Builder()`，**只**加 `QzxyInterceptor`，超时 15 s。
- 所有请求加头：`User-Agent: okhttp/4.2.2`。有会话时再加 `Config-Project: {projectId}`、`Config-Keys: {QZXY_CONFIG_KEYS}`。
- 路径为 `/user/login`、`/user/verification/code/get`、`/user/registerAndLogin` 时**不注入**公共参数。
- 其余请求注入公共参数（已存在同名字段则不覆盖）：

```
loginCode, userId, accountId, projectId, telephone, telPhone(=telephone), phoneSystem=android, version=6.5.28
```

  GET → 追加到 query；`FormBody` POST → 追加到 body。

- 把「合并参数」写成一个纯函数（例如 `mergeCommon(existing: List<Pair>, common: Map): List<Pair>`）供 `QzxyInterceptorTest` 直接测，不必起 MockWebServer。

### 4.4 接口与字段集合（HAR 核对后的**完整**字段集合）

| 接口 | 方法 | 业务字段（公共字段由拦截器补） |
|---|---|---|
| `/user/login` | POST form | `telephone, password(=passwordDigest), phoneSystem=android, type=0, identifier=(空串), version` |
| `/user/verification/code/get` | GET | `telephone, typeId=3, platform=1, secret(=smsSecret)` |
| `/user/registerAndLogin` | POST form | `telephone, smsCode, type=5, phoneSystem=android, version` |
| `/account/wallet` | GET | （无） |
| `/device/info/mac` | GET | `macAddress`（带冒号大写，如 `C4:7F:0E:26:9C:B1`）, `isNew=1`, `isHaier=0` |
| `/order/downRate/bluetooth/rateOrder` | POST form | `deviceId, macAddress(去冒号大写), macType, bigTypeId, smallTypeId, protocolType, randomNumber, xfModel=0, signature` |
| `/order/upload/bluetooth/data` | POST form | `protocolType, randomNumber, xfData(小写), signature` |
| `/order/upload/bluetooth/fail` | POST form | `consumeDate, signature`（等级 B） |

拦截器补齐后，`rateOrder` 与 `upload/data` 的最终字段集合必须分别等于：

```
rateOrder: accountId bigTypeId deviceId loginCode macAddress macType phoneSystem projectId protocolType
           randomNumber signature smallTypeId telPhone telephone userId version xfModel
data:      accountId loginCode phoneSystem projectId protocolType randomNumber signature telPhone
           telephone userId version xfData
```

### 4.5 关键响应字段

| 接口 | 用到的字段 |
|---|---|
| login / registerAndLogin | `data.loginCode`, `data.v3LoginCode`, `data.userId`, `data.telephone`, `data.userAccount.{projectId, accountId, name}` |
| wallet | `data.money`（字符串，元，如 `"14.110"`） |
| device/info/mac | `deviceId, deviceName, bigTypeId, smallTypeId, snCode, macAddress, communicationTypeId, isUse, withholdMoney(元), deviceUseTimeList[{startTime,endTime}]` |
| rateOrder | `downData`(96 位 hex), `rate`(厘), `preDeductMoney`(字符串，元), `consumeDate`(`yyyyMMddHHmmss`) |
| upload/data | `consumeMoney`(**厘**), `preDeductMoneyAfter`(厘), `orderNo`, `consumeTime`。`clData` **忽略**（官方 App 不用，报告 §9.7） |

### 4.6 `QzxyRepository` 错误映射

- 判成功只看 body：`success == true && errorCode == 0`。HTTP 一律 200。
- 已知码：`12` 手机号或密码错误、`46` 验证码错误、`29` 验证码已失效、`53` 请勿频繁获取验证码、`307` 设备使用中、`4004` 设备不存在、`170` 签名错误、`226` 加密校验失败。其余直接展示 `errorMessage`。
- 会话失效：非登录接口返回 `success=false` 且 `errorMessage` 含「登录」「失效」「过期」「请重新」之一 → 清 `QzxySessionStore`，抛 `QzxySessionExpired`，UI 回登录页。首次遇到时把实际 `errorCode` 记进 note。
- `IOException` → 「网络异常，请检查连接」。

**验证**：`./gradlew testDebugUnitTest --tests "com.lightxin.feature.water.*"` + 把 `QzxyHarReplayTest` 跑通（字段集合与签名逐字段对比，满足 verify.md「网络协议字段」要求）。

---

## 5. 阶段三：蓝牙传输与订单编排

### 5.1 `SppTransport`

```kotlin
interface WaterTransport {
    suspend fun connect(mac: String)                         // mac 带冒号大写
    suspend fun request(cmd: Int, data: ByteArray, timeoutMs: Long = 5_000): Frame
    fun close()
}
```

实现要点：

- `BluetoothAdapter.cancelDiscovery()` → `adapter.getRemoteDevice(mac)` → `createInsecureRfcommSocketToServiceRecord(UUID 00001101-0000-1000-8000-00805F9B34FB)` → `connect()`（`Dispatchers.IO`，外层 `withTimeout(10_000)`，超时调用 `socket.close()` 打断阻塞）。
- insecure 连接失败再试一次 `createRfcommSocketToServiceRecord`（secure，可能弹系统配对框）。仍失败报「连接设备失败」。**哪种能连通属待真机验证项**，结果写进 note。
- 读：一个协程循环读 `InputStream`，按 `\n` 切行，`QzxyFrame.decodeLine` 解出的帧发进 `Channel<Frame>`；`request` 写入后在 channel 上等待 `direction==0x81 && cmd==目标cmd` 的帧，其它帧打日志丢弃（设备重连时可能主动推 `0x22/0x85`，报告 §4.2）。
- `request` 用 `Mutex` 串行化。每次发送打 `Log.d("Qzxy", "→ #...")` / `"← #..."` 便于真机对照（release 也无敏感信息：帧内只有设备号/账号尾号）。

### 5.2 `WaterOrderRunner`（domain，依赖 `WaterTransport` + 一个 `WaterOrderApi` 接口，便于用假实现单测）

**开阀 `start(device)`**：

```
1. transport.connect(device.mac)
2. s = parseStatus(request(0x23,[0]))
   - s.state == 0                         → 继续
   - s.state != 0 且 s.acct == 本人        → 返回 NeedSettle（UI 引导先结算）
   - s.state != 0 且非本人                 → 失败「设备使用中」
3. r = api.rateOrder(device, s)           // 用这次 0x23 的 randomNumber / protocolType / macType
4. ActiveOrder(mac, deviceId, deviceName, bigTypeId, consumeDate=r.consumeDate, startedAt) 写入 WaterLocalStore
5. request(0x21, hex(r.downData))
   - 失败 → api.uploadFail(r.consumeDate)；成功后清 ActiveOrder；返回失败「开阀失败，已撤销」
     uploadFail 也失败 → 保留 ActiveOrder，返回失败「开阀失败，请稍后在设备页结算」
6. 返回 Running
```

**结算 `settle(device)`**（严格按真机观测顺序，报告 §9.3）：

```
1. 未连接则 connect
2. s = parseStatus(request(0x23,[0]))     // 这次的 randomNumber 用于上传
3. request(0x22,[0])                       // 关阀；设备已自行关阀时也应返回 0x80（待真机验证）
4. xf = request(0x85,[0]).data             // 42 字节
5. res = api.uploadData(s.protocolType, s.randomNumber, xf.toHexLower())
6. request(0x86, ackPayload(xf))           // 失败只记日志，不影响结算结果（服务端已入账）
7. 清 ActiveOrder，加入/更新最近设备，返回 SettleResult(consumeMoney 厘, balanceAfter 厘, orderNo)
```

不变量（单测必须覆盖）：

- 没有成功的 `0x23` 就**绝不**调用 `rateOrder`。
- `rateOrder` 成功后到 `upload/data` 或 `uploadFail` 成功前，`ActiveOrder` 一直存在。
- `upload/data` 失败（含 170 / 226）→ `ActiveOrder` 保留，可重试；重试时**重新走 settle 全流程**（重新 0x23 拿 randomNumber），不要复用旧的 xfData/randomNumber。
- 任何异常路径都 `transport.close()`（`finally`），但 Running 状态下页面在前台时保持连接。

### 5.3 `WaterLocalStore`

- `activeOrder: Flow<ActiveOrder?>`、`setActiveOrder()`、`clearActiveOrder()`。
- `recentDevices`：最多 5 个 `{mac, deviceName, bigTypeId}`，最近使用在前。
- 用 Gson + `TypeToken.getParameterized(List::class.java, RecentDevice::class.java)`。

### 5.4 `WaterOrderRunnerTest`（假 transport + 假 api）

至少覆盖：正常开阀→结算；`state=1` 非本人拒绝；`state=6` 本人引导结算；`0x21` 超时 → 调 `uploadFail` 且清 ActiveOrder；`upload/data` 返回 226 → ActiveOrder 保留；`0x86` 失败不影响结果；结算使用的是结算时那次 `0x23` 的 randomNumber 而非开阀那次。

**验证**：`./gradlew testDebugUnitTest --tests "com.lightxin.feature.water.*"`

---

## 6. 阶段四：权限与清单

`AndroidManifest.xml`：

```xml
<uses-permission android:name="android.permission.BLUETOOTH" android:maxSdkVersion="30" />
<uses-permission android:name="android.permission.BLUETOOTH_ADMIN" android:maxSdkVersion="30" />
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
<uses-feature android:name="android.hardware.bluetooth" android:required="false" />
```

- 只连已知 MAC，不做 discovery，所以**不需要** `BLUETOOTH_SCAN` 和定位权限。
- API 31+ 进入设备页时请求 `BLUETOOTH_CONNECT`；蓝牙关闭时用 `BluetoothAdapter.ACTION_REQUEST_ENABLE` 请求打开。

---

## 7. 阶段五：UI 与导航（**逐条照做，不要自由发挥**）

### 7.0 UI 铁律

**只用 Claude 层组件与 token**（`core/designsystem/component/ClaudeComponents.kt`、`ClaudeSheets.kt`、`theme/ClaudeTokens.kt`），参照页面：`feature/repair/ui/RepairListScreen.kt`（列表 + FAB）、`feature/repair/ui/RepairCreateScreen.kt`（表单 + snackbar）、`feature/credit/ui/CreditScreen.kt`（总览 `LxPanel` + 大数字）。

| 允许 | 禁止 |
|---|---|
| `LxNavBar`、`LxPageHeader`、`LxPanel`、`LxSectionLabel`、`LxStatus`、`LxCallout`、`LxMetaGrid`、`LxPrimaryButton`、`LxGhostButton`、`LxTextAction`、`LxField`、`LxInput`、`LxChoiceGroup`、`LxBottomBar`、`LxFloatingAction`、`LxBottomSheet`、`LxConfirmSheet`、`LxEmptyState`、`LxErrorState`、`LxSkeletonList`、`LxSkeletonBlock`、`Modifier.lxPressable`、`SnackbarHost` | 旧组件 `LxTopBar`、`LxCard`、`LxButton`、`LxTextField`、`LxChoiceChip`、`LxDialog`、`LxMenuRow`（更多功能页内部除外）；任何 Material 的 `TopAppBar`、`Button`、`OutlinedTextField`、`Card`、`AlertDialog`、`Divider`；`Toast` |
| 颜色只用 `LxCanvas / LxSurface / LxTint / LxText / LxTextSecondary / LxTextMuted / LxTextFaint / LxAccent / LxAccentText` 等 token | `MaterialTheme.colorScheme.*`、`Color(0x…)` 硬编码、`Color.White/Black` |
| 动画只用 `LxMotion` 里的时长和曲线 | 自定义 `tween(300)`、`spring()`、无限循环动画（连接中的 `LxProgressIndicator` 除外） |

通用尺寸（与现有页面一致，不要改）：
- 页面水平边距 **24.dp**；`Scaffold(containerColor = LxCanvas)`。
- `LxPageHeader` **放进滚动列表的第一个 item**，`LxNavBar(collapsed = listState.lxHeaderCollapsed(), title = 同一标题)`，滚动后才出现小标题。
- 列表行之间**不画分割线**，行内上下 padding 14.dp。
- 金额、计时数字一律 `style = TextStyle(fontFeatureSettings = "tnum")`。
- 大数字：`fontSize = 34.sp, lineHeight = 40.sp, fontWeight = FontWeight.SemiBold, color = LxText`（照抄 `CreditScreen.kt:247-253`）。
- 文案用全角引号 `“ ”`，**不写副标题式解释文字**，下表之外不要新增文案。
- 每个 Composable 都要能在深色模式下正常显示（token 自动适配，不用写 `isSystemInDarkTheme()`）。

### 7.1 文案表（**只能用这些**）

| key | 文案 |
|---|---|
| 入口 / 首页标题 | 趣智校园 |
| 登录页标题 | 登录趣智校园 |
| 登录方式 | 密码 / 验证码 |
| 字段 | 手机号 / 密码 / 验证码 |
| 获取验证码按钮 | 获取验证码 / `{n}s` |
| 登录按钮 | 登录 / 登录中… |
| 余额标签 | 余额 |
| 最近设备分组 | 最近使用 |
| 首页空态 | 标题“还没有使用过的设备”，正文“扫描设备上的二维码开始” |
| FAB | 扫码 |
| 导航栏动作 | 输入设备号 |
| 退出 | 退出趣智校园（确认弹层：标题“退出趣智校园”，正文“退出后需要重新登录。”，按钮“退出”） |
| 未结算卡片 | 状态“未结算”，动作“去结算” |
| 设备类型 | `bigTypeId=5` → 饮水；`4` → 热水 |
| 设备页主按钮 | 见 7.5 状态表 |
| 设备页信息 | 预扣 / 可用时段 |
| 使用中面板 | 标签“已用时”，状态“使用中” |
| 结算弹层 | 标签“本次消费”，信息“余额”“订单号”，按钮“完成” |
| 离开确认 | 标题“还在使用中”，正文“离开后回到这里结束并结算。”，按钮“离开” |
| 手动输入弹层 | 标题“输入设备号”，占位“12 位设备号”，按钮“确定”，错误“无法识别设备号” |
| 扫码错误 | 无法识别设备码 |
| 不支持 | 标题“暂不支持此设备”，正文为设备全名 `deviceName` |
| 已停用 | 标题“设备已停用”，正文为 `deviceName` |
| 权限 / 蓝牙 | “需要“附近的设备”权限才能连接”（snackbar，带动作“设置”）；“此手机不支持蓝牙” |
| 业务错误 | 一律直接显示 `QzxyRepository` / `WaterOrderRunner` 给出的中文 message（§4.6、§5.2） |

### 7.2 金额与名称格式化（放 `feature/water/domain/WaterFormat.kt`，写单测）

```kotlin
fun formatLi(li: Long): String          // 厘 → "¥0.05"；第三位小数非 0 时保留 3 位："¥0.011"
fun formatYuan(raw: String): String     // "14.110" → "¥14.11"；"14.115" → "¥14.115"；解析失败 → "—"
fun deviceTitle(d: WaterDevice): String // "${类型} · ${roomName}"，例 "热水 · 214"、"饮水 · 开水机1-1"
fun deviceSubtitle(d: WaterDevice): String // listOf(areaName, buildingName, floorName).filter{isNotBlank}.joinToString(" · ")，例 "A36公寓 · 中楼 · 2层"
fun useTimeText(d: WaterDevice): String?   // showDeviceUseTimeContent 非空就用它；否则把 deviceUseTimeList 里 start/end 都非空的拼成 "06:30-08:00，11:30-23:59"；都为空返回 null
```

单测用例：`formatLi(50)="¥0.05"`、`formatLi(11)="¥0.011"`、`formatLi(1000)="¥1.00"`、`formatYuan("14.110")="¥14.11"`、饮水机 `deviceUseTimeList=[{startTime:"",endTime:""}]` → `useTimeText == null`。

### 7.3 路由与入口

```kotlin
// Routes.kt
const val WATER_HOME = "water/home"
const val WATER_LOGIN = "water/login"
const val WATER_SCAN = "water/scan"
const val WATER_DEVICE = "water/device/{mac}"
fun waterDevice(mac: String) = "water/device/${Uri.encode(mac)}"
```

- `MoreFeaturesScreen`：新增参数 `onNavigateWater: () -> Unit`；在第一张卡片“报修”那行后面加 `MoreMenuDivider()` + `MoreMenuRow(icon = Icons.Outlined.WaterDrop, title = "趣智校园", onClick = onNavigateWater)`。**只加这一行，不要改这个页面的其它样式。**
- `NavGraph`：`MORE_FEATURES` 里传 `onNavigateWater = { navController.navigate(Routes.WATER_HOME) { launchSingleTop = true } }`。
- `WATER_HOME` 的 ViewModel 启动时读 `QzxySessionStore`：无会话 → 调 `onRequireLogin()`，NavGraph 中实现为 `navigate(WATER_LOGIN) { popUpTo(WATER_HOME) { inclusive = true } }`。
- 登录成功：`navigate(WATER_HOME) { popUpTo(WATER_LOGIN) { inclusive = true } }`。
- 任意页面收到 `QzxySessionExpired`：同上跳登录。
- 扫码 / 手动输入成功：`navigate(waterDevice(mac))`，扫码页同时 `popUpTo(WATER_SCAN) { inclusive = true }`，返回时回到首页而不是相机。
- 设备页返回首页时首页要刷新余额与“未结算”卡片：用 `savedStateHandle["water_refresh"] = true`，写法照抄 `NavGraph` 里 `leave_refresh` 的用法。
- 页面转场沿用 NavGraph 全局设置，**不要**为这几个路由单独写 `enterTransition`。

### 7.4 登录页 `QzxyLoginScreen`

```
Scaffold(containerColor = LxCanvas,
         topBar = LxNavBar(onBack, title = "登录趣智校园", collapsed = listState.lxHeaderCollapsed()),
         bottomBar = LxBottomBar(Modifier.imePadding()) { LxPrimaryButton("登录" / "登录中…", fillMaxWidth, enabled = canSubmit && !loading) },
         snackbarHost = SnackbarHost(snackbarHostState))
└─ LazyColumn(state = listState, contentPadding = PaddingValues(horizontal = 24.dp))
   ├─ item: LxPageHeader("登录趣智校园")  + Spacer(4.dp)
   ├─ item: LxChoiceGroup(options = listOf("密码" to "password", "验证码" to "sms"), selectedValue, onSelect)  + Spacer(16.dp)
   ├─ item: LxField("手机号", error = phoneError) { LxInput(keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)) }
   └─ item:
        mode == password → LxField("密码") { LxInput(visualTransformation = PasswordVisualTransformation(), keyboardType = Password) }
        mode == sms      → LxField("验证码") {
                              Row(verticalAlignment = CenterVertically) {
                                  LxInput(Modifier.weight(1f), keyboardType = NumberPassword)
                                  Spacer(8.dp)
                                  LxGhostButton(text = if (countdown > 0) "${countdown}s" else "获取验证码",
                                                enabled = phoneValid && countdown == 0 && !sending)
                              }
                           }
```

- 手机号输入只保留数字、最多 11 位；`phoneValid = 11 位且以 1 开头`；失焦且非空且无效时 `phoneError = "手机号格式不对"`（加进 7.1 文案表的唯一例外）。
- 验证码只保留数字、最多 6 位。`canSubmit`：手机号有效 且（密码非空 / 验证码 4–6 位）。
- 倒计时 60 s 放 ViewModel（`viewModelScope` + `delay(1000)`），旋转屏幕不重置。
- 服务端错误（§4.6）用 snackbar 显示，不清空输入。
- 键盘：手机号 `ImeAction.Next`，最后一个字段 `ImeAction.Done` 且 Done 时若 `canSubmit` 直接登录。

### 7.5 首页 `WaterHomeScreen`

```
Scaffold(containerColor = LxCanvas,
         topBar = LxNavBar(onBack, title = "趣智校园", collapsed = ..., actions = { LxTextAction("输入设备号", onClick = 打开手动输入弹层) }),
         floatingActionButton = LxFloatingAction(text = "扫码", icon = Icons.Outlined.QrCodeScanner, onClick = onScan))
└─ PullToRefreshBox（指示器写法照抄 RepairListScreen.kt:151-165，刷新余额 + 读本地数据）
   └─ LazyColumn(contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 112.dp))
      ├─ item "header":  LxPageHeader("趣智校园")
      ├─ item "balance": BalancePanel
      ├─ item "pending": （仅 activeOrder != null）PendingPanel，上方 Spacer(12.dp)
      ├─ item "recentLabel": （仅 recent 非空）LxSectionLabel("最近使用", Modifier.padding(top = 24.dp, bottom = 4.dp))
      ├─ items(recent, key = mac): RecentDeviceRow
      ├─ item "empty": （recent 为空且 activeOrder == null）Box(fillParentMaxHeight(0.45f), Center) { LxEmptyState(...) }
      └─ item "logout": Box(fillMaxWidth, Center, padding(top = 32.dp)) { LxTextAction("退出趣智校园", color = LxTextMuted) } → LxConfirmSheet(destructive = false)
```

`BalancePanel`：`LxPanel(radius = LxRadiusLg, padding = 20.dp)`
- 第一行 `LxSectionLabel("余额")`
- 第二行（`padding(top = 6.dp)`）：
  - 加载中且无旧值：`LxSkeletonBlock(height = 40.dp, widthFraction = 0.4f)`
  - 有值：大数字 `formatYuan(money)`
  - 失败且无旧值：大数字 `"—"`，右侧 `LxTextAction("重试")`
- 有旧值时刷新失败**保留旧值**，只弹 snackbar。

`PendingPanel`：`LxPanel(padding = 16.dp, modifier = Modifier.lxPressable(onClick = 去设备页))`
```
Row(CenterVertically) {
    Column(weight 1f) {
        LxStatus("未结算", LxTone.Warning)
        Text(deviceTitle, 15.sp, Medium, LxText, maxLines = 1, Ellipsis, padding(top = 4.dp))
    }
    LxTextAction("去结算", onClick = 同上)
}
```

`RecentDeviceRow`：照抄 `RepairRow` 的骨架（`RepairListScreen.kt:240` 起），去掉共享元素动画：
```
Row(fillMaxWidth, lxPressable(onClick), padding(vertical = 14.dp), verticalAlignment = CenterVertically) {
    Box(size(44.dp), clip(RoundedCornerShape(12.dp)), background(LxTint), contentAlignment = Center) {
        Icon(if (bigTypeId == 5) Icons.Outlined.WaterDrop else Icons.Outlined.Shower, null, size(22.dp), tint = LxTextSecondary)
    }
    Spacer(14.dp)
    Column(weight 1f) {
        Text(deviceTitle, 15.sp, lineHeight 21.sp, FontWeight.Medium, LxText, maxLines 1, Ellipsis)
        Text(deviceSubtitle, 12.5.sp, lineHeight 18.sp, LxTextMuted, maxLines 1, Ellipsis, padding(top = 2.dp))
    }
}
```
注意：行本身**不要**再加 24.dp 水平 padding（LazyColumn 已经有）；`RepairRow` 自带 padding 是因为它的 LazyColumn 没有。

手动输入弹层：
```
LxBottomSheet(onDismiss) {
    Text("输入设备号", 17.sp, SemiBold, LxText)；Spacer(16.dp)
    LxField(label = "设备号", error = 校验失败时 "无法识别设备号") {
        LxInput(placeholder = "12 位设备号", keyboardOptions = KeyboardOptions(capitalization = Characters, keyboardType = Ascii, imeAction = Done))
    }
    Spacer(20.dp)
    LxPrimaryButton("确定", fillMaxWidth, enabled = 输入非空) { 校验通过 → hide { onOpenDevice(mac) } }
}
```
弹层打开时输入框自动获取焦点（`FocusRequester` + `LaunchedEffect(Unit)`）。

### 7.6 扫码页 `WaterScanScreen` 与组件抽取

1. 先做抽取（**独立提交**）：把 `AiClassScanScreen.kt` 里的 `CameraPreview`、`ScanOverlay`、`processImage`、`applyOfficialZoomSuggestion`、`CameraPermissionRequest` 移到 `core/designsystem/component/QrCodeScanner.kt`，对外只暴露
   ```kotlin
   @Composable fun QrCodeScanner(onDetected: (String) -> Unit, modifier: Modifier = Modifier)
   ```
   内部包含相机权限请求、预览、取景框遮罩。`extractQrPayload`、`AiClassQrPayload`、`ScanState` 等 AI 课堂业务逻辑**留在原文件**。`AiClassScanScreen` 改为调用 `QrCodeScanner`，视觉与行为**完全不变**。抽取后真机回归 AI 课堂扫码签到一次。
2. `WaterScanScreen`：页面外壳（返回按钮、标题位置、背景）**照抄 `AiClassScanScreen` 抽取后的外壳**，标题文字改“扫码”，中间换成 `QrCodeScanner(onDetected = viewModel::onScanned)`。
3. `onScanned`：用 `parseDeviceCode()`（§7.8）解析；成功 → `onOpenDevice(mac)` 并停止继续识别；失败 → snackbar “无法识别设备码” 并 `Log.i("Qzxy", 原文)`，**1.5 s 内同一原文不重复提示**。

### 7.7 设备页 `WaterDeviceScreen`（最容易出错，严格按状态表）

#### UI 状态

```kotlin
data class WaterDeviceUiState(
    val device: WaterDevice? = null,
    val balance: String? = null,                 // wallet.money 原串
    val phase: DevicePhase = DevicePhase.Loading,
    val result: SettleResult? = null,            // 非 null 时显示结算弹层
    val runningSince: Long? = null,              // ActiveOrder.startedAt，用于计时
)

sealed interface DevicePhase {
    data object Loading : DevicePhase
    data class LoadFailed(val message: String) : DevicePhase
    data class Unsupported(val title: String) : DevicePhase      // “暂不支持此设备” / “设备已停用”
    data object Idle : DevicePhase                               // 可开始
    data object Connecting : DevicePhase
    data object Starting : DevicePhase                           // rateOrder + 0x21
    data object Running : DevicePhase
    data object Settling : DevicePhase
    data object NeedSettle : DevicePhase                         // 本地有 ActiveOrder 或设备 state≠0 且是本人
    data object BusyByOther : DevicePhase
}
// 一次性事件（snackbar、跳登录）用 Channel<WaterEvent>，不要塞进 UiState。
```

进入页面：`GET /device/info/mac` → 判断不支持 / 已停用 → 本地 `ActiveOrder.mac == 当前 mac` 则 `NeedSettle`，否则 `Idle`。**进入页面不自动连接蓝牙**，只有点按钮才连接。

#### 状态 → 界面对照表

| phase | 主面板（LxPanel） | 主按钮（LxBottomBar 里的 LxPrimaryButton，fillMaxWidth） | 返回键 |
|---|---|---|---|
| `Loading` | 整页 `LxSkeletonList(count = 4)`，无底栏 | — | 正常返回 |
| `LoadFailed` | 整页居中 `LxErrorState(message, onRetry)` | — | 正常返回 |
| `Unsupported` | 整页居中 `LxEmptyState(title, body = deviceName)` | — | 正常返回 |
| `Idle` | 余额面板（标签“余额” + 大数字） | “开始接水”(5) / “开始洗澡”(4)，enabled | 正常返回 |
| `Connecting` | 同 Idle | 文字“连接中…”，enabled = false | **拦截，不响应** |
| `Starting` | 同 Idle | 文字“开阀中…”，enabled = false | **拦截，不响应** |
| `Running` | 计时面板：第一行 `LxSectionLabel("已用时")` + `Spacer(weight)` + `LxStatus("使用中", LxTone.Accent)`；第二行大数字 `mm:ss`（≥1 小时用 `h:mm:ss`） | “结束”，enabled | 弹 `LxConfirmSheet`（离开确认，`destructive = false`），确认后才返回 |
| `Settling` | 同 Running（计时停止在当前值） | 文字“结算中…”，enabled = false | **拦截，不响应** |
| `NeedSettle` | `LxCallout(tone = LxTone.Warning, title = "上次使用未结算")` | “结算”，enabled | 正常返回 |
| `BusyByOther` | 余额面板 + 面板下方 `LxStatus("设备使用中", LxTone.Warning)` | “设备使用中”，enabled = false | 正常返回 |

页面结构（`Loading/LoadFailed/Unsupported` 以外）：
```
Scaffold(containerColor = LxCanvas,
         topBar = LxNavBar(onBack = 受上表约束的返回, title = deviceTitle, collapsed = ...),
         bottomBar = LxBottomBar { 主按钮 },
         snackbarHost = ...)
└─ LazyColumn(contentPadding = PaddingValues(horizontal = 24.dp))
   ├─ item: LxPageHeader(title = deviceTitle, subtitle = deviceSubtitle)
   ├─ item: Spacer(8.dp) + AnimatedContent(targetState = 面板类型) { 主面板 }
   │        transitionSpec = fadeIn(tween(LxMotion.StateMs, easing = LxMotion.EaseEnter)) togetherWith fadeOut(tween(LxMotion.StateExitMs, easing = LxMotion.EaseExit))
   └─ item: Spacer(24.dp) + LxMetaGrid(items = buildList {
                add("预扣" to formatYuan(withholdMoney.toString()))
                useTimeText(device)?.let { add("可用时段" to it) }
            })
```

细节：
- 返回拦截：用 `BackHandler(enabled = phase in {Connecting, Starting, Settling}) {}`，`LxNavBar.onBack` 走同一判断；`Running` 用另一个 `BackHandler` 打开离开确认。
- 计时：`runningSince` 非空时 `LaunchedEffect(runningSince) { while (true) { now = System.currentTimeMillis(); delay(1000) } }`，不要在 ViewModel 里每秒发状态。
- `Running` 期间屏幕常亮：`val view = LocalView.current; DisposableEffect(phase is Running) { view.keepScreenOn = phase is Running; onDispose { view.keepScreenOn = false } }`。
- 点击主按钮前的蓝牙前置检查（在 Screen 里做，ViewModel 只管业务）：
  1. API ≥ 31 且没有 `BLUETOOTH_CONNECT` → `rememberLauncherForActivityResult(RequestPermission())` 请求；拒绝 → snackbar “需要“附近的设备”权限才能连接”，动作“设置”打开 `Settings.ACTION_APPLICATION_DETAILS_SETTINGS`。
  2. `BluetoothManager.adapter == null` → snackbar “此手机不支持蓝牙”。
  3. 未开启 → `StartActivityForResult()` 启动 `BluetoothAdapter.ACTION_REQUEST_ENABLE`，`RESULT_OK` 后继续，否则什么也不做。
  4. 全部通过 → `viewModel.start()` 或 `viewModel.settle()`。
- 业务失败（任何 `Result.failure`）：snackbar 显示 message，phase 回到动作前的稳定态（`Idle` / `Running` / `NeedSettle`）；开阀失败但 ActiveOrder 仍在时回到 `NeedSettle`。
- 离开页面（ViewModel `onCleared`）调用 `transport.close()`；`ActiveOrder` 不动。

结算弹层（`result != null` 时显示）：
```
LxBottomSheet(onDismiss = viewModel::dismissResult) {
    LxSectionLabel("本次消费")
    Text(formatLi(result.consumeMoneyLi), 大数字样式, padding(top = 6.dp))
    Spacer(20.dp)
    LxMetaGrid(listOf("余额" to (balance?.let(::formatYuan) ?: "—"), "订单号" to result.orderNo))
    Spacer(8.dp)
    LxPrimaryButton("完成", fillMaxWidth) { hide { viewModel.dismissResult() } }
}
```
- 结算成功后 ViewModel 立即重新请求 `/account/wallet` 更新 `balance`，弹层里的余额随之变化。
- 关闭弹层后 phase = `Idle`。

### 7.8 设备码解析 `parseDeviceCode(raw): String?`（domain，写单测）

- 二维码形如 `KLCXKJ-Water,M,AABBCCDDEEFF` → 取最后一个逗号后的内容；
- 手动输入允许 `AABBCCDDEEFF`、`aabbccddeeff`、`AA:BB:CC:DD:EE:FF`；
- 去空白和冒号后必须是 12 位 hex，统一返回 `AA:BB:CC:DD:EE:FF`（大写带冒号），否则 `null`。
- **本校贴纸二维码的真实内容未抓到，属待真机验证项**；如果真机扫到的是 URL，把样例补进单测再扩展解析。

设备页加载判定：`bigTypeId !in {4, 5}` 或 `communicationTypeId != 0` → `Unsupported("暂不支持此设备")`；`isUse != 1` → `Unsupported("设备已停用")`。

### 7.9 UI 验收（截图必须交）

在 1:1 真机上（MEIZU 20 / 或 Pixel 级 393dp 宽），**浅色、深色各一套**，每张图同时看“顶部”和“滚动后”两态（若页面可滚动）：

1. 登录页：密码模式、验证码模式（倒计时中）、键盘弹出时登录按钮在键盘上方。
2. 首页：有余额 + 最近设备；有“未结算”卡片；空态；手动输入弹层（含错误态）。
3. 设备页：`Idle`、`Connecting`、`Running`（计时在走）、`NeedSettle`、结算弹层、不支持设备。
4. AI 课堂扫码页（抽取后回归，视觉应与改动前一致）。

自查清单（逐条打勾写进 checklist evidence）：
- [ ] 全文件搜索 `MaterialTheme.colorScheme`、`Color(0x`、`LxTopBar`、`LxCard(`、`AlertDialog`、`Toast` 在 `feature/water/` 下为 0 处。
- [ ] 没有出现 7.1 文案表以外的中文文案（`grep -rn '"[^"]*[一-龥]' feature/water/ui` 人工核对）。
- [ ] 所有金额走 `formatLi / formatYuan`，没有手写 `"%.2f"`。
- [ ] 按钮禁用态下文字已变成“…中”，没有转圈叠在按钮上。
- [ ] 深色模式下没有白底块、没有看不清的文字。

**验证**：`./gradlew testDebugUnitTest --tests "com.lightxin.feature.water.*" assembleDebug assembleDebugAndroidTest lintDebug` + 上面的截图。

## 8. 阶段六：真机验证与收尾

用自己的账号在饮水机上做（饮水预扣 1 元，比热水表 5 元便宜）。打开开发者选项「蓝牙 HCI 信息收集日志」，测试后 `adb bugreport` 取 btsnoop 与官方 App 对比。

必须逐项确认并写进 checklist `evidence`：

1. 密码登录 / 短信登录成功，`wallet` 余额与官方 App 一致。
2. insecure / secure RFCOMM 哪种能连上，是否弹配对框。
3. 完整一单：开始 → 出水 → 结束 → 扣费与官方 App 账单一致；logcat 帧与报告 §9.4 一致。
4. 用户按设备实体键停水后再点「结束」，`0x22` 是否仍返回 `0x80`，结算是否成功。
5. 出水中离开页面（断开蓝牙），水是否继续出；回来后能否从「未结算」卡片完成结算。
6. 出水中杀进程，重进后能结算。
7. 热水表（`bigTypeId=4`）跑一单，确认 `0x86` 写回同样被接受（报告 §9.10 唯一未验证项）。
8. 官方 App 被顶号后的提示与 LightXin 再次请求时的 `errorCode`（填 §4.6）。
9. release 包（`assembleRelease`）跑一遍登录 + 一单（R8 / Gson 风险）。

收尾：

- 新建 `.agents/notes/2026-11-xx-decision-qzxy-water.md`：独立 OkHttpClient、签名白名单、randomNumber 每次重取、ActiveOrder 不变量、真机验证得到的结论（第 2、4、5、8 项）。
- 把真机得到的结论回填到报告第 9 节。
- 提交：中文主题 + 正文（AGENTS.md「Commit」规范），每个阶段单独提交，正文写跑过的命令与结果。

---

## 9. 待确认 / 风险

| 项 | 风险 | 处理 |
|---|---|---|
| 登录请求 | `/user/login` 请求本身不在现存 HAR 里（字段来自三方实现 + 报告） | 真机第 1 项验证；失败时抓官方 App 登录包对照 |
| `bluetooth/fail` | 未抓包，等级 B | 只在 0x21 失败路径调用；真机制造一次失败（开阀前关掉设备电源）验证 |
| 断开蓝牙是否停水 | 未知 | 真机第 5 项；若断开即停水，`WaterDeviceScreen` 需改为离开页面前先结算 |
| 28 字节状态布局 | 本校未见 | v1 直接报不支持 |
| 二维码内容 | 本校未抓 | 真机确认后更新解析器与单测 |

---

## 10. 附加任务：主题合并 + 死资源清理（与趣智无关，**最先做，单独提交**）

### 10.1 现状（2026-11-02 核对）

- `d0326c3` 已修好深色 splash 空白，但把 API 31 属性写进了 `values-night-v29`，**`lintDebug` 现在报 3 个 `NewApi` error、构建失败**（`values-night-v29/themes.xml:9-11`）。
- `values-v29/themes.xml` 只有 `forceDarkAllowed`：Android 10/11 **浅色**下它整份覆盖 `values/`，透明状态栏/导航栏、`windowBackground`、`windowSplashscreenContent` 全部丢失。
- `values-night/themes.xml`（API 26–28 深色）缺 `windowSplashscreenContent`，没有 logo。
- 注意：`android:windowSplashscreenContent` 是 **API 26 引入、31 废弃**（SDK `api-versions.xml`：`since="26" deprecated="31"`），在 26–30 上**有效**，必须保留。“它只在 31+ 存在、26–30 不生效”的说法是错的。

### 10.2 改法：5 份主题合并为 2 份

删除 `values-v29/`、`values-v31/`、`values-night-v29/` 下的 `themes.xml`（目录空了就删目录）。

`app/src/main/res/values/themes.xml`：

```xml
<resources xmlns:tools="http://schemas.android.com/tools">
    <!-- 主题只有 values/ 与 values-night/ 两份，两份都写全。
         style 的限定符变体之间不继承也不合并：任何变体都会整份覆盖，少写一个 item 就会在对应配置下丢失。
         新 API 属性用 tools:targetApi 标注，低版本系统忽略未知属性，不会崩。 -->
    <style name="Theme.LightXin" parent="android:Theme.Material.Light.NoActionBar">
        <item name="android:statusBarColor">@android:color/transparent</item>
        <item name="android:navigationBarColor">@android:color/transparent</item>
        <!-- 窗口兜底背景与 splash/首页底色一致，避免首帧前后闪白 -->
        <item name="android:windowBackground">@color/splash_background</item>
        <!-- API 26-30 的启动画面（31 起废弃，由下面的 windowSplashScreen* 接管） -->
        <item name="android:windowSplashscreenContent">@drawable/splash_content</item>
        <!-- 禁止 ROM 强制反色，保证暖色 token 不被反色 -->
        <item name="android:forceDarkAllowed" tools:targetApi="29">false</item>
        <item name="android:windowSplashScreenBackground" tools:targetApi="31">@color/splash_background</item>
        <item name="android:windowSplashScreenAnimatedIcon" tools:targetApi="31">@drawable/splash_logo</item>
        <item name="android:windowSplashScreenAnimationDuration" tools:targetApi="31">500</item>
    </style>
</resources>
```

`app/src/main/res/values-night/themes.xml`：内容**完全相同**，只有两处不同：parent 改为 `android:Theme.Material.NoActionBar`；顶部注释补一句“深色用非 Light 父主题，让系统组件默认走深色”。`@color/splash_background` 在 `values-night/colors.xml` 里已是 `#FF262624`，`splash_content.xml` 引用的也是它，无需新增颜色。

### 10.3 死资源（lint `UnusedResources` + 人工核对引用，全部可删）

| 资源 | 为什么是死的 |
|---|---|
| `values/colors.xml` 里的 `splash_background_dark` | 深色值由 `values-night/colors.xml` 的 `splash_background` 提供，全仓库无引用 |
| `res/xml/shortcuts.xml` | 静态 shortcut 已改为 `navigation/ShortcutRegistrar.kt` 动态注册，Manifest 不再引用 |
| `res/drawable/ic_launcher_background.xml` | 自适应图标用的是 `@color/ic_launcher_background` |
| `res/mipmap-*/ic_launcher_foreground.webp`（5 个密度） | 自适应图标用的是 `@drawable/ic_launcher_foreground` |

顺带（lint `ObsoleteSdkInt`，minSdk=26）：`mipmap-anydpi-v26/` 改名为 `mipmap-anydpi/`；`navigation/NavGraph.kt:246` 的 `SDK_INT >= LOLLIPOP` 分支与 `MoreFeaturesScreen.kt` 里 `SDK_INT < M` 两处判断恒为假/真，去掉死分支。**`SDK_INT < S` 的判断不要动**（31 以下确实存在）。

删之前对每个资源名再 `grep -rn <名字> app/src` 确认 0 引用。

### 10.4 验证

- `./gradlew lintDebug`：`NewApi` 与 `UnusedResources` 为 0，构建通过。
- `./gradlew assembleDebug` + `installDebug`。
- 真机（MEIZU 20 / Android 16）冷启动，浅色与深色各录一次（`am force-stop` + `screenrecord` 抽帧），看 logcat `ShellStartingWindow: getWindowAttrs`：浅色 `color: fffaf9f5`、深色 `color: ff262624`，两者都是 `replace icon: true`。**确认 31+ 上没有出现两个 logo**（同时写了废弃的 `windowSplashscreenContent`）；若出现，退回方案：保留 4 份——`values`、`values-night`（不含 31 属性）和 `values-v31`、`values-night-v31`（各自写全）。
- 有 API 29/30 模拟器就跑一次浅色冷启动（这是本次修掉的另一个 bug）；没有就在 checklist 里写明“API 29/30 未验证”。
- 提交主题：`合并主题变体并清理死资源`，正文写命令与结果。
