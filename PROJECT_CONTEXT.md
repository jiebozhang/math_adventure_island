# PROJECT_CONTEXT.md · 数学冒险岛 Android 端项目记忆

> 写给下一个接手的 AI：token 有限时，先读这份文档，再读代码。只写"看代码猜不出来、但必须知道"的事实与决策。

---

## 一、项目概述

面向小学 1-3 年级孩子的数学学习 App，核心是**"七步解题法 + AI 批改"的游戏化闯关**（读题→找条件→找问题→选方法→列算式→检查→总结）。

当前有**两个并行分支**：① 本工程 = Android 原生版（Kotlin + Jetpack Compose）；② PC 桌面版 = Python/PyQt5（独立工程，路径见下文）。两边功能保持同步，但代码完全独立、互不复用。

> 硬约束：PC 端 v14（Claude 开发）**不可修改**，所有改动只在 v15 或本 Android 工程。

---

## 二、技术栈与关键文件

### Android 端（本工程 `D:\Math_Adventure_Island_android`）
- **语言/框架**：Kotlin 2.2.10 + Jetpack Compose（BOM 2024.09.00 / material3 1.3.0），单 Activity + 状态路由（`when` 分支，**非** Navigation Compose）
- **架构**：Clean Architecture（Presentation=UI+ViewModel / Domain / Data）
- **网络/同步**：OkHttp + Retrofit + Moshi，**直调 Supabase PostgREST**（贴近 PC 端 `sync_manager.py`，**不引入 supabase-kt**）
- **AI 调用**：`GeminiHelper`，纯 `HttpURLConnection`（无 SDK），实际接 **DeepSeek**（走 OpenAI 兼容通道）；另有 Gemini native / Anthropic 分支但默认不走
- **数据**：Room v3（本地缓存）+ Supabase 云端（配置在 `local.properties` 的 `supabase.url/anonKey/email/password` 4 字段）
- **构建**：JDK 21（`/d/AndroidSdk/jdk21clean/jdk-21.0.12+8`），Gradle 9.3.1 / AGP 9.1.1，minSdk 24 / targetSdk 36
- **编译命令**：`export JAVA_HOME="/d/AndroidSdk/jdk21clean/jdk-21.0.12+8" && cd "D:/Math_Adventure_Island_android" && ./gradlew :app:compileDebugKotlin`

### 关键文件索引（改某类功能先看哪）
| 要改什么 | 看哪个文件 |
|---|---|
| AI 批改/判题（网络层、prompt 模板、JSON 解析、本地规则兜底） | `app/src/main/java/com/example/data/ai/GeminiHelper.kt` |
| 七步状态机、批改分流、AI 失败降级、跳过出口 | `ui/viewmodel/GradingState.kt`（QuestStep/StepGradingState/canAdvance）+ `ui/viewmodel/MathViewModel.kt`（validateAndAdvance / degradeOnAiFailure / skipStep / shortAiError） |
| 闯关页 UI（反馈卡、主按钮、语音、输入框光标） | `ui/screens/QuestScreen.kt`（手机版 + 平板版 QuestScreenTablet 同文件末尾） |
| 家长 AI 配置（超时/开关/模型/Key） | `ui/screens/ParentConsoleScreen.kt` + `data/model/Models.kt`（`UserSettings` 默认值，`llmTimeoutSeconds` 默认 **20** 秒） |
| 题库数据 | `data/model/Constants.kt`（引用 V15Data）+ `V15Data.kt`（60 关/113 题/6 思想/4 领域） |
| 关卡选择/地图 | `ui/screens/MapScreen.kt` |
| 尺寸适配（平板/横屏） | `ui/theme/Dimens.kt`（DimensImpl/CompactDimens/TabletDimens/LocalDimens） |

### PC 桌面版（对照参考）
- 路径：`C:\Users\zhang\PycharmProjects\PythonProject\Math_Adventure_Island`
- 三文件：`math_adventure_island_v15.py`(入口) / `v15_data.py`(题库) / `v15_views.py`(视图)；同步 `sync_manager.py`
- Android 抄作业时去读的 PC 端函数见「四、跨分支同步状态」

---

## 三、已经验证有效的设计原则（最重要，每条都有踩坑背景）

1. **AI 失败必须降级，不能让技术故障变成孩子过不去的墙**
   区分两类步骤分别处理：**软性步骤**（读题/找条件/找问题/选方法，考察"有没有理解/有没有说理由"）AI 失败→**直接放行**；**硬性步骤**（列算式，考察最终对错）AI 失败→**先退本地规则**，本地判不出→给"重试 + 先跳过这步"两条出路。
   *为什么*：Android 之前是"AI 暂时不可用，点重试"死胡同，网络不好时点多少次重试都没用，孩子卡死；对照 PC 端源码（`_render_conversational_step.on_err` 注释原话"AI请求失败，不能让技术故障卡住孩子，直接按通过处理"）才确认这条哲学。

2. **判题 AI 必须 `temperature=0`，且 system prompt 严格限定"只输出 JSON、无前后缀、无 markdown 代码块"**
   *为什么*：AI 偶尔夹带闲聊/代码块前缀，解析失败；解析失败默认判"错"（绝不误判为对），宁可让孩子重试也不放水。

3. **超时必须设在网络客户端层，不能只靠上层协程/线程取消**
   `HttpURLConnection` 上直接设 `connectTimeout` / `readTimeout`；且 **socket `readTimeout` 要比协程 `withTimeout` 大 5 秒**，让协程超时先触发，走准确的"批改超时"文案。
   *为什么*：①之前用 `withContext(IO)` 包同步 `HttpURLConnection`，取消信号传不进阻塞 I/O，8 秒超时不生效；②强制 `minOf(x,8)` 把用户配置的 70s 截成 8s，误判"AI 不可用"；③readTimeout 和 withTimeout 同时到期会竞态——socket 先抛 `SocketTimeoutException` 会被 `catch(Exception)` 误报成"不可用"而非"超时"。

4. **批改链路（callGrading / shortAiError / isRetryableLlmError）禁止用正则**
   JSON 提取用 `indexOf('{') + lastIndexOf('}')`，HTTP 状态判断用字符串包含。
   *为什么*：`Regex("\\{[\\s\\S]*}")` 的 `\{` 转义在某些 Android ROM 正则引擎上抛 `PatternSyntaxException("Syntax error in regexp pattern")`（标准 JVM 上合法，纯设备兼容性坑），这个真凶藏了两天——因为异常一直被吞成"AI 暂时不可用"，而"测试连接"不经过这段正则所以一直显示成功。

5. **`BasicTextField` 必须用 `TextFieldValue`（含 selection），不能用 String**
   *为什么*：用 String 时删除按钮只能作用在整体尾部，光标放中间点删除删的是末尾的字。

6. **`canAdvance` 状态机**：`Grading→false`（防重复点击）；`Error/Failed/Idle/Passed→true`。
   *为什么*：漏掉 Failed/Error→true 会导致判错后按钮永久灰；漏掉 Idle→true 会导致首次点击永远无法触发批改。

7. **语音识别用 `SpeechRecognizer` clickable 点击切换（点开始/再点结束），不是 `detectTapGestures` 长按**
   设 `EXTRA_MAX_RESULTS=5` + partial 累积 + onResults 取最长候选。
   *为什么*：长按在部分机型 `onPress` 立即释放→录音刚启动就停→报"语音识别出错"；只取第一个候选会丢文字（识别器有时只回中间结果）。

8. **AI 异常文案必须带真实原因摘要（`shortAiError`），不能笼统说"暂时不可用"**
   *为什么*：之前所有异常被 `catch(Exception)` 吞成同一句话，连续排查几轮都定位不到根因；透出"接口限流(429)/Key无效/服务端5xx/响应慢"后才揪出真凶。

9. **列算式本地判对优先（零配额零延迟），本地判错直接 Failed，判不出才调 AI** —— 省 DeepSeek 配额，且本地结果不受 AI 波动影响。

---

## 四、跨分支同步状态

| 功能/修复 | Android 现状 | PC 端对应 | 要不要同步 |
|---|---|---|---|
| **AI 失败降级策略** | 已实现（degradeOnAiFailure + skipStep，见原则 1） | 参照 PC 源码实现：`LLMClient._send()`（网络层 timeout）、`WorkerThread`（最外层 try/except 兜底）、`_render_conversational_step.on_err`（软步骤放行）、`AnswerJudge.check()`（本地规则优先） | ✅ 两边已对齐，改任一侧前先读另一侧 |
| 题库 | `V15Data.kt`（60 关/113 题） | `v15_data.py` 的 `KNOWLEDGE_MAP`+`QUESTION_BANK` | Android 用 `scripts/gen_v15_data.py` 自动转换，改题库走脚本不要手改 |
| 同步层 | OkHttp+Retrofit 直调 PostgREST | `sync_manager.py` | Android 有意贴近 PC 逻辑、不引 supabase-kt |
| 题库字段 schema | 只信任云端 schema，本地缓存 | `pc_field → cloud_field` 非 1:1 同构 | 见 `UI_DESIGN/PC_TO_ANDROID_MIGRATION_GUIDE.md` §1.5 |

---

## 五、当前已知问题与状态

| 问题 | 状态 | 更新时间 |
|---|---|---|
| AI 失败降级 + 正则真凶修复（app-debug-degrade-20260905.apk） | 已修复，待真机验证 | 2026-09-05 |
| 平板/横屏适配（手机竖/横、7-8寸、10寸+、平板竖 5 类机型） | 已实现，待真机验证 | 2026-09-05 |
| FORMULA 链路仍有正则（`-?\d+(?:\.\d+)?` / 中文数字字符类） | 静态合法、设备未验证，列算式若再报正则错嫌疑在此 | 2026-09-05 |

---

## 六、最近重要变更

- **2026-09-05** AI 失败降级对齐 PC 端哲学（软步骤放行/硬步骤给跳过出路），新增 degradeOnAiFailure + skipStep
- **2026-09-05** 修复"AI 暂时不可用"真凶：callGrading 正则 `\{` 转义在 ROM 引擎抛 PatternSyntaxException，改 indexOf 提取 JSON
- **2026-09-05** 错误透出机制：Error 文案带真实原因（shortAiError）+ 429/5xx 自动重试一次 + 修 connRef 失效
- **2026-09-05** 平板横屏适配（WindowSizeClass + Dimens 双档 + 闯关双栏）
- **2026-09-05** v15 题库接入（60 关/113 题，脚本自动转换）＋ MapScreen 关卡选择重构
- **2026-09-04** 按设计文档从零重建 Android 工程（数据层复用旧代码，UI 层重建，4 Tab 底部导航）

---

*维护约定：每次完成有意义的修复/功能后，增量更新「三、设计原则」和「五、已知问题」；若某节越堆越多就合并旧内容而非无限追加。如需 CLAUDE.md / AGENTS.md，只写一行"项目上下文见 PROJECT_CONTEXT.md"指向本文档。*
