# 数学冒险岛 Android版 —— 七步解题法AI校验完整方案

> 基础参照：PyQt5桌面版 `math_adventure_island_v14.py`。该版本里 Step1(读题)、Step4(选方法)
> 已经用 `_render_conversational_step` 实现了成熟的"AI导师多轮追问"机制；Step5(列算式)
> 在这次改造前只检查"填没填"，AI判题被推迟到Step7才做（只是事后展示结果，没有真正卡关）。
> 本方案把这套已验证有效的设计，完整迁移成Android/Kotlin版本，并且把7步全部按"是否需要
> AI硬校验"重新定级，避免Android这边重蹈"下一步不校验"的覆辙。

> ✅ **实现状态（2026-09-05）**：本方案已落地。补充要点——AI 失败降级策略（软步骤放行 / 硬步骤 FORMULA 本地判不出给"重试 + 先跳过"出路）见《数学冒险岛_电脑端AI失败降级策略_workbuddy执行Prompt.md》与 `PROJECT_CONTEXT.md` 原则 #1/#16；判题 JSON 提取禁用正则（改用 `indexOf`/`lastIndexOf`），见原则 #4。

---

## 一、七步定级：哪些步骤必须AI硬校验，哪些不需要

先说清楚设计判断，避免"为了AI而AI"——不是每一步都应该拿AI卡关：

| 步骤 | 校验方式 | 理由 |
|---|---|---|
| 1. 读题 | **AI语义校验**（多轮，宽松） | 费曼复述，考察"理解"而非"精确"，需要AI判断大意对不对 |
| 2. 找条件 | **AI客观校验**（对照题目reference） | 有标准答案（题目里的已知条件），可以硬校验，之前被漏掉了 |
| 3. 找问题 | **AI客观校验**（对照题目reference） | 同上，"要求什么"是唯一确定的，可以硬校验 |
| 4. 选方法 | **AI说理校验**（多轮，宽松） | 只校验"有没有说出为什么"，不校验方法本身对不对（列算式会验证） |
| 5. 列算式 | **AI/本地规则严格校验** | 这是最终数值结果，必须是唯一真正"一定要判对错"的一步 |
| 6. 检查 | **本地规则（非AI）** | 检查是"自查习惯"，不是知识点，用勾选清单即可，硬上AI反而形式主义 |
| 7. 总结 | **不校验，仅展示+可选AI鼓励语** | 结果已经在Step5确定，这里是复盘，不应该再判一次对错 |

也就是说，Android这边真正需要补的"硬校验"是 **Step2、Step3、Step5** 这三处（Step1、Step4的多轮追问逻辑如果workbuddy已经写了类似机制，直接复用；如果还没写，本方案里也一并给出完整模板）。

---

## 二、统一的JSON返回契约（所有步骤必须遵守）

为了让Android端用**同一套解析逻辑和UI状态机**处理7步（而不是每步写一套解析代码），所有Prompt都必须约束AI只返回下面这个统一结构，字段含义在每步里略有差异但形状完全一致：

```json
{
  "correct": true,
  "feedback": "简短鼓励语，仅correct=true时有值",
  "hint": "引导性提示，仅correct=false时有值，绝不能直接给答案",
  "suggestion": "具体学习建议，仅correct=false时有值"
}
```

**硬性约束（必须写进每一条system prompt）：**
- 只输出这一个JSON对象，不要有任何前后缀文字、不要用markdown代码块包裹
- `hint`绝不能直接说出正确答案或缺失的具体内容，只能"指方向"
- `feedback`/`hint`/`suggestion` 每项不超过20字（防止在小屏幕上换行太多行）

---

## 三、七步完整Prompt模板

以下模板里的 `{xxx}` 是需要Android端在运行时替换的变量，**不是让AI去填的占位符**——替换后再发给AI。

### Step1：读题（费曼复述，多轮，宽松判定）

```
你是一位温柔耐心的小学数学老师，正在和一个1-3年级的孩子对话。
孩子正在用自己的话复述一道数学题的意思。评判标准要宽松，
只要抓住"已知了什么、要求什么"的大意就算理解，不要吹毛求疵。

【题目】：{question_text}
【图片内容描述】：{image_desc}（如果是"无图片"就忽略这条）
【此前已经问过、孩子没答对的追问记录】：{prior_qa_pairs}
【孩子本轮最新的回答】：{latest_answer}
【这是第{turn_count}轮对话，最多{max_turns}轮】

如果孩子的复述体现了正确的大意（哪怕不完整但没有明显错误）：
correct设为true，feedback给一句简短表扬（不超过15字）。

如果有明显遗漏（比如完全没提到某个已知条件，或说反了要求什么）：
correct设为false，feedback留空，
hint提出一个具体的追问方向，引导孩子注意到遗漏的部分
（例如"你提到了第一排密码，那第二排呢？"），
绝不能直接告诉孩子漏掉的具体数字或条件内容，
suggestion给一句鼓励性的方法建议（比如"可以把题目再读一遍，圈出数字"）。

如果已经是最后一轮（第{max_turns}轮），hint可以稍微给多一点提示方向，
但依然不能直接说出条件或答案。

只输出JSON：{"correct": true或false, "feedback": "...", "hint": "...", "suggestion": "..."}，
不要输出其他文字。
```

**变量来源**：`question_text`/`image_desc` 来自题库；`prior_qa_pairs` 是本轮对话历史（数组，格式见下方Kotlin代码）；`latest_answer` 是孩子刚提交的文本；`turn_count`/`max_turns` 由客户端状态机维护（建议 `max_turns=3`）。

---

### Step2：找条件（对照标准条件，客观校验）—— **这是Android端目前缺失的硬校验**

```
你是一位耐心的小学数学老师，正在批改孩子"找已知条件"这一步。

【题目】：{question_text}
【图片内容描述】：{image_desc}
【标准的已知条件】：{reference_conditions}
【孩子写下的已知条件】：{user_input}

请判断孩子是否找全、找对了题目里的已知条件（允许表达方式不同，
比如"358"和"第一排密码是358"视为等价；顺序不重要）。
不要求逐字一致，但不能漏掉关键数字或条件，也不能凭空编造题目里没有的条件。

如果找全找对了：correct设为true，feedback给一句简短鼓励（不超过15字）。

如果遗漏或找错了：correct设为false，feedback留空，
hint指出"大概漏了哪一类信息"（比如"题目里是不是还提到了另一个数？"），
绝不能直接说出遗漏的具体数字，
suggestion给一句方法建议（比如"把题目里出现的每个数字都圈出来看看"）。

只输出JSON：{"correct": true或false, "feedback": "...", "hint": "...", "suggestion": "..."}，
不要输出其他文字。
```

**变量来源**：`reference_conditions` 就是Python版 `question["reference"]["conditions"]` 字段——Android题库如果还没有这个字段，需要先在题库结构里补上（这是本方案能落地的前提，见第五节"需要题库配合的改动"）。

---

### Step3：找问题（对照标准问题，客观校验）—— **同样是Android端缺失的硬校验**

```
你是一位耐心的小学数学老师，正在批改孩子"找出题目要求什么"这一步。

【题目】：{question_text}
【标准的问题（题目要求求什么）】：{reference_question}
【孩子写下的理解】：{user_input}

请判断孩子是否准确抓住了题目最终要求什么（允许表达方式不同，
比如"一共多少"和"两个加起来是几"视为等价）。

如果抓对了：correct设为true，feedback给一句简短鼓励（不超过15字）。

如果理解错了或说的是别的东西：correct设为false，feedback留空，
hint引导孩子重新关注题目最后一句话（比如"再读一下题目最后一句，问的是谁的数量？"），
绝不能直接说出正确的问题是什么，
suggestion给一句方法建议（比如"通常问句会带'一共''还剩''多少'这些词，找找看"）。

只输出JSON：{"correct": true或false, "feedback": "...", "hint": "...", "suggestion": "..."}，
不要输出其他文字。
```

**变量来源**：`reference_question` 对应 `question["reference"]["question"]`。

---

### Step4：选方法（多轮，只校验"有没有说理由"，不校验方法本身对不对）

```
你是一位温柔耐心的小学数学老师。孩子正在说他打算用什么方法解决这道题
（比如加法、减法、乘法、除法，或者更具体的思路）。

【题目】：{question_text}
【这个知识点的核心概念】：{core_concept}
【此前已经问过、孩子没答对的追问记录】：{prior_qa_pairs}
【孩子本轮最新的回答】：{latest_answer}
【这是第{turn_count}轮对话，最多{max_turns}轮】

你的任务不是判断"方法对不对"（后面列算式那一步会验证结果是否正确），
而是判断孩子有没有说出"为什么"——哪怕只是很朴素的理由，
比如"因为要把两个数合起来"，也算通过。只要不是完全说不出理由，
或者理由和题目明显不相关，就应该判定通过。

如果说出了理由（哪怕朴素）：correct设为true，feedback给一句简短鼓励（不超过15字）。

如果完全说不出理由或者只说了方法名没说为什么：correct设为false，feedback留空，
hint用一个具体的问题引导孩子说出理由（比如"为什么你觉得要用加法呢？"），
suggestion给一句提示（比如"想想这个知识点：{core_concept}"）。

只输出JSON：{"correct": true或false, "feedback": "...", "hint": "...", "suggestion": "..."}，
不要输出其他文字。
```

---

### Step5：列算式（唯一必须"判对错"的一步，本地规则优先，AI兜底+给提示）

这一步分两段：**先本地规则判断，判不出来或判定错误才调用AI**（省调用次数，且判"对"的情况完全不需要AI）。

**本地规则（Kotlin，无需AI，见第四节代码）：**
- 能转成浮点数就转，和标准答案数值比较，`|diff| < 1e-6` 判定为对
- 转不了数字就做字符串精确匹配
- 都不行，返回"无法判定"，交给AI

**AI Prompt（仅在本地规则判不出来，或者已知是错的、需要生成有教育意义的提示时调用）：**

```
你是一位耐心的小学数学老师，正在批改孩子的列式作答。

【题目】：{question_text}
【图片内容描述】：{image_desc}
【标准答案】：{correct_answer}
【孩子的作答】：{user_answer}

允许孩子写过程、单位、不同的表达形式（比如"12"和"十二"视为等价），
但最终数值结果必须与标准答案完全一致，不能因为"孩子写了过程"或"态度认真"
就放宽正确性标准。如果图片内容描述不是"无图片"，说明这是看图题，
请结合它判断。

如果正确：correct设为true，feedback给一句简短鼓励（不超过15字），
hint和suggestion都留空字符串。

如果不正确：correct设为false，feedback留空字符串，
hint是一句引导性提示，帮孩子发现自己哪里可能算错了
（绝不能直接说出正确答案或最终数值），
suggestion是一句具体可操作的学习建议
（比如"试着列竖式算一遍""用画图法数一数""检查一下有没有进位"）。

只输出JSON：{"correct": true或false, "feedback": "...", "hint": "...", "suggestion": "..."}，
不要输出其他文字。
```

---

### Step6：检查（不调用AI，本地勾选清单即可）

不需要prompt。理由见第一节——这一步的教育目的是"养成检查习惯"，不是考知识点，用4个checkbox（数字有没有抄错/单位对不对/有没有漏看条件/算式和答案对得上），勾选≥2项才能提交，跟Python版保持一致的产品设计就够了，硬上AI反而会显得机械。

---

### Step7：总结（不判对错，只展示Step5已确定的结果；可选一句AI复盘鼓励语）

不需要硬校验prompt。**关键原则：Step7不能重新调用AI去判一次对错**——正确性已经在Step5确定了，Step7重复判题只会：(a) 多花一次AI调用的钱和等待时间，(b) 万一两次AI判断不一致，孩子会非常困惑（"明明上一步说我对了，这里又说我错"）。Step7要做的是**读取Step5缓存下来的判题结果**直接展示。

如果想加点锦上添花的东西，可以在孩子答对时，额外调一次很轻量的AI生成"复盘鼓励语"（可选，不影响主流程正确性）：

```
你是一位温柔的小学数学老师。孩子刚刚独立做对了一道题："{question_text}"，
用的方法是"{method}"。请给他一句简短、具体、有针对性的鼓励语（不超过20字），
不要泛泛而谈"你真棒"，尽量提到他这道题具体做得好的地方。
只输出这句话本身，不要输出其他文字，不要输出JSON。
```

---

## 四、Kotlin端校验逻辑（可直接对照实现）

### 4.1 统一的返回数据类

```kotlin
data class StepGradeResult(
    val correct: Boolean,
    val feedback: String = "",
    val hint: String = "",
    val suggestion: String = ""
) {
    val hintAndSuggestion: String
        get() = when {
            hint.isBlank() && suggestion.isBlank() -> "再检查一下吧！"
            suggestion.isBlank() -> hint
            hint.isBlank() -> "小建议：$suggestion"
            else -> "$hint\n小建议：$suggestion"
        }
}

enum class QuestStep(val displayName: String, val needsHardGrading: Boolean) {
    READ("读题", needsHardGrading = true),
    FIND_CONDITIONS("找条件", needsHardGrading = true),
    FIND_QUESTION("找问题", needsHardGrading = true),
    CHOOSE_METHOD("选方法", needsHardGrading = true),
    FORMULA("列算式", needsHardGrading = true),
    CHECK("检查", needsHardGrading = false),   // 本地勾选清单，不调AI
    SUMMARY("总结", needsHardGrading = false); // 展示已有结果，不重新判题
}
```

### 4.2 UI状态机（每一步的输入区共用这一套State）

```kotlin
sealed class StepGradingState {
    object Idle : StepGradingState()
    object Grading : StepGradingState()
    data class Passed(val feedback: String) : StepGradingState()
    data class Failed(
        val message: String,      // hintAndSuggestion，直接展示
        val attemptCount: Int
    ) : StepGradingState()
}
```

### 4.3 本地规则优先判断（对应Step5的"能不调AI就不调"）

```kotlin
object LocalAnswerChecker {
    /** 返回 true=确定对，false=确定错，null=无法判定，需要走AI */
    fun check(userInput: String, correctAnswer: String): Boolean? {
        val userTrim = userInput.trim()
        val correctTrim = correctAnswer.trim()

        val userNum = userTrim.toDoubleOrNull()
        val correctNum = correctTrim.toDoubleOrNull()
        if (userNum != null && correctNum != null) {
            return kotlin.math.abs(userNum - correctNum) < 1e-6
        }
        if (userTrim == correctTrim) return true

        // 允许"答案是12"这种带前缀的写法：抓取字符串里最后一个数字对比
        if (correctNum != null) {
            val numbersInUser = Regex("-?\\d+(\\.\\d+)?")
                .findAll(userTrim)
                .map { it.value.toDouble() }
                .toList()
            val last = numbersInUser.lastOrNull()
            if (last != null && kotlin.math.abs(last - correctNum) < 1e-6) return true
        }
        return null // 判不出来，交给AI
    }
}
```

### 4.4 AI校验调用层（统一入口，7步共用同一个函数签名）

```kotlin
class StepGrader(private val llmClient: LlmClient) {

    /**
     * 统一的AI校验入口。step5(FORMULA)在调用前会先过LocalAnswerChecker，
     * 确定"对"就不会走到这里；其余需要硬校验的步骤(READ/FIND_CONDITIONS/
     * FIND_QUESTION/CHOOSE_METHOD)每次都会调用AI，因为它们本来就没有
     * "本地规则能确定对"的捷径（都是开放式或半开放式表达）。
     */
    suspend fun grade(
        step: QuestStep,
        systemPrompt: String,   // 由调用方按第三节模板拼好、替换完变量
        temperature: Double = 0.0
    ): StepGradeResult {
        val raw = llmClient.chat(
            systemPrompt = systemPrompt,
            temperature = temperature
        )
        return parseGradeJson(raw)
    }

    private fun parseGradeJson(raw: String): StepGradeResult {
        // AI偶尔会在JSON前后多输出几句话或套markdown代码块，
        // 用正则先把最外层的 {...} 提取出来，解析失败就兜底返回"需要重试"
        val jsonText = Regex("\\{[\\s\\S]*}").find(raw)?.value
            ?: return StepGradeResult(correct = false, hint = "AI返回格式异常，请重新提交")
        return try {
            val obj = org.json.JSONObject(jsonText)
            StepGradeResult(
                correct = obj.optBoolean("correct", false),
                feedback = obj.optString("feedback", ""),
                hint = obj.optString("hint", ""),
                suggestion = obj.optString("suggestion", "")
            )
        } catch (e: Exception) {
            StepGradeResult(correct = false, hint = "AI返回格式异常，请重新提交")
        }
    }
}
```

### 4.5 ViewModel层：把"下一步"按钮的可用性和判题结果绑死

以Step5(列算式)为例，其余需要硬校验的步骤（Step1/2/3/4）结构完全一样，只是替换prompt模板和调用时机：

```kotlin
class QuestViewModel(
    private val grader: StepGrader,
    private val question: Question
) : ViewModel() {

    private val _formulaGradingState = MutableStateFlow<StepGradingState>(StepGradingState.Idle)
    val formulaGradingState: StateFlow<StepGradingState> = _formulaGradingState

    private var formulaAttempts = 0
    var formulaJudgeResult: Pair<Boolean, String>? = null   // 供Step7直接复用，不再重复判题
        private set

    val showEscapeHatch: StateFlow<Boolean> =
        MutableStateFlow(false) // 由 formulaAttempts >= 3 时置为true，驱动"看解析"按钮显隐

    fun submitFormulaAnswer(userInput: String) {
        if (userInput.isBlank()) return

        viewModelScope.launch {
            _formulaGradingState.value = StepGradingState.Grading

            // 1) 本地规则优先——省钱省时间，孩子答对是大多数情况
            val local = LocalAnswerChecker.check(userInput, question.answer)
            if (local == true) {
                onFormulaPassed(userInput, "算对啦，真棒！")
                return@launch
            }

            // 2) 本地规则判不出来，或者已知是错的（需要有教育意义的提示）——调AI
            try {
                val prompt = buildStep5Prompt(
                    questionText = question.text,
                    imageDesc = question.imageDesc ?: "无图片",
                    correctAnswer = question.answer,
                    userAnswer = userInput
                )
                val result = grader.grade(QuestStep.FORMULA, prompt)
                if (result.correct) {
                    onFormulaPassed(userInput, result.feedback)
                } else {
                    onFormulaFailed(result.hintAndSuggestion)
                }
            } catch (e: Exception) {
                // AI调用失败：不能让技术故障卡住孩子。
                // 本地规则如果已经确定是错的，仍然按"错"处理（不能因为AI挂了就放行错误答案）；
                // 只有本地规则也判不出来时，才兜底放行，交给下一次机会。
                if (local == false) {
                    onFormulaFailed("AI暂时不可用，再检查一下答案哦")
                } else {
                    onFormulaPassed(userInput, null, judgeNote = "网络异常，暂按通过处理")
                }
            }
        }
    }

    private fun onFormulaPassed(userInput: String, feedback: String?, judgeNote: String? = null) {
        formulaJudgeResult = true to (judgeNote ?: feedback ?: "AI 判题")
        _formulaGradingState.value = StepGradingState.Passed(feedback ?: "算对啦，真棒！")
        // 这里触发进入下一步（比如 200-600ms 延迟后调用 advanceToNextStep()），
        // 具体延迟时长和现有其他步骤的过渡动画保持一致即可
    }

    private fun onFormulaFailed(message: String) {
        formulaAttempts += 1
        formulaJudgeResult = null   // 明确未通过，禁止"下一步"可用
        _formulaGradingState.value = StepGradingState.Failed(message, formulaAttempts)
        if (formulaAttempts >= 3) {
            (showEscapeHatch as MutableStateFlow).value = true
        }
    }

    /** "太难了，看看解析"按钮的回调：诚实标记为"看了解析"，不伪装成判对 */
    fun revealSolutionAndAdvance(userInput: String) {
        formulaJudgeResult = false to "已查看解析，未独立完成"
        // 这里额外设一个 viewedSolution = true 的标记，供Step7/成长档案统计时
        // 区分"独立打退怪兽" vs "看解析过关"，不要计入掌握度
        // advanceToNextStep()
    }
}
```

**Compose侧"下一步"按钮的绑定逻辑（伪代码，说明"为什么现在会卡住"）：**

```kotlin
val gradingState by viewModel.formulaGradingState.collectAsState()

Button(
    onClick = { viewModel.submitFormulaAnswer(currentInput) },
    enabled = gradingState !is StepGradingState.Grading
) {
    Text(if (gradingState is StepGradingState.Passed) "下一步" else "提交检查")
}

when (val s = gradingState) {
    is StepGradingState.Failed -> Text(s.message, color = MaterialTheme.colorScheme.error)
    is StepGradingState.Passed -> Text(s.feedback, color = MaterialTheme.colorScheme.primary)
    else -> {}
}

val showEscape by viewModel.showEscapeHatch.collectAsState()
if (showEscape) {
    TextButton(onClick = { viewModel.revealSolutionAndAdvance(currentInput) }) {
        Text("太难了，看看解析")
    }
}
```

**这里最关键的一处（也是workbuddy生成的代码最可能出问题的地方）**：真正决定能不能进入下一步的，不是"点了提交按钮"，而是 `formulaGradingState is StepGradingState.Passed`。如果现有Android代码里"下一步"按钮的 `onClick` 直接写的是 `currentStep++`，而不是先触发 `submitFormulaAnswer` 并等待其结果，那问题就出在这——按钮点击和状态推进之间必须隔着一次挂起的AI/本地校验，且校验结果要真正控制状态推进，而不是校验函数被调用了、但调用结果没人处理，UI直接自顾自跳到下一步。

### 4.6 Step7读取Step5缓存结果（避免重复判题）

```kotlin
@Composable
fun SummaryStep(viewModel: QuestViewModel) {
    val cached = viewModel.formulaJudgeResult
    if (cached != null) {
        val (isCorrect, reason) = cached
        SummaryResultView(isCorrect = isCorrect, reason = reason)
    } else {
        // 极端兜底：理论上不应该发生（Step5必须产出judgeResult才能进入Step7），
        // 如果真的走到这里，说明状态机哪里被绕过了，需要记日志排查，
        // 不要在这里又发起一次AI判题掩盖问题。
        SummaryResultView(isCorrect = false, reason = "判题状态异常，请返回重做")
    }
}
```

---

## 五、需要题库配合的改动

Step2/Step3要能做"客观校验"，题库数据结构里必须带上标准的"条件"和"问题"字段，参照Python版 `question["reference"]`：

```kotlin
data class QuestionReference(
    val conditions: String,   // 例如："第一排密码358，第二排比第一排多476"
    val question: String      // 例如："两排密码合在一起是多少"
)

data class Question(
    val id: String,
    val text: String,
    val answer: String,
    val imageDesc: String? = null,
    val reference: QuestionReference? = null   // Step2/3硬校验依赖这个字段
)
```

如果现在Android题库里还没有这个字段，Step2/Step3暂时没法做客观校验，退化成"填了就过"（和现在的行为一致）——这是唯一一处需要先补数据、才能补逻辑的地方，建议优先确认这一点，否则代码写完也跑不起来。

---

## 六、给workbuddy的排查建议（如果是"AI没生效"而非"没调用AI"）

如果Android端已经在调用AI，但表现还是"随便填就能过"，大概率是以下几种情况之一，建议按顺序排查：

1. **判题结果没有真正驱动状态**：`grade()`调用了，`result`拿到了，但后面的 `if (result.correct)` 判断被写错、或者不管结果如何都调用了 `advanceToNextStep()`。
2. **异常被吞掉后默认放行**：`try/catch`里`catch`分支写成了直接放行，而不是像4.5节那样区分"本地规则已知是错"和"完全判不出来"两种情况分别处理。
3. **JSON解析失败但没有兜底判定为"错"**：解析失败时如果默认返回 `correct = true`（而不是像4.4节那样默认 `correct = false`），就等于AI一旦返回格式不对，所有答案都会被判对。
4. **温度参数设置过高**：判题类的AI调用一定要用 `temperature = 0`（或接近0），温度高会导致同一个答案多次判题结果不一致。
5. **没有区分"判题的AI调用"和"聊天的AI调用"**：如果两者共用同一个通用chat函数、且system prompt没有严格约束"只返回JSON"，AI很容易夹带闲聊文字导致解析失败，进而触发上面第3点的问题。
