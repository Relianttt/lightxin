---
doc_type: feature-design
feature: 2026-05-24-live-notification-consumers
status: approved
summary: AI 课堂上课倒计时接入实况通知
tags: [notification, aiclass, schedule, live-activity]
---

# AI 课堂上课倒计时实况通知

## 1. 决策与约束

**需求摘要**

在 app 前台时，检测到下节课 25 分钟内即将开始，自动发布实况通知。课程开始后显示"已上课"，开始后 5 分钟自动取消。

**成功标准**

- 课前 20 分钟内，实况通知出现，胶囊显示教室地点
- Flyme 展开卡片：星课表风格（课程名大字 + 倒计时/已上课 + 地点大字 + 右侧图标）
- 原生/普通：BigTextStyle 显示课程名、倒计时、地点
- 课程开始后 5 分钟自动取消通知
- 点击跳转 AI 课堂页面

**明确不做**

- 不做后台定时唤醒（仅 app 前台触发）
- 不做查寝签到通知（本次不接入）
- 不做课程结束提醒

**关键决策**

1. 数据源：复用 `HomeViewModel.todayCourses` + `SectionSchedule` 计算上课时间
2. 触发机制：app 前台时定时检查（每分钟一次），发现下节课 ≤20 分钟则发布通知
3. 胶囊文本：教室地点（如 `A1S204`），≤7 字符
4. Flyme 展开：RemoteViews 星课表风格布局
5. 点击路由：AI 课堂首页 `Routes.AICLASS_HOME`

## 2. 编排

### 2.1 触发流程

```
HomeViewModel 持有 todayCourses
    ↓
CourseNotificationScheduler（新增）
    - 每 60 秒检查一次
    - 找到 "startTime - now ∈ [0, 25min]" 的课程 → 发布/更新通知
    - 找到 "now - startTime > 5min" 的课程 → 取消通知
    - 无匹配课程 → 不操作
    ↓
LiveActivityNotifier.show(request) / cancel(key)
```

### 2.2 通知内容

| 状态 | 胶囊 | Flyme 展开 | 原生 contentTitle | 原生 BigText |
|---|---|---|---|---|
| 即将上课（≤25min） | 地点 | 课程名(大) + "即将上课 \| 还有 xx 分钟" + 地点(大) | 课程名 | 即将上课 \| 还有 xx 分钟\n地点：xxx |
| 已上课（0-5min） | 地点 | 课程名(大) + "已上课" + 地点(大) | 课程名 | 已上课\n地点：xxx |

### 2.3 Flyme RemoteViews 布局

参考星课表风格：

```
课程名称（20sp bold）                    [app图标]
即将上课 | 还有 15 分钟（橙色高亮分钟数）
地点
A1S204（25sp bold）
```

新建 `res/layout/notification_live_course.xml`。

### 2.4 挂载点

- `feature/home/ui/HomeViewModel.kt`：持有 todayCourses，启动 scheduler
- `core/notification/CourseNotificationScheduler.kt`（新增）：定时检查 + 发布/取消逻辑
- `res/layout/notification_live_course.xml`（新增）：Flyme 展开布局
- `LiveActivityNotifier`：已有，直接调用

### 2.5 生命周期

- `HomeViewModel` init 时启动 scheduler 协程
- scheduler 在 viewModelScope 中每 60 秒检查一次
- ViewModel onCleared 时协程自动取消，通知也取消

## 3. 验收契约

| # | 触发 | 期望结果 |
|---|---|---|
| 1 | 当前时间距下节课 ≤25 分钟 | 实况通知出现，胶囊显示地点 |
| 2 | 倒计时每分钟更新 | 通知内容从"还有 25 分钟"递减到"还有 1 分钟" |
| 3 | 课程开始 | 通知内容变为"已上课" |
| 4 | 课程开始后 5 分钟 | 通知自动消失 |
| 5 | 点击通知 | 跳转 AI 课堂首页 |
| 6 | 今日无课 | 不发通知 |
| 7 | app 退到后台 | scheduler 停止（viewModelScope 取消），已发通知保留直到超时 |
