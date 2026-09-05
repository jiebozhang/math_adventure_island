package com.example.ui.viewmodel

/**
 * 七步法定级与批改状态机
 * 依据：《数学冒险岛_Android七步AI校验完整方案.md》
 *
 * 关键修正：Step1(读题) 也是 needsHardGrading=true —— 之前把它当"费曼软反馈"直接放行，
 * 导致"随便填就能下一步"。
 */
enum class QuestStep(val displayName: String, val needsHardGrading: Boolean) {
    READ("读题", needsHardGrading = true),
    FIND_CONDITIONS("找条件", needsHardGrading = true),
    FIND_QUESTION("找问题", needsHardGrading = true),
    CHOOSE_METHOD("选方法", needsHardGrading = true),
    FORMULA("列算式", needsHardGrading = true),
    CHECK("检查", needsHardGrading = false),   // 本地勾选清单，不调 AI
    SUMMARY("总结", needsHardGrading = false); // 读 Step5 缓存结果，不重新判题

    companion object {
        fun of(index: Int): QuestStep = entries.getOrElse(index) { SUMMARY }
    }
}

/** 需要多轮追问的步骤（Step1 读题 / Step4 选方法） */
val MULTI_TURN_STEPS = setOf(0, 3)

/** 多轮追问的最大轮数 */
const val MAX_TURNS = 3

/** 每步最大尝试次数，达到后开放"看解析"逃避口 */
const val MAX_ATTEMPTS = 3

/** Step6 检查：勾选清单最少项数 */
const val MIN_CHECKLIST = 2

/** Step6 检查清单（4 项，和 PC 版保持一致） */
val CHECKLIST_ITEMS = listOf(
    "数字有没有抄错",
    "单位对不对",
    "有没有漏看条件",
    "算式和答案对得上"
)

/** 每一步的批改状态（7 步共用一套） */
sealed class StepGradingState {
    object Idle : StepGradingState()
    object Grading : StepGradingState()
    data class Passed(val feedback: String) : StepGradingState()
    data class Failed(val message: String, val attemptCount: Int) : StepGradingState()
    data class Error(val message: String) : StepGradingState()
}

/**
 * 「下一步」是否可点。
 * - 批改中：禁用（防重复点击）
 * - 硬校验步骤：必须 Passed
 * - 非硬校验步骤（检查/总结）：由各自的本地条件决定（localOk）
 */
fun canAdvance(stepIndex: Int, state: StepGradingState, localOk: Boolean = true): Boolean = when {
    state is StepGradingState.Grading -> false       // 批改中：禁用防重复点击
    state is StepGradingState.Error -> true          // 超时/异常：允许重试
    state is StepGradingState.Failed -> true        // 答错：允许重试（点击会重新调 AI）
    !QuestStep.of(stepIndex).needsHardGrading -> localOk
    // 硬校验步骤：Idle（首次点击触发批改）和 Passed（已通过）都可点
    else -> state is StepGradingState.Idle || state is StepGradingState.Passed
}
