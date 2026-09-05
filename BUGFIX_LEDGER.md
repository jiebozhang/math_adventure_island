# 数学冒险岛 Android · Bug 修复台账（防回归清单）

> **用途**：记录每一次修复的 bug。以后**每次改代码前，必须先读完下面"防回归检查清单"**；
> 每次修完 bug，必须在本文件追加一条记录。目标是杜绝"修了新 bug、放出了旧 bug"。

---

## 一、防回归检查清单（改代码前必读）

每次改动 `QuestScreen / MathViewModel / GradingState / GeminiHelper` 后，逐条核对下面行为是否依然成立：

| # | 必须保持的行为 | 曾经的教训 |
|---|---|---|
| 1 | 题目卡显示 `story + "\n" + text` 两段完整文本 | 只显示 story 把题目描述删了 |
| 2 | 答题区必须有「AI 提示」按钮和「语音输入」按钮（手机版+平板版都要有） | 两次被误删 |
| 3 | 系统键盘弹出不能遮挡输入框（`imePadding` + `BringIntoViewRequester`） | 键盘遮挡 |
| 4 | 语音识别：静音参数 4000/4000/15000ms + `EXTRA_MAX_RESULTS=5` + partial 累积 + onResults 取最长候选 | 提前截断 / 只识别出部分文字 |
| 5 | 语音按钮是**点击切换**（点开始/再点结束），不是长按（detectTapGestures 在部分机型立即释放会报"语音识别出错"） | 长按方案实测失败 |
| 6 | LLM 超时一律走 `settings.llmTimeoutSeconds`（家长-AI智能判题-请求超时时间），**禁止任何 `minOf(x, 8)` 硬编码** | 强制 8s 把配置 70s 截断，误判"AI 不可用" |
| 7 | `connRef` 必须真正持有 conn（三个 callXxxApi 创建连接后立即 `connRef.set(conn)`），否则取消时 disconnect 是空操作 | connRef 从未 set 过，取消机制失效 |
| 8 | socket `readTimeout` 必须比协程 `withTimeout` **大 5 秒**（readTimeout=(t+5)s），保证超时先走协程分支显示"批改超时"而不是被 catch(Exception) 误报"不可用" | 两边同时到期靠竞态决定文案 |
| 9 | `canAdvance`：Grading→false（防重复点击）；Error/Failed/Idle/Passed→true | 按钮永久灰 / 无法重试 |
| 10 | 硬校验步骤 AI 异常/超时**不放行**（进 Error），累计 3 次后才开"跳过"逃避口 | catch(Exception) 直接 passStep 导致随意输入就能过 |
| 11 | `llmJudgeEnabled=false` 时：硬校验步骤进 Error 提示开启 AI，本地判错的直接 Failed，**不直接放行** | 同上 |
| 12 | `BasicTextField` 必须用 `TextFieldValue`（含 selection）；删除/插入按光标位置执行 | 用 String 时删除只作用于尾部 |
| 13 | AI 异常的 Error 文案必须带**真实原因摘要**（shortAiError），不得笼统"暂时不可用" | 异常被吞，无法定位 |
| 14 | **批改链路（callGrading/shortAiError/isRetryableLlmError）禁止使用正则**：JSON 提取用 indexOf，HTTP 状态判断用字符串包含。原因：某些 Android ROM 的正则引擎对 `\{` 转义抛 PatternSyntaxException | 真凶 #15 隐藏了两天 |
| 15 | 列算式步骤本地判对优先（零配额零延迟），本地判错直接 Failed，判不出才调 AI | — |
| 16 | **AI 失败降级哲学**：技术故障永远不能变成孩子过不去的墙。软性步骤（读题/找条件/找问题/选方法）AI 挂了直接放行；列算式本地判不出 + AI 挂了给两条出路（重试 + 先跳过这步） | PC 端哲学：反复重试解决不了技术问题，孩子进度不该被拦住 |
| 17 | `handleMainAction` 在 Failed/Error 状态点击 = 重新触发批改；`solutionRevealed=true` 后主按钮解锁 | 看演示后卡死 |

---

## 二、Bug 台账（时间倒序，最新在最上）

### #16 · 2026-09-05 · AI 失败降级对齐 PC 端哲学
- **现象**：之前 catch(Exception) 进 Error 状态只显示"重试"按钮——网络持续不好时孩子卡死，反复重试毫无意义。
- **根因（设计层面）**：Android 把 AI 技术故障包装成"需要手动介入才能解除的拦路虎"，违反 PC 端的"技术故障不能变成孩子过不去的墙"哲学。
- **修复（对齐《数学冒险岛_电脑端AI失败降级策略_workbuddy执行Prompt.md》）**：
  - **软性步骤**（READ 读题 / FIND_CONDITIONS / FIND_QUESTION / CHOOSE_METHOD）：AI 失败（超时/异常）→ `passStep("网络不太好，这步先过～")`，不弹任何错误。孩子的输入实时存过（setUserAnswer），孩子输入不会丢。
  - **列算式**（FORMULA）：本地判错直接 Failed（不变）；本地判不出 + AI 失败 → Error 状态 + 反馈卡加"重试 + 先跳过这步"两个按钮；新增 `skipStep()` 跳过出口（不记 viewedSolution，只是继续往下走）。
  - `llmJudgeEnabled=false` 分支对齐：列算式本地判不出也给"先跳过这步"出路；其他硬校验软步骤放行（保持原"未开启 AI 批改，本次直接通过"语义）。
  - catch(TimeoutCancellationException) / catch(Exception) 统一走 `degradeOnAiFailure(...)`。
- **文件**：`ui/viewmodel/MathViewModel.kt`（新增 degradeOnAiFailure + skipStep、重写 catch 与 llmJudgeEnabled 分支）、`ui/screens/QuestScreen.kt`（GradingFeedbackCard 加 onSkip 按钮 + 两处调用）
- **验证要点**：完全断网后读题步骤输入文字点下一步 → 安静直接放行；列算式本地判不出时进 Error → "先跳过这步"按钮可点；正常网络下批改通过逻辑不变。

### #15 · 2026-09-05 · "AI 暂时不可用"真凶：正则引擎拒绝 `\{` 转义
- **现象**：#14 修复后再次报"AI 暂时不可用：Syntax error in regexp pattern(第 1 次)，点重试"——错误透出机制立功。
- **根因**：`callGrading` 里的 `Regex("\\{[\\s\\S]*}")` 在某些 Android ROM 的正则引擎（Harmony 派生或 ICU）上对 `\{` 转义抛 PatternSyntaxException。所有源码正则**在 OpenJDK 上全部合法**（用 JDK 21 单文件程序验证过），所以这是设备/ROM 兼容性坑。
- **为什么之前一直没人发现**：①所有异常被吞成"AI 暂时不可用"，②"测试连接"不经过这个正则所以一直成功。
- **修复**：批改链路全部去正则化——
  - `callGrading` JSON 提取：`Regex("\\{[\\s\\S]*}")` → `indexOf('{') + lastIndexOf('}')` 零正则
  - `isRetryableLlmError` 的 `Regex("HTTP 5\\d\\d")` → `(500..504).any { m.contains("HTTP $it") }`
  - `shortAiError` 的 `Regex("HTTP 5\\d\\d")` / `Regex("HTTP (5\\d\\d)")` → 同样改成字符串包含
- **文件**：`data/ai/GeminiHelper.kt`、`ui/viewmodel/MathViewModel.kt`
- **残留风险**：FORMULA 链路还有 `Regex("-?\\d+(?:\\.\\d+)?")` / `Regex("[答案...]")` 等（不含 `\{` 转义，静态合法），如以后列算式再报正则错嫌疑是这些。

### #14 · 2026-09-05 · "AI 暂时不可用"再次出现（异常被吞 + 超时竞态 + connRef 失效）
- **现象**：输入文字点下一步，又出现"AI 暂时不可用，点重试"，但测试连接是成功的，无法定位原因。
- **根因（三个叠加）**：
  1. `catch(Exception)` 把所有异常（429 限流/5xx/网络错误/SocketTimeoutException）统一显示成"AI 暂时不可用"，真实原因被吞；
  2. socket `readTimeout` 与协程 `withTimeout` 都等于 `timeoutSec*1000`，几乎同时到期——socket 先抛就进 catch(Exception)（其实真实原因是**超时**，文案却显示"不可用"）；
  3. `connRef` 从未被 set（三个 API 函数内部创建局部 conn），取消时 `disconnect()` 是空操作，超时取消实际不生效。
- **修复**：
  - `GeminiHelper.callLlmApi`：connRef 传入三个 API 函数并立即 set；`readTimeout=(timeoutSec+5)s`、`connectTimeout=min(timeoutSec,15)s`；HTTP 错误信息截断 150 字；线程异常打日志。
  - `GeminiHelper.callGrading`：429/5xx/连接重置自动重试 1 次（delay 1.5s）；SocketTimeoutException 不重试。
  - `MathViewModel`：新增 `shortAiError(e)`，Error 文案带真实原因（限流429/Key无效/服务端5xx/网络超时/响应慢），超时文案提示"可在家长控制台调大超时"。
- **文件**：`data/ai/GeminiHelper.kt`、`ui/viewmodel/MathViewModel.kt`
- **验证要点**：故意断网批改→显示"网络异常..."；正常批改→通过；慢响应→显示"批改超时"而非"不可用"。

### #13 · 2026-09-05 · AI 误判不可用 + 删除按钮不按光标
- **现象**：①第一步输入文字后报"AI暂时不可用(第1次)"（实际是配置 70s 被截成 8s）；②光标放中间点删除，删的是末尾的字。
- **根因**：①`effectiveTimeoutSec = minOf(settings.llmTimeoutSeconds, 8)` 硬编码截断；②`BasicTextField` 用 String 而非 TextFieldValue，无 selection 信息。
- **修复**：①超时全部改用 `settings.llmTimeoutSeconds`；②改用 `TextFieldValue(selection)`，删除/插入按 selection 执行。
- **文件**：`GeminiHelper.kt`、`MathViewModel.kt`、`QuestScreen.kt`

### #12 · 2026-09-05 · 随意输入文字就能进入第二步
- **现象**：硬校验步骤（读题/找条件等）随便输入什么都放行。
- **根因**：`catch(Exception)` 和 `llmJudgeEnabled=false` 分支直接 `passStep` 放行。
- **修复**：异常→进 Error 不放行（3 次后开跳过）；未开 AI→硬校验步骤进 Error 提示开启。
- **文件**：`MathViewModel.kt`

### #11 · 2026-09-05 · 语音识别只出来部分文字
- **现象**：说一长句只识别出片段。
- **根因**：`onResults` 只取第一个候选；识别器有时只回中间结果。
- **修复**：`EXTRA_MAX_RESULTS=5` 取最长候选 + `partialText` 实时累积，onResults 时 final/partial 取更长者。
- **文件**：`QuestScreen.kt`

### #10 · 2026-09-05 · 下一步按钮始终灰色（Idle 不可点）
- **现象**：输入前后按钮都灰。
- **根因**：`canAdvance` 对硬校验步骤的 Idle 返回 false，首次点击永远无法触发批改。
- **修复**：Idle 也可点（点击即触发 AI 批改）。
- **文件**：`GradingState.kt`

### #9 · 2026-09-05 · 长按语音报"语音识别出错" + 判错后按钮灰色
- **现象**：①长按麦克风提示"语音识别出错"；②AI 判错后按钮灰死。
- **根因**：①`detectTapGestures.onPress` 部分机型立即释放，录音刚启动就停；②`canAdvance` 对 Failed/Error 返回 false。
- **修复**：①改回 clickable 点击切换，续听延迟 80ms→300ms 防 RECOGNIZER_BUSY；②Failed→"再试一次"、Error→"重试"，均可点并重新批改。
- **文件**：`QuestScreen.kt`、`GradingState.kt`

### #8 · 2026-09-05 · 8 秒超时不生效 + 长按说话提前截断
- **现象**：①AI 卡住 8 秒不返回也不报错；②没说完话识别就结束。
- **根因**：①`withContext(IO)` 包同步 HttpURLConnection，取消信号传不进阻塞 I/O；②识别器默认静音判定太短。
- **修复**：①`suspendCancellableCoroutine` + 后台 Thread + invokeOnCancellation；②静音参数 4000/4000/15000ms + 按住期间自动续听拼接（当时还加了 minOf(...,8) 截断，后被 #13 证明是过度设计并移除）。
- **文件**：`GeminiHelper.kt`、`QuestScreen.kt`

### #7 · 2026-09-05 · 提示按钮被删 + 语音按钮被删 + 键盘遮挡输入框
- **现象**：AI 提示按钮消失、语音输入按钮消失、系统键盘弹出后输入框被挡住。
- **根因**：重构闯关页时误删组件；未处理 imePadding。
- **修复**：恢复 AI 提示按钮 + 恢复语音按钮（手机+平板都加）+ `imePadding` + `BringIntoViewRequester`。
- **文件**：`QuestScreen.kt`

### #6 · 2026-09-05 · 题目完整描述被删
- **现象**：题目卡只显示 story，完整题干没了。
- **根因**：重构时题目文本只取了 story 字段。
- **修复**：`story + "\n" + text` 同时显示（手机版+平板版）。
- **文件**：`QuestScreen.kt`

### #5 · 2026-09-05 · 七步中 5 步被当"软反馈"随意放行
- **现象**：读题/找条件/找问题/列算式/总结随便填就能过。
- **根因**：初版只把"列算式"当硬校验。
- **修复**：按《七步AI校验完整方案》Step1-5 全部 needsHardGrading=true，5 套 Prompt 模板 + JSON 契约。
- **文件**：`GradingState.kt`、`MathViewModel.kt`、`GeminiHelper.kt`、`QuestScreen.kt`

---

## 三、维护规则（给 AI 的约定）

1. **修 bug 前**：读"防回归检查清单"，确认你的改动不会破坏表中任何一条。
2. **修 bug 后**：在本文件"台账"最上方追加一条（现象/根因/修复/文件/验证要点），如改变了行为约定，同步更新检查清单。
3. **打包命名**：`outputs/app-debug-{主题}-YYYYMMDD.apk`，与台账编号对应。
