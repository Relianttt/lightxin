---
doc_type: issue-fix
issue: 2026-10-18-repair-building-picker-sheet
status: fixed
severity: minor
summary: 新建报修的楼栋/房间选项弹层改为限高懒加载列表，不再占满全屏，36 项楼栋可滚动到底
tags: [repair, compose, bottom-sheet, design-system, ui]
---

# Fix Note：报修楼栋选择弹层占满全屏、末尾楼栋不可达

## 现象

新建报修 → 点「楼栋」，底部弹层从状态栏下方一直铺到屏幕底部，列表在「A3博学楼」处被截断；
在列表区域上滑没有任何位移（前后两张截图字节完全一致），所以只看得到前 13 项左右，
看起来像"楼栋信息不全"。

## 根因

`core/designsystem/component/ClaudeSheets.kt` 的 `LxOptionSheet` 把选项用普通 `Column` 平铺在
`LxBottomSheet`（Material3 `ModalBottomSheet`）里：

- 没有滚动容器，也没有高度上限，内容有多高弹层就多高 → 36 项直接顶满全屏；
- 超出一屏的选项被 sheet 边界裁掉，且无法滚动，末尾选项永远够不到。

不是数据问题：楼栋确实来自接口，见下文。

## 数据来源核对（非硬编码）

- `RepairRepository.addressNodes()` → `POST /bxjlSjd/getBxddForSjd.do`（无参），
  `RepairJson.parseNodes()` 从响应里取平铺的 `{name,value,parent}` 数组；
- `RepairCascade.childrenOf(nodes, xqdm)` 取 `parent` 等于当前校区 `xqdm` 的节点作为楼栋；
- 抓包与分析文档一致：`docs/接口分析/报修系统接口分析报告.md` §14.2 实测 **99 节点**，
  校区 `02`（新芜校区）下 **36 项**楼栋/场馆/门岗，`A36宿舍楼` 下 4 个房间（南楼/北楼/西楼/中楼）；
- 源码里不存在楼栋清单：`A36` / `新芜` / `宿舍楼` 只出现在输入框占位符和列表地址格式化逻辑中。

## 修复

`app/src/main/java/com/lightxin/core/designsystem/component/ClaudeSheets.kt`：

- `LxOptionSheet` 的选项改为 `LazyColumn`，`heightIn(max = 屏幕高度 * 0.42，夹在 220–360dp)`，
  列表内部滚动，弹层整体约半屏多一点；
- 标题行右侧在选项多于 6 项时显示「共 N 项」，让"一共多少"可见，避免再被当成数据缺失；
- 空列表显示「暂无可选项」。

该组件同时被请假表单的学院选择复用，两处一起生效。

## 验证

- `JAVA_HOME=/opt/android-studio/jbr ./gradlew -Dorg.gradle.java.home=/opt/android-studio/jbr :app:assembleDebug`：BUILD SUCCESSFUL。
- 真机（Pixel 4 / Android 13，1080×2280）安装后人工验证：
  - 楼栋弹层不再全屏，标题右侧「共 36 项」，可连续滚动到末项 `A6博思楼`，选中后回填「A36宿舍楼」且房间联动放开；
  - 房间弹层 4 项（南楼/北楼/西楼/中楼）一屏展示；
  - 请假 →「学院」弹层 7 项一屏展示，不再需要滚动。
- `:app:testDebugUnitTest`：129 个用例，0 失败。
- `:app:lintDebug`：BUILD SUCCESSFUL。

## 备注

真机截图留在 `tmp/shots/`（未纳入版本库）。若日后楼栋数继续增长，可选加搜索框，
但不属于本次 bug 范围。
