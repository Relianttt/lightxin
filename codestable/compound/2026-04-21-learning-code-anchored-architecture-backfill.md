---
doc_type: learning
track: knowledge
date: 2026-04-21
updated: 2026-04-21
slug: code-anchored-architecture-backfill
component: codestable/architecture
tags: [architecture, documentation, learning-boundary, workflow, home, running, network]
---

# 1. 背景

这次给一个文档年久失修的仓库补了 `network / running / home` 三份 architecture doc。过程中踩到的最大问题是容易把 learning 写成第二份模块说明书。核心收获可以归纳为三点：

- architecture 与 learning 的职责边界怎么切
- 材料不一致时（代码、旧设计稿、接口分析不同步）该以谁为准、怎么落档
- learning 越写越像架构文档时，有哪些反信号可以提前发现

# 2. 指导原则

## 2.1 `architecture` 与 `learning` 的职责边界

`architecture` 的主语是系统，回答这模块由哪些层组成、入口在哪、状态归谁、链路怎么交互。

`learning` 的主语是这次工作本身，回答这次为什么容易写错、哪种写法以后应该默认采用、出现什么信号说明文档写偏、下次怎样更早纠偏。

正文如果落成了模块结构描述，就进 architecture；正文如果落成了方法、教训、判断标准，就进 learning。两者混写的直接后果是：learning 退化成模块总览，architecture 被工作心得污染，检索价值同时下降。

## 2.2 材料不同步时，记"取证方法"而不是复述"结果"

代码、旧设计稿、接口分析三类材料经常不一致。这次验证下来最稳的顺序是：

1. 先用代码还原当前主链
2. 再拿历史设计稿做对照
3. 最后把"设计意图"与"当前现实"的差异写进 architecture

这条顺序才是 learning 该保留的内容；`home`、`running`、`network` 各自长什么样，留在对应的 architecture doc 即可。

## 2.3 反信号：例子压过了结论

Learning 可以举例，但例子只能支撑结论，不能替代结论。

出现下面任意一条，就说明写偏了：

- 大段篇幅在讲某个模块的结构
- 例子比方法本身更长
- 去掉模块名后，这份 learning 几乎不剩内容
- 读完后学到的是"这个系统怎么实现"，而不是"以后该怎么做"

这次初稿的典型表现就是虽然名义上讲方法，主体仍被 `home / running / network` 的结构说明占住了。

## 2.4 先写"不该怎么写"，再写"应该怎么写"

给老仓库补文档时，learning 优先回答这两个问题效果最直接：

- 最容易滑向什么错误
- 推荐做法是什么

把"反结论"放在前面，能保留这次工作里踩到的认知偏差，比只罗列推荐做法更有复用价值。

# 3. 为什么重要

对这种"旧规划很多、代码演进很快"的仓库，文档职责不清的代价尤其明显：

- `architecture` 会被工作心得污染，不再能单独当作系统地图来读
- `learning` 会退化成另一份模块总览，指导不了下次怎么做
- 后续 agent 检索时，会同时搜到两份看起来不同、实则都在讲系统结构的文档

# 4. 何时适用

优先回想这条 learning 的情境：

- 给老仓库补 architecture doc
- 手头同时有代码、旧设计稿、接口分析，但三者不完全一致
- 写 learning 时越来越像在写"模块说明书"
- 一轮工作里要同时产出 `architecture` 和 `learning`

不适合直接套用的情境：

- 需求还没实现，只是在做未来方案
- 用户本来就要一份模块分析文档，而不是经验沉淀
- 这次工作没有形成新的方法或判断标准，只是单纯补齐事实记录

# 5. 示例

## 5.1 `home`：结构进 architecture，偏差处理进 learning

`HomeBootstrap` 预取什么、`HomeDashboard` 画了哪些卡、`SceneResolver` 的优先级顺序，这些内容只属于 `home-overview.md`。

Learning 该记的是：当设计稿预期"单智慧卡片状态机"，而代码实际仍是固定骨架时，architecture 里要明确写"设计意图"与"当前现实"的偏差；learning 里则记录"遇到三类材料不一致，先按代码写现状，再把设计稿当对照输入"这条处理方法。

参考：

- `docs/项目规划/homepage-design-spec.md`
- `codestable/architecture/home-overview.md`

## 5.2 `running` 和 `network`：先锁主链，再补特例

`running`、`network` 上可复用的写法是：

- 先锁主链，再补协议 / 特例
- 先确认共享模型或共享边界，再写各分支
- 发现协议特例很多时，避免把例外写成默认路径

`RunningTracker` 细节、`RunningRepository` 上传细节、network qualifier 逐项映射，这些内容一旦展开，learning 马上又退化成架构补充说明。

参考：

- `codestable/architecture/running-overview.md`
- `codestable/architecture/network-overview.md`

## 5.3 自检

落盘前过三个问题：

1. 去掉模块名以后，这份文档还剩下可复用的方法吗？
2. 读完之后，别人学到的是"这次应该怎么做"，还是"这个模块怎么实现"？
3. 如果这份文档删掉具体代码锚点，主体是否仍然成立？

前两条回答不了，通常说明这份 learning 其实应该拆回 architecture。

---

2026-04-21：初稿过度展开了 `home / running / network` 的结构示例，已改成以"文档类型边界"和"反信号"为主。
