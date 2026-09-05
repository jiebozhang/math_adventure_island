import io

P = r'D:/Math_Adventure_Island_android/app/src/main/java/com/example/data/ai/GeminiHelper.kt'
TPL = r'D:/Math_Adventure_Island_android/scripts/_grading_templates.txt'

src = io.open(P, encoding='utf-8').read()
tpl = io.open(TPL, encoding='utf-8').read()

start_marker = '    // ─────────────────────────────────────────────────────────────\n'
end_marker = '    suspend fun explainSolution('

i = src.find('    // 七步法 AI 批改（模板 A：客观题 / 模板 B：费曼讲解）')
if i == -1:
    # 回退：找注释块起始
    i = src.find('七步法 AI 批改')
    i = src.rfind('\n', 0, i) + 1
j = src.find(end_marker)
assert i != -1 and j != -1 and j > i, "markers not found i=%s j=%s" % (i, j)

new = src[:i] + tpl + '\n' + src[j:]
io.open(P, 'w', encoding='utf-8').write(new)
print('replaced. old len=%d new len=%d' % (len(src), len(new)))
