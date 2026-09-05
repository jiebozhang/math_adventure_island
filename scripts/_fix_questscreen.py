# -*- coding: utf-8 -*-
import io, re

p = 'app/src/main/java/com/example/ui/screens/QuestScreen.kt'
s = io.open(p, encoding='utf-8').read()

# 1. StepGradingState 加 Error 分支（先去 GradingState.kt 里加）
sgs_path = 'app/src/main/java/com/example/ui/viewmodel/GradingState.kt'
sgs = io.open(sgs_path, encoding='utf-8').read()
if 'data class Error' not in sgs:
    sgs = sgs.replace(
        '    data class Failed(val message: String, val attemptCount: Int) : StepGradingState()\n}',
        '    data class Failed(val message: String, val attemptCount: Int) : StepGradingState()\n    data class Error(val message: String) : StepGradingState()\n}',
        1)
    io.open(sgs_path, 'w', encoding='utf-8').write(sgs)
    print('Error added to GradingState')

# 2. ExperimentalFoundationApi import
if 'ExperimentalFoundationApi' not in s:
    s = s.replace('import androidx.compose.runtime.*',
                  'import androidx.compose.runtime.*\nimport androidx.compose.foundation.ExperimentalFoundationApi', 1)

# 3. StepProgress 参数名
s = s.replace('StepProgress(current = stepIndex, total = 7,', 'StepProgress(currentStep = stepIndex, totalSteps = 7,')

# 4. PinyinText 删除 fontWeight/maxLines
s = re.sub(
    r'(PinyinText\(text = [^,]+, fontSize = [^,]+, color = [^)]+?)(?:, fontWeight = [^,]+)?(?:, maxLines = \d+)?(\))',
    r'\1\2', s)
# 兜底处理：单 maxLines 的
s = re.sub(r'(PinyinText\(text = [^,]+, fontSize = [^,]+, color = [^)]+), maxLines = \d+(\))',
           r'\1\2', s)

# 5. BasicTextField + focusRequester import
if 'import androidx.compose.foundation.text.BasicTextField' not in s:
    s = s.replace('import androidx.compose.runtime.*',
                  'import androidx.compose.runtime.*\nimport androidx.compose.foundation.text.BasicTextField\nimport androidx.compose.ui.focus.focusRequester', 1)

# 6. NumberKeyboard 整块替换（onDelete → onBackspace，加 onOperator）
def rewrite_all_kb(text):
    pattern = re.compile(r'NumberKeyboard\([^()]*(?:\([^()]*\)[^()]*)*\)', re.DOTALL)
    new_kb = '''NumberKeyboard(
                                onDigit = { d -> answerText += d; viewModel.setUserAnswer("step_$stepIndex", answerText) },
                                onOperator = { op -> answerText = if (answerText.isBlank()) op else "$answerText $op "; viewModel.setUserAnswer("step_$stepIndex", answerText) },
                                onBackspace = {
                                    val ops = listOf(" + ", " - ", " × ", " ÷ ")
                                    val matched = ops.firstOrNull { answerText.endsWith(it) }
                                    answerText = if (matched != null) answerText.dropLast(matched.length).trimEnd() else answerText.dropLast(1)
                                    viewModel.setUserAnswer("step_$stepIndex", answerText)
                                },
                                modifier = Modifier.fillMaxWidth()
                            )'''
    result = []
    last = 0
    for m in pattern.finditer(text):
        result.append(text[last:m.start()])
        result.append(new_kb)
        last = m.end()
    result.append(text[last:])
    return ''.join(result)

s = rewrite_all_kb(s)

io.open(p, 'w', encoding='utf-8').write(s)
print('QuestScreen fixes done')
