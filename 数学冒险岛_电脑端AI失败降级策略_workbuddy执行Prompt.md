# 电脑端"AI失败/超时"是怎么处理的 —— 找到的源码 + 给workbuddy的Prompt

先说结论：**电脑端从设计上根本不会让孩子卡在"AI暂时不可用，点重试"这堵墙前面**。
翻遍 v14/v15 全部代码，没有一处会在AI失败时原地弹出一个只能"重试"的死胡同——
要么直接放行，要么退回本地规则判断，从来不会让孩子"进退不得"。这和Android
现在的表现（弹出"点重试"卡住不动）是两种完全不同的设计哲学，这才是根本问题，
比"withTimeout有没有生效"这个技术细节更值得先对齐。

---

## 一、找到的电脑端源码

### 1. 真正的网络层超时（不是靠外层协程/线程包一层"假装能取消"）

来自 `math_adventure_island_v14.py` 第1377-1389行，`LLMClient._send()`：

```python
def _send(self, req):
    try:
        with urllib.request.urlopen(req, timeout=self.timeout) as resp:
            return json.loads(resp.read().decode("utf-8"))
    except urllib.error.HTTPError as exc:
        detail = ""
        try:
            detail = exc.read().decode("utf-8", "ignore")[:200]
        except Exception:
            pass
        raise RuntimeError("大模型接口返回 HTTP {}：{}".format(exc.code, detail))
    except Exception as exc:
        raise RuntimeError("请求大模型接口失败：{}".format(exc))
```

关键点：`timeout=self.timeout` 是**直接传给底层socket的超时参数**，不是"外层包一个
计时器，时间到了就假装取消，但网络请求本身可能还在傻等"。`self.timeout` 来自
家长控制台可配置的 `llm_timeout_seconds`（默认20秒，第710/1033-1037行），
用户可以自己调5-120秒之间任意值。超时一到，`urlopen` 会在**同一个线程里立刻**
抛出异常，不存在"协程说取消了，但底层请求还在裸奔"这种状态不一致的情况。

### 2. 异步线程模型（AI请求全部丢到后台线程，不卡UI线程）

第1275-1304行，`WorkerThread`：

```python
class WorkerThread(QThread):
    finished_ok = pyqtSignal(object)
    finished_err = pyqtSignal(str)

    def __init__(self, fn, *args, **kwargs):
        super().__init__()
        self.fn = fn
        self.args = args
        self.kwargs = kwargs

    def run(self):
        try:
            result = self.fn(*self.args, **self.kwargs)
        except Exception as exc:
            self.finished_err.emit(str(exc))
            return
        self.finished_ok.emit(result)
```

不管`self.fn`内部抛出什么异常（网络超时、HTTP错误、JSON解析失败……），
统一被这一层`try/except`兜住，转成`finished_err`信号发给UI线程——**调用方
永远不需要担心某种异常没被catch住导致状态卡死**，因为兜底的那一层在最外面，
兜的是`Exception`这个最宽的类型，不存在"catch的类型太具体，超时异常漏网"
这种情况（这正是我们之前排查Android 8秒超时不生效时怀疑的头号原因）。

### 3. 核心设计哲学：AI失败了，就退一步，绝不堵死

**"软性"步骤（读题费曼复述、选方法说理由）—— 直接放行，不问对错：**

第3574-3583行，`_render_conversational_step`里的`on_err`：

```python
@safe_ui_callback
def on_err(_err):
    if _stale(): return
    text_box.setEnabled(True)
    if nb_holder.get("btn"): nb_holder["btn"].setEnabled(True)
    status_lbl.setText("")
    # AI请求失败，不能让技术故障卡住孩子，直接按通过处理
    self.user_answers[answer_field] = "\n".join(
        m["text"] for m in conv.messages if m["role"] == "user")
    self._next_step()
```

代码注释写得很直白：**"AI请求失败，不能让技术故障卡住孩子，直接按通过处理"**。
这两步本来就是在考察"有没有理解/有没有想理由"，不是在判断客观对错，
AI挂了就没法验证，那就默认相信孩子，直接放行——用户完全不会看到任何
"点重试"字样，UI上只是安静地翻到下一步。

**"硬性"步骤（列算式/最终答案）—— 退回本地规则，而不是死等AI：**

第1400-1410行，`AnswerJudge.check()`：

```python
def check(self, question, user_val):
    correct_val = question.get("answer", "")
    local = self._local_check(user_val, correct_val)
    if local is not None:
        return local, "本地规则判题"
    if not self.progress.get_llm_judge_enabled():
        return False, "本地规则判题"
    try:
        return self._llm_check(question, user_val)
    except Exception as exc:
        return False, "AI 判题失败，已按本地规则处理：{}".format(exc)
```

这里的顺序很关键：**先用本地规则（数值比对/字符串匹配）判一次，能判出来
就完全不碰AI**；只有本地规则判不出来才调AI；AI也失败了，就把这次判定
按本地规则的结果处理（本地规则判不出来的情况下，保守地按"未通过"处理，
但依然会往下走一个明确的流程分支，而不是卡在原地弹"点重试"）。

**语音识别失败——只是一条会自动消失的提示，不是拦路的弹窗：**

第3288-3303行：

```python
@safe_ui_callback
def on_err(err):
    restore()
    if err == "已取消":
        return
    tip.setText("没有识别到：{}".format(err))
    tip.setStyleSheet("color: {}; font-weight: bold;".format(COLORS["danger"]))

    def _restore_tip():
        tip.setText(old_tip)
        tip.setStyleSheet("color: {};".format(COLORS["muted"]))

    QTimer.singleShot(4000, safe_ui_callback(_restore_tip))
```

提示文字4秒后自动消失，恢复原状，孩子可以立刻再按一次麦克风重来，
没有任何按钮被锁死。

---

## 二、总结成一句话的设计原则

**技术故障（超时/网络失败/AI返回格式错误）永远不应该表现为一堵孩子过不去的墙。**
能验证对错的地方（列算式），失败了就退回本地规则，规则判不出来才保守处理；
不追求精确判对错、只是考察参与度的地方（读题/选方法），AI一旦失败，
直接相信孩子、放行——因为"卡住不让走"对一个三年级孩子来说，
造成的挫败感远大于"AI偶尔漏判一次"的损失。

**Android现在的"AI暂时不可用，点重试"恰恰是反过来的设计**：把AI的技术故障
包装成了一个需要孩子（或家长）手动介入才能解除的拦路虎。哪怕底层的
超时/取消机制以后修得再完美，只要这个"失败了就是死胡同"的UI设计不改，
类似问题还会以别的形式反复出现（换一种网络异常、换一种设备就又卡住）。

---

## 三、给workbuddy的可执行Prompt

```
请把"AI批改/判题失败时的处理方式"从"卡住不动，只能点重试"改成
"技术故障不能拦住用户，参考桌面版的降级策略"。

现象：用户输入答案点击下一步后，如果AI请求失败或超时，界面会显示
"AI 暂时不可用，点重试"并卡在当前步骤，用户除了重试没有别的路可走，
如果网络持续不好，会一直卡死在这一步无法前进。

请按以下原则重构相关的判题/批改调用逻辑（不管是Step1读题、Step4选方法
这类"考察理解/说理由"的步骤，还是Step5列算式这类"考察最终答案对错"
的步骤，处理原则不同，请分别实现）：

1. 【网络层超时要设置在HTTP客户端本身，不能只依赖上层协程withTimeout】
   如果用OkHttp，给发起AI请求的OkHttpClient单独配置：
       .callTimeout(20, TimeUnit.SECONDS)
       .connectTimeout(10, TimeUnit.SECONDS)
       .readTimeout(20, TimeUnit.SECONDS)
   这样超时是在网络客户端层面强制生效的，不依赖协程取消信号能否正确传递
   到底层请求——即使上层withTimeout因为某种原因没生效，网络客户端自己
   也会在20秒后主动掐断连接并抛出异常，不会无限期挂起。

2. 【"考察理解/说理由"类步骤（读题复述、选方法说理由）——
    AI请求失败(超时/网络异常/返回格式解析失败)时，直接放行进入下一步，
    不要弹出任何"重试"提示】
   理由：这类步骤本来就不是在判断客观对错，AI故障时没法验证，
   与其让孩子卡在原地，不如默认相信孩子已经完成了这一步。
   实现：在这些步骤的判题函数的catch块里，直接调用"进入下一步"的方法，
   同时把用户当前已经输入的内容原样存进答案记录，不要弹错误提示。

3. 【"考察最终答案对错"的步骤（列算式）——
    AI请求失败时，优先退回本地规则判断，而不是直接判"不通过"或者
    弹"点重试"卡住】
   实现顺序：
   a) 先用本地规则判断（数值精确匹配/字符串匹配，参考之前给的
      LocalAnswerChecker实现）；能判出"对"或"错"就不需要调用AI，
      直接按本地结果处理，AI请求失败与否根本不影响这类情况。
   b) 只有本地规则判不出来（比如标准答案不是纯数字，答案表达方式复杂）
      时才需要AI介入。如果这时候AI请求失败：
      - 如果本地规则虽然判不出"对/错"，但存在其他辅助信号（比如题库里
        有可以对比的其他字段），可以按当时能拿到的最保守判断处理；
      - 如果确实完全没法判断，不要卡死界面：可以先保存"待复核"标记，
        让答案暂时算作"未确定通过"，同时依然把控制权交还给用户——
        允许用户选择"再检查一下答案后重新提交"或者"跳过这道题，
        稍后在错题本/复习模式里重新处理"，而不是只留一个"点重试"
        的死胡同（网络不好的时候点多少次重试都没用）。

4. 【任何情况下都不应该出现"按钮永久禁用，且没有任何可操作的出路"
    这种状态】即使暂时判不了对错，也至少要给用户一个"跳过/稍后再说"
    的退出路径，不能让孩子的进度被网络问题彻底拦住。

5. 修复后请测试：完全断网的情况下，分别在读题步骤、列算式步骤输入内容
   并提交，确认读题步骤会在超时后自动放行进入下一步（不弹任何错误），
   列算式步骤会先看本地规则能不能判、判不了时给出"跳过/稍后重试"这类
   有出路的提示，而不是死锁的"点重试"。

请直接修改代码实现这个降级策略，这是产品设计层面的要求，
不是单纯的bug修复——"技术故障不能变成孩子过不去的墙"是这次修改的
核心原则，请在实现时始终以这条原则为准绳。
```
