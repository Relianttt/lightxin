# AI课堂接口分析报告

## 1. 文档范围

本文聚焦安信工 APP 内 `AI课堂` 功能的真实接口链路，目标是为独立新应用开发提供可直接落地的接口级依据。

本次结论综合自以下样本：
- `ai课堂抓包/` 中的既有登录、课程、课程详情抓包
- `ai课堂实际扫码抓包/打开相机扫码界面.har`
- `ai课堂实际扫码抓包/数字码签到成功.har`
- `ai课堂实际扫码抓包/扫码签到成功后返回上一页.har`
- `ai课堂实际扫码抓包/full_log_aiclass_scan_page.txt`
- 主报告中已整理的源码逆向与运行时结论

---

## 2. 总体架构结论

AI课堂不是校内主站自有业务页，而是 `AIIT -> FIF` 的单点接入。

整体链路如下：

1. 校内主站上报服务点击
2. 跳转 `aiitpass.fifedu.com` 完成单点登录
3. 进入 `sttp.fifedu.com/studycenter-nh5/` 的 FIF H5 页面
4. H5 页面继续请求 FIF 业务接口
5. 扫码时由原生扫码页负责“识别二维码”
6. 真正的签到提交与成功页展示仍由 FIF H5 接口完成

结论：
- `AI课堂` 是典型的 `WebView + 原生扫码壳 + H5业务接口` 混合模式
- 若做独立新应用，核心不是复刻原生壳，而是复刻 FIF 登录态与 H5 对应业务接口

---

## 3. 相关域名与职责

| 域名 | 职责 |
|------|------|
| `in.aiit.edu.cn` | 校内主站服务入口、点击上报 |
| `aiitpass.fifedu.com` | AIIT -> FIF 单点登录入口 |
| `sttp.fifedu.com` | FIF 主业务接口与 H5 页面 |
| `www.fifedu.com` | FIF websocket 登录令牌接口 |
| `wcs.fifedu.com` | FIF websocket 实际连接地址 |
| `appstore.xfaike.com` | AI课堂详情页静态图片资源 |

---

## 4. 入口与单点登录

### 4.1 校内主站前置请求

从首页点击 `服务 -> 教务教学 -> AI课堂` 前，主站会先发起：

| URL | 方法 | 说明 |
|-----|------|------|
| `https://in.aiit.edu.cn/zhxy-new-scps/appService/checkServiceVersion.do` | POST | 服务版本校验 |
| `https://in.aiit.edu.cn/zhxy-new-scps/userLogin/click.do` | POST | 服务点击上报 |

已确认参数：
- `checkServiceVersion.do`: `serviceId=20230821`
- `click.do`: `id=20230821`, `type=2`

### 4.2 FIF 单点登录入口

| 属性 | 值 |
|------|-----|
| URL | `https://aiitpass.fifedu.com/iplat-pass-aiit/h5/login` |
| 方法 | GET |

已确认查询参数包括：
- `access_token`
- `_userCode`
- `code`
- `userCode`
- `_userName`
- `_userType`
- `appId`
- `returnFromIscToAppFunc=ReturnDefault`

302 跳转目标：

```text
https://sttp.fifedu.com/studycenter-nh5/?token=...&app=axx&redirect_uri=null
```

结论：
- 校内身份先在 `aiitpass.fifedu.com` 换成 FIF 可识别的登录态
- 进入 FIF 后，后续业务主要依赖 `Cookie + authorization + Visit-Type`

---

## 5. FIF 用户映射与会话

### 5.1 用户映射接口

| URL | 方法 | 说明 |
|-----|------|------|
| `https://sttp.fifedu.com/studycenter/mobile/common/getAiktUserIdByMemberId` | POST | 校内身份映射为 FIF 用户 |
| `https://sttp.fifedu.com/studycenter/mobile/common/termList` | POST | 学期列表 |

已确认映射结果：
- `id=3889526`
- `userType=student`
- `number=3233032235`
- `userName=aiit3233032235`
- `schoolId=2811000226000001678`

### 5.2 会话特征

后续 FIF 请求中稳定出现：

**Cookie**:
- `id=3889526`
- `studentId=3889526`
- `currentUserName=aiit3233032235`
- `SESSION=<动态值>`

**请求头**:
- `authorization: Basic <token>`
- `Visit-Type: mobile`
- `Content-Type: application/x-www-form-urlencoded;charset=UTF-8`

说明：
- 页面导航请求主要依赖 `Cookie`
- XHR / Fetch 业务请求通常同时携带 `Cookie + authorization`

---

## 6. 课程与课堂运行态接口

### 6.1 课程列表

| 属性 | 值 |
|------|-----|
| URL | `https://sttp.fifedu.com/studycenter/mobile/student/myClassroom` |
| 方法 | POST |

请求参数：

```text
termYear=2025
term=2
```

返回要点：
- 课程列表位于 `data.dataList`
- 单门课程至少包含：`classId / courseId / courseRecordId / courseName / teacherName / studentNum`

### 6.2 课程详情主接口

| URL | 方法 | 说明 |
|-----|------|------|
| `https://sttp.fifedu.com/coursecenter-interaction/course/getOneCourse` | GET | 课程详情 |
| `https://sttp.fifedu.com/coursecenter-interaction/userManage/getCountByCourseId` | GET | 课程人数 |
| `https://sttp.fifedu.com/studycenter/mobile/course/liveAndRecord/getWeekList` | GET | 周次列表 |
| `https://sttp.fifedu.com/studycenter/mobile/course/getTeachHourLiveAndRecordList` | POST | 录播/直播列表 |
| `https://sttp.fifedu.com/coursecenter-interaction/sign/getSignInCourseInfo` | POST | 签到信息 |
| `https://sttp.fifedu.com/coursecenter-interaction/homeworkJob/discuss/getDiscussInCourseInfo` | POST | 讨论信息 |
| `https://sttp.fifedu.com/coursecenter-interaction/quickAnswer/getQuickAnswerInCourseInfo` | POST | 快答信息 |
| `https://sttp.fifedu.com/coursecenter-interaction/paper/getPaperInCourseInfo` | GET | 试卷信息 |

### 6.3 正在上课态补充接口

这次新 HAR 又补出了课堂运行中的一组接口：

| URL | 方法 | 说明 |
|-----|------|------|
| `https://sttp.fifedu.com/coursecenter-interaction/courseRecord/getWorkingCourseRecordByStudentId` | POST | 查询当前正在上课记录 |
| `https://sttp.fifedu.com/coursecenter-interaction/course/checkStudentHadInTeachclass` | GET | 判断学生是否在当前教学班 |
| `https://sttp.fifedu.com/coursecenter-interaction/courseRecord/startStudentOnline` | POST | 标记学生进入在线课堂 |
| `https://sttp.fifedu.com/studycenter/mobile/groupTeaching/getGroupInfoByStudentId` | GET | 小组信息 |
| `https://sttp.fifedu.com/coursecenter-interaction/evaluate/getManagerEvaluateActivity` | GET | 课堂评价活动 |
| `https://sttp.fifedu.com/smart-discuss/weike/queryWebsocketConfig` | POST | 获取讨论 websocket 配置 |
| `https://www.fifedu.com/iplatform-websocket/foot/stone/ws/login` | POST | 获取 websocket 登录票据 |

已确认参数与返回要点：

**`getWorkingCourseRecordByStudentId`**
- 请求体：`studentId=3889526`
- 返回中含：
  - `courseRecordId=cc7603debff0465b9492955e789eb687`
  - `courseName`
  - `courseItemName`
  - `cover`
  - `wordTime`

**`checkStudentHadInTeachclass`**
- 查询参数：
  - `studentId=3889526`
  - `teachClassId=3402030000000352381`
- 返回：`{"data":"1","message":"查询成功","status":"success"}`

**`startStudentOnline`**
- 请求体：
  - `username=aiit3233032235`
  - `courseRecordId=cc7603debff0465b9492955e789eb687`
- 返回：`message="保存成功"`

**`getGroupInfoByStudentId`**
- 查询参数：
  - `teachClassId=3402030000000352381`
  - `courseRecordId=cc7603debff0465b9492955e789eb687`
- 当前样本返回：`status="error"`，`message="查询失败"`

**`getManagerEvaluateActivity`**
- 查询参数：
  - `courseRecordId=cc7603debff0465b9492955e789eb687`
  - `termId=5fcc85173d204f368b919480041b3cbd`
  - `teachClassId=3402030000000352381`
- 返回：`code="1000"`，`status="1"`

**`queryWebsocketConfig`**
- 返回：
  - `wsToken="https://www.fifedu.com/iplatform-websocket/foot/stone/ws/login"`
  - `websocketUrl="wss://wcs.fifedu.com/ws/foot/stone"`

**`ws/login`**
- 请求体 JSON：

```json
{
  "userId": "95f777de234fd428b39d84d6651b8397",
  "schoolId": "2811000226000001678",
  "liveId": 0,
  "clientType": "app",
  "bizSystem": "zhwk",
  "bizType": "discuss",
  "isVisitor": 0
}
```

说明：
- 这里的 `userId` 不是 `studentId=3889526`，而是另一个成员维度的标识
- AI课堂内部至少同时存在 `studentId / teachClassId / courseRecordId / memberUserId` 多套标识，开发时不能混用

---

## 7. 签到入口与模式

### 7.1 已确认入口

已确认签到入口不在课程详情页，而在 AI课堂首页/第一页。

当前存在两种签到方式：
- 输入数字码签到
- 扫码签到

此外，首页右上角还有一个通用扫码入口，也可以扫描课程二维码完成签到。

### 7.2 结构判断

结论已经比较明确：
- `数字码签到` 由 FIF H5 直接提交接口
- `扫码签到` 先由原生扫码页识别二维码，再回传给 H5 执行业务跳转
- 原生层主要负责“打开相机和读码”，不是最终签到提交者

---

## 8. 数字码签到成功链

样本文件：
- `ai课堂实际扫码抓包/数字码签到成功.har`

### 8.1 成功链路

1. 查询当前签到信息

```text
POST /coursecenter-interaction/sign/getSignInCourseInfo
```

请求体：

```text
teachClassId=3402030000000352381
courseRecordId=cc7603debff0465b9492955e789eb687
studentId=3889526
```

返回：

```json
{
  "data": {
    "teacherName": "吉涛",
    "signId": "453059"
  },
  "message": "查询成功",
  "status": "success"
}
```

2. 提交数字码签到

```text
POST /coursecenter-interaction/qrcodeV2/checkQrcodeHandler
```

请求体：

```text
signId=453059
userName=aiit3233032235
signCode=626922
```

返回：

```json
{
  "data": {},
  "message": "签到成功",
  "status": "success"
}
```

### 8.2 关键结论

- 数字码签到已经被完整坐实，不再只是“推测存在”
- 该接口不需要先跳 `qrcodeHandler`
- 数字码签到真正提交点就是：

  `POST /coursecenter-interaction/qrcodeV2/checkQrcodeHandler`

- `signId` 不是用户手输的数字码，而是要先从 `getSignInCourseInfo` 获取
- 用户实际输入的是 `signCode`

### 8.3 请求头特征

数字码签到请求稳定携带：
- `authorization: Basic <token>`
- `Visit-Type: mobile`
- `Referer: https://sttp.fifedu.com/studycenter-nh5/mobileinteraview/havingClass.html`
- `Cookie: id / studentId / currentUserName / SESSION`

说明：
- 独立新应用若要直接调用该接口，不能只带 Cookie，最好完整复用 FIF 页面态请求头

---

## 9. 扫码签到成功链

样本文件：
- `ai课堂实际扫码抓包/打开相机扫码界面.har`
- `ai课堂实际扫码抓包/扫码签到成功后返回上一页.har`

### 9.1 打开扫码相机页时的观察

`打开相机扫码界面.har` 仅有 `5` 条 entry，持续约 `5.5` 秒。

其中主要只有：
- `sttp.fifedu.com` 的 websocket 探活
- `content-autofill.googleapis.com` 系统噪音请求
- 个推长连接

未出现：
- `qrcodeHandler`
- `checkQrcodeHandler`
- 其他新的 FIF 签到业务请求

结论：
- 打开扫码相机这一动作本身，没有触发新的 H5 签到接口
- 扫码相机页基本可以判定为原生页面
- 真正的业务请求发生在“扫到有效二维码并回到 WebView”之后

### 9.2 扫码成功后的页面级跳转

成功扫码后，出现如下关键请求：

```text
GET /coursecenter-interaction/qrcodeV2/qrcodeHandler?token=8af3a7f01b37d635d95a026a31eef49ae2463c94&openTheWay=2
```

返回状态：
- `302`

Location：

```text
https://sttp.fifedu.com/studycenter-nh5/mobileinteraview/signSuccess.html?signId=453059&courseId=3402030000000352381&courseCode=3402030000000352381&couseItemId=7f31ab16fff7549333953c7b777b9bbe&courseRecordId=cc7603debff0465b9492955e789eb687
```

说明：
- 这次已经拿到真实 `302 + Location`
- 不再是之前那种只看到半截 processing 状态
- `qrcodeHandler` 更像“校验二维码 token 并导航到成功页”的页面入口

### 9.3 成功页参数

成功页 URL 直接暴露了以下关键业务标识：
- `signId=453059`
- `courseId=3402030000000352381`
- `courseCode=3402030000000352381`
- `couseItemId=7f31ab16fff7549333953c7b777b9bbe`
- `courseRecordId=cc7603debff0465b9492955e789eb687`

注意：
- 参数名是 `couseItemId`，存在拼写问题，但抓包显示后端/前端就是这么传的
- 实现时应按真实字段名兼容，不能擅自改成 `courseItemId`

### 9.4 成功页后的刷新请求

进入 `signSuccess.html` 后，页面继续请求：

| URL | 方法 | 说明 |
|-----|------|------|
| `https://sttp.fifedu.com/coursecenter-interaction/course/getOpeningCourseInfo` | GET | 拉取当前课堂打开态信息 |
| `https://sttp.fifedu.com/smart-living/live/getOpeningLiveInfoById` | POST | 拉取直播信息 |
| `https://sttp.fifedu.com/coursecenter-interaction/sign/getSignInCourseInfo` | POST | 重新查询签到信息 |

其中：

**`getOpeningCourseInfo`**

请求示例：

```text
courseRecordId=cc7603debff0465b9492955e789eb687
studentId=3889526
schoolId=3212
username=aiit3233032235
courseId=3402030000000352381
groupId=
microGroupId=
appKey=aikt
termId=5fcc85173d204f368b919480041b3cbd
```

返回要点：

```json
{
  "data": [
    {
      "data": {
        "signName": "2026.04.15",
        "teacherName": "吉涛",
        "signStatus": "1",
        "signId": "453059",
        "status": "1"
      },
      "dataType": 6
    }
  ],
  "message": "保存成功",
  "status": "success"
}
```

说明：
- `signStatus="1"` 可视为“已签到”证据字段
- 成功扫码后，页面并不是只展示一个纯静态成功页，而是继续用课堂接口刷新状态

**`getOpeningLiveInfoById`**

请求体：

```text
courseRecordId=cc7603debff0465b9492955e789eb687
```

返回要点：

```json
{
  "data": {
    "currentTime": null,
    "resourceId": null,
    "startTime": null,
    "id": null
  },
  "status": 1,
  "message": "查询成功"
}
```

**`getSignInCourseInfo`**

本次返回存在两种形态：

1. `teachClassId + courseRecordId + studentId`
2. `courseId + studentId`

当前样本中这两次返回均为：

```json
{
  "data": {},
  "message": "查询成功",
  "status": "success"
}
```

推断：
- 在签到成功后的某些页面态下，`getOpeningCourseInfo` 才是更稳定的“已签到”展示数据来源
- `getSignInCourseInfo` 更像“当前是否存在待签到任务”的查询接口，成功后可返回空对象

### 9.5 扫码签到请求头特征

`qrcodeHandler` 作为页面导航请求，主要依赖：
- `User-Agent: Android WebView`
- `Cookie: id / studentId / currentUserName / SESSION`

`signSuccess.html` 后续 XHR 则稳定携带：
- `authorization: Basic <token>`
- `Visit-Type: mobile`
- `Referer: https://sttp.fifedu.com/studycenter-nh5/mobileinteraview/havingClass.html`
- `Cookie: id / studentId / currentUserName / SESSION`

结论：
- 扫码签到并不是一个单独的 JSON 提交接口
- 它是“二维码 token 导航入口 + 成功页 + 课堂状态刷新”的组合链路

---

## 10. 原生扫码壳与异常分支

根据源码与 `logcat`，扫码流程中会从 `WebActivity` 跳到原生 `ScanAttendanceForWebViewActivity`。

已确认链路：

1. H5 调用原生扫码入口
2. 原生相机识别二维码
3. 原生通过 `Intent.putExtra("url", 扫码结果)` 回传
4. `WebActivity.onActivityResult()` 再把扫码结果送回 H5

已确认异常：
- 当扫码页返回 `result=0` 且 `data=null` 时
- `WebActivity.onActivityResult()` 没有做空判断
- 会触发 `NullPointerException`
- 这就是“返回一下就回主页”的已确认原因之一

结论：
- 成功链已经基本明确
- 当前 AI课堂剩余主要是“取消扫码 / 无效二维码 / 空结果回传”这类异常分支
- 用户另有运行时反馈：若扫描到“当前账号尚未加入的课程码”，页面会出现“加入课堂”按钮；但当前尚未抓到该分支的真实请求与响应
- 对独立新应用来说，这些是体验优化项，不再是首版阻塞项

---

## 11. 参数与标识字典

### 11.1 已确认的核心标识

| 字段 | 示例 | 含义 |
|------|------|------|
| `studentId` | `3889526` | FIF 学生 ID |
| `currentUserName` | `aiit3233032235` | FIF 用户名 |
| `schoolId` | `2811000226000001678` | FIF 学校 ID |
| `teachClassId` | `3402030000000352381` | 教学班 ID |
| `courseId` | `3402030000000352381` 或其他值 | 课程/课堂页面使用 ID，不能假定与教学班 ID 永远一致 |
| `courseRecordId` | `cc7603debff0465b9492955e789eb687` | 当前上课记录 ID |
| `signId` | `453059` | 当前签到任务 ID |
| `signCode` | `626922` | 用户输入的数字码 |
| `token` | `8af3a7f01b37d635d95a026a31eef49ae2463c94` | 扫码二维码 token |
| `couseItemId` | `7f31ab16fff7549333953c7b777b9bbe` | 成功页中出现的课程项 ID，字段名保持原样 |

### 11.2 重要提醒

- FIF 内部存在多套 ID 体系，不能简单把 `courseId = teachClassId = courseRecordId`
- `signId` 与 `signCode` 完全不是一个东西
- 扫码成功依赖二维码中的 `token`
- 数字码签到依赖先查询到的 `signId`

---

## 12. 开发可行性评估

### 12.1 已经足够开发的部分

当前信息已经足以支持：
- AI课堂入口跳转与 FIF 登录态建立
- 课程列表与课程详情展示
- 当前课堂状态获取
- 数字码签到
- 扫码签到成功链
- 成功页与已签到状态展示

### 12.2 当前剩余缺口

剩余未完全补齐的，主要是边缘场景：
- 取消扫码返回空数据时的异常分支
- 无效二维码、过期二维码的失败返回结构
- 扫描“未加入课程二维码”后出现“加入课堂”按钮的分支接口
- 极少数课堂场景下不同 `courseId / teachClassId` 组合是否有分支差异

这些缺口不影响首版开发。

---

## 13. 最终结论

AI课堂现在已经不是“入口明确但签到链缺失”的状态，而是已经补到：

1. 校内主站入口与服务点击上报
2. AIIT -> FIF 单点登录
3. FIF 用户映射与会话
4. 课程列表与课程详情
5. 正在上课态接口
6. 数字码签到真实提交接口
7. 扫码签到真实导航链与成功页刷新链
8. 原生扫码取消导致回主页的异常原因

如果目标是独立新应用，AI课堂模块已经进入“可实现核心功能”的阶段。

---

**文档版本**: v1.0  
**创建日期**: 2026年4月15日  
**作者**: Codex（AI助手）  
**更新说明**: 基于 `ai课堂实际扫码抓包` 目录中的三份新 HAR，补全 AI课堂数字码签到成功链、扫码成功 `302 -> signSuccess.html -> 课堂状态刷新` 链，并与既有课程列表、课程详情、源码异常链路结论合并成独立文档
