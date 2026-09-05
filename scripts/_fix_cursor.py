# -*- coding: utf-8 -*-
import io, re

p = 'app/src/main/java/com/example/ui/screens/QuestScreen.kt'
s = io.open(p, encoding='utf-8').read()

# 1. 把 answerText 改成 answerField (TextFieldValue)
s = s.replace(
    '    var answerText by remember(stepIndex, question.id) { mutableStateOf("") }',
    '    var answerField by remember(stepIndex, question.id) { mutableStateOf(TextFieldValue("", TextRange(0))) }\n' \
    '    val answerText: String get() = answerField.text', 2)

# 2. 删除运算符条（手机版）—— 用 selection 处理插入
old_op_row = '''                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf("\u00d7", "\u00f7", "\u2212", "+", ".").forEach { key ->
                        Surface(shape = RoundedCornerShape(8.dp), color = Color(0xFFB45309), modifier = Modifier.weight(1f).height(36.dp).clickable {
                            answerText = if (answerText.isBlank()) key else "$answerText $key "
                            viewModel.setUserAnswer("step_$stepIndex", answerText)
                        }) { Box(contentAlignment = Alignment.Center) { Text(key, color = Color.White, fontSize = 18.s, fontWeight = FontWeight.SemiBold) } }
                    }
                    Surface(shape = RoundedCornerShape(8.dp), color = Color(0xFF6B6258), modifier = Modifier.weight(1f).height(36.dp).clickable {
                        answerText = if (answerText.isBlank()) ""
                            else {
                            val ops = listOf(" + ", " - ", " \u00d7 ", " \u00f7 ")
                            val matched = ops.firstOrNull { answerText.endsWith(it) }
                            if (matched != null) answerText.dropLast(matched.length).trimEnd() else answerText.dropLast(1)
                        }
                        viewModel.setUserAnswer("step_$stepIndex", answerText)
                    }) { Box(contentAlignment = Alignment.Center) { Text("\u5220\u9664", color = Color.White, fontSize = 12.s, fontWeight = FontWeight.SemiBold) } }
                }'''

new_op_row = '''                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf("\u00d7", "\u00f7", "\u2212", "+", ".").forEach { key ->
                        Surface(shape = RoundedCornerShape(8.dp), color = Color(0xFFB45309), modifier = Modifier.weight(1f).height(36.dp).clickable {
                            // 在光标位置插入
                            val s = answerField.text
                            val cur = answerField.selection.start
                            val withSpaces = if (s.isEmpty() || cur == 0) key else " $key "
                            val newText = s.substring(0, cur) + withSpaces + s.substring(cur)
                            val newCur = cur + withSpaces.length
                            answerField = TextFieldValue(newText, TextRange(newCur))
                            viewModel.setUserAnswer("step_$stepIndex", newText)
                        }) { Box(contentAlignment = Alignment.Center) { Text(key, color = Color.White, fontSize = 18.s, fontWeight = FontWeight.SemiBold) } }
                    }
                    // 删除按钮：根据光标位置删除（selection 是 collapsed 则删左边一个字符，是范围则删选中内容）
                    Surface(shape = RoundedCornerShape(8.dp), color = Color(0xFF6B6258), modifier = Modifier.weight(1f).height(36.dp).clickable {
                        val sel = answerField.selection
                        val s = answerField.text
                        val (newText, newCur) = if (!sel.collapsed) {
                            s.removeRange(sel.min, sel.max) to sel.min
                        } else if (sel.start > 0) {
                            val idx = sel.start - 1
                            s.removeRange(idx, sel.start) to idx
                        } else {
                            s to 0
                        }
                        answerField = TextFieldValue(newText, TextRange(newCur))
                        viewModel.setUserAnswer("step_$stepIndex", newText)
                    }) { Box(contentAlignment = Alignment.Center) { Text("\u5220\u9664", color = Color.White, fontSize = 12.s, fontWeight = FontWeight.SemiBold) } }
                }'''

if old_op_row in s:
    s = s.replace(old_op_row, new_op_row, 1)
    print('op row replaced')
else:
    print('op row NOT FOUND')

# 3. 平板版运算符条（如果有，搜索包含 "NumberKeyboard(" 块上方）
# 平板版没运算符条，只有 BottomSheet 键盘，不需要改

# 4. 数字键盘回调 - 改成 onValueChange 形式（用 TextFieldValue）
# 先找两个 NumberKeyboard 调用点
def rewrite_kb(m):
    block = m.group(0)
    # 替换 onDigit/onOperator/onBackspace
    new = '''NumberKeyboard(
                                onDigit = { d ->
                                    val cur = answerField.selection.start
                                    val txt = answerField.text
                                    val newText = txt.substring(0, cur) + d + txt.substring(cur)
                                    answerField = TextFieldValue(newText, TextRange(cur + 1))
                                    viewModel.setUserAnswer("step_$stepIndex", newText)
                                },
                                onOperator = { op ->
                                    val cur = answerField.selection.start
                                    val txt = answerField.text
                                    val ins = if (txt.isEmpty() || cur == 0) op else " $op "
                                    val newText = txt.substring(0, cur) + ins + txt.substring(cur)
                                    answerField = TextFieldValue(newText, TextRange(cur + ins.length))
                                    viewModel.setUserAnswer("step_$stepIndex", newText)
                                },
                                onBackspace = {
                                    val sel = answerField.selection
                                    val s = answerField.text
                                    val (newText, newCur) = if (!sel.collapsed) {
                                        s.removeRange(sel.min, sel.max) to sel.min
                                    } else if (sel.start > 0) {
                                        val idx = sel.start - 1
                                        s.removeRange(idx, sel.start) to idx
                                    } else {
                                        s to 0
                                    }
                                    answerField = TextFieldValue(newText, TextRange(newCur))
                                    viewModel.setUserAnswer("step_$stepIndex", newText)
                                },
                                modifier = Modifier.fillMaxWidth()
                            )'''
    return new

# 找两处 NumberKeyboard 调用
s_new = re.sub(r'NumberKeyboard\(\s*\n[^\)]*onDigit[^\)]*\)', rewrite_kb, s, flags=re.DOTALL)
if s_new != s:
    s = s_new
    print('NumberKeyboard rewrites done')
else:
    print('NumberKeyboard not matched')

# 5. BasicTextField 改用 TextFieldValue
old_btf = '''BasicTextField(
                                value = answerText,
                                onValueChange = { v ->
                                    answerText = v
                                    viewModel.setUserAnswer("step_$stepIndex", v)
                                },'''

new_btf = '''BasicTextField(
                                value = answerField,
                                onValueChange = { v ->
                                    answerField = v
                                    viewModel.setUserAnswer("step_$stepIndex", v.text)
                                },'''

if old_btf in s:
    s = s.replace(old_btf, new_btf, 1)
    print('phone BasicTextField updated')
else:
    print('phone BasicTextField NOT FOUND')

# 6. 语音输入结果（追加到末尾）
old_voice = 'onResult = { t -> answerText = if (answerText.isBlank()) t else "$answerText $t"; viewModel.setUserAnswer("step_$stepIndex", answerText) },'
new_voice = '''onResult = { t ->
                                    val s = answerField.text
                                    val newText = if (s.isBlank()) t else "$s $t"
                                    answerField = TextFieldValue(newText, TextRange(newText.length))
                                    viewModel.setUserAnswer("step_$stepIndex", newText)
                                },'''
count = s.count(old_voice)
s = s.replace(old_voice, new_voice)
print(f'voice callbacks: {count}')

# 7. onCleared 调用 - answerText = "" 改成清空 answerField
s = s.replace(
    'handleMainAction(viewModel, stepIndex, isLastStep, questState, answerText, checklistLocalOk) { answerText = "" }',
    'handleMainAction(viewModel, stepIndex, isLastStep, questState, answerField.text, checklistLocalOk) { answerField = TextFieldValue("", TextRange(0)) }'
)

io.open(p, 'w', encoding='utf-8').write(s)
print('done')