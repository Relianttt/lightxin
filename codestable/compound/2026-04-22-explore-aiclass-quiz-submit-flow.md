---
doc_type: explore
type: question
date: 2026-04-22
slug: aiclass-quiz-submit-flow
topic: AI课堂测验从未提交到交卷再到结果查看的真实接口链路是什么
scope: HAR/ai课堂测验全流程.har、HAR/ai课堂提交测验后重新打开测验.har 及对应操作记录
keywords: [aiclass, fif, paper, quiz, submit, temp-storage]
status: active
confidence: high
---

## 问题与范围

目标问题：`AI课堂` 测验是否已经抓到完整的“未提交 -> 作答中 -> 交卷 -> 重新打开查看结果”接口链。

本次只分析以下新样本：
- `HAR/ai课堂测验全流程.txt`
- `HAR/ai课堂测验全流程.har`
- `HAR/ai课堂完成测验重新打开测验.txt`
- `HAR/ai课堂提交测验后重新打开测验.har`

不包含：
- 多题型测验
- 作答中途退出后重新进入的恢复链路
- 教师端发卷/改卷逻辑

## 速答

已经补齐。

FIF 的测验提交不是“一次把整张卷子的答案随交卷接口一起发出去”，而是分成两段：

1. 进入未提交测验后，用 `question/getQuestionListOfPaper` 拉题面
2. 作答过程中，对每一题调用一次 `paper/tempStorageStudentPaperAnswer` 暂存
3. 最后用 `paper/storageStudentPaperAnswer` 只带 `paperId + studentId` 完成交卷
4. 交卷后再次请求 `paper/getPublishPaperListOfStudent`，同一份试卷的 `iscommited` 从 `false` 变成 `true`
5. 重新打开测验时，用 `question/getStudentQuestionDetailListOfPaper` 查看标准答案、学生答案和正误

这说明当前证据已经足够支撑：
- 未提交测验列表态
- 测验答题页
- 逐题自动/手动暂存
- 最终交卷
- 已提交测验详情/结果查看

同时也修正了旧认知：`status` 不能单独当作“是否已提交”的判断依据；在 2026-04-22 的新样本里，交卷前后同一份试卷的 `status` 都是 `1`，真正发生变化的是 `iscommited`。

## 关键证据

1. 操作记录明确说明第一份样本覆盖“进入测验、完成 20 题并提交”，第二份样本覆盖“提交后重新打开查看答题情况”。
   证据：`HAR/ai课堂测验全流程.txt:1`，`HAR/ai课堂完成测验重新打开测验.txt:1`

2. 在交卷前，`getPublishPaperListOfStudent` 返回同一份试卷 `id=132418`，`iscommited=false`。
   证据：`HAR/ai课堂测验全流程.har @ 2026-04-22T11:34:21Z`

3. 进入测验时，`getQuestionListOfPaper` 返回 20 道题，字段只有题面、选项、`studentAnswer` 等，没有标准答案字段；首题 `studentAnswer` 为 `{"index":"","isCorrect":false}`，符合“未作答/未提交态”。
   证据：`HAR/ai课堂测验全流程.har @ 2026-04-22T11:34:25Z`

4. 作答过程中连续发出 20 次 `tempStorageStudentPaperAnswer`，每次请求体只提交单题：`paperId + studentId + questionId + answer=[{"index":"N"}]`。
   证据：`HAR/ai课堂测验全流程.har @ 2026-04-22T11:34:30Z` 至 `2026-04-22T11:34:59Z`

5. 最终交卷接口 `storageStudentPaperAnswer` 的请求体只有 `paperId=132418&studentId=3889526`，没有整卷答案；交卷成功后 1 秒内再次请求列表，同一试卷 `iscommited=true`，而 `status` 仍是 `1`。
   证据：`HAR/ai课堂测验全流程.har @ 2026-04-22T11:35:11Z` 与 `2026-04-22T11:35:12Z`

6. 重新打开已提交测验时，`getStudentQuestionDetailListOfPaper` 返回 20 道题，字段比未提交态多出 `answer`，且 `studentAnswer` 中直接带所选项和 `isCorrect`。
   证据：`HAR/ai课堂提交测验后重新打开测验.har @ 2026-04-22T11:36:28Z`

## 细节展开

### 1. 列表态与提交态的切换

同一个 `paperId=132418` 在交卷前后都出现在 `getPublishPaperListOfStudent` 响应里：

- 交卷前：`iscommited=false`
- 交卷后：`iscommited=true`
- 两次 `status` 都是 `1`

可得结论：
- 列表页判断“是否已提交”时，应优先信任 `iscommited`
- `status` 至少在当前样本里不足以独立表达提交态

### 2. 未提交测验页的数据来源

`getQuestionListOfPaper` 是未提交测验页的真实拉题接口，请求参数为：

- `paperId`
- `studentId`
- `userId`

单题字段已确认包含：
- `id`
- `content`
- `questionTypeName`
- `options`
- `rank`
- `studentAnswer`
- `isCollect`

当前样本 20 题均为单选题，首题 `studentAnswer.index=""`，说明页面初次进入时并未直接带入已作答结果。

### 3. 答题暂存与交卷的职责分离

`tempStorageStudentPaperAnswer` 的职责是“逐题暂存”，不是最终交卷：

- 每题一条 POST
- `answer` 不是纯数字，而是 URL 编码后的 JSON 数组字符串
- 服务端返回统一成功结构：`{"data":{},"location":null,"message":"添加成功","status":"success"}`

`storageStudentPaperAnswer` 的职责是“把已暂存的整卷答案正式提交”：

- 请求体只有 `paperId + studentId`
- 不再重复上传每题答案
- 返回仍然是相同的成功结构

这意味着独立实现时，不能把 `storageStudentPaperAnswer` 误写成“带整卷答案的一次性提交接口”。

### 4. 已提交详情页的数据来源

`getStudentQuestionDetailListOfPaper` 是已提交详情页接口，请求参数为：

- `paperId`
- `studentId`
- `userId`

和未提交态相比，单题字段多出：
- `answer`

同时 `studentAnswer` 中已有：
- `index`
- `isCorrect`

因此它足以直接支撑“答题结果页/解析页”，而不需要前端自行比对答案。

## 未决问题

- 未验证“作答到一半退出，再次进入”时是否仍走 `getQuestionListOfPaper`，以及是否会把此前的 `tempStorage` 结果回显出来
- 当前新增样本只覆盖单选题，尚未覆盖多选、判断、主观题等不同 `answer` 结构
- 尚未确认交卷失败、超时交卷、重复交卷等异常分支

## 后续建议

如果你接下来要继续补 AI课堂测验实现，可以直接基于这份 explore 去更新主接口报告和 `feature/aiclass` 的测验能力设计。

## 相关文档

- `docs/接口分析/AI课堂接口分析报告.md`
- `codestable/architecture/aiclass-overview.md`
