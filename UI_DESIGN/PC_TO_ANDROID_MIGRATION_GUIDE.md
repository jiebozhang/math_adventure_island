# 数学冒险岛 · PC(PyQt5) → Android(Kotlin+Compose) 迁移指南

> 适用对象：把桌面端 PyQt5/Python 版「数学冒险岛」完整迁移到 Android App。
> 参考基线：当前 Android 工程 `app/` 是 2026-09-04 从零重建的 Kotlin + Jetpack Compose 工程——OkHttp + Retrofit 直调 Supabase PostgREST（**不引入 supabase-kt**）、Room v3 本地缓存、单 Activity + `when` 状态路由（**非** Navigation Compose）、Coil-SVG。权威事实见 `PROJECT_CONTEXT.md`（无 `codex_prompt_android_V3.md`）。
> PC 端参考：v15 三文件——`math_adventure_island_v15.py`(入口) / `v15_data.py`(题库 1469 行 KNOWLEDGE_MAP+QUESTION_BANK) / `v15_views.py`(PyQt5 视图 1700 行) / `v15_bridge.py`(数据桥接)；同步逻辑 `sync_manager.py`(requests + threading.Event + QSettings + RotatingFileHandler，原子写 JSON tmp+rename)。

---

## 0. 文档导读：先确认「该不该迁移」，再谈「怎么迁移」

迁移不是把 Python 一行行翻译成 Kotlin。**90% 的工作量在重写「运行时模型」**——PC 端是「单进程桌面程序 + 用户自己管理电脑」，App 端是「随时被系统杀死、随时切换网络、随时拿到不同尺寸屏幕、同时跟一群第三方 App 共享内存」的运行环境。代码翻译只在最后 10%。

下面六大问题章节（兼容性→依赖→平台 API→性能线程→UI→权限生命周期→持久化）就是「先做对这 90% 的重写、再做最后 10% 的翻译」的清单。

---

## 1. 版本兼容性问题（最容易一开始就把项目拖废）

### 1.1 Python 版本陷阱：别假设 PC 端 Python 3.10+ 的语法能直译

| PC 端 Python 特性 | Android/Kotlin 怎么落 | 备注 |
|---|---|---|
| `dict` 解构 `a, b = {"a":1,"b":2}.values()` | 用 `Pair`/`data class` | 不要图省事用 `Map` |
| `match-case` (3.10+) | `when (x) { is Foo -> ... }` | Kotlin 的 `when` 是表达式，能力对等 |
| `dataclass(slots=True, frozen=True)` | `data class` (默认 val) | Kotlin data class 默认不可变，**比 Python 还好用** |
| 类型注解 `list[int] \| None` | `List<Int>?` | 注意 `?` 位置（整体可空 vs 元素可空） |
| `from __future__ import annotations` 后所有注解变字符串 | 全部强类型校验 | 这是**好事**，Android 编译期就拦住 |
| `asyncio`/`await` | Kotlin Coroutines `viewModelScope.launch { ... }` | Coroutine 比 asyncio 更适合 UI 生命周期 |

**先做的核对动作**：跑一遍 `python --version` 和 `pip freeze > pc.lock` 然后写 `app/build.gradle.kts` 的 `compileSdk`/`minSdk`——这两份是后面对照「是不是 1:1 翻译」的依据。

### 1.2 Python 2 vs 3 老坑（PC 历史包常见）

- `from typing import List` 在 3.9+ 是 PEP 585 内置 `list[...]` —— **直接删掉**，对应 Kotlin 也是 `List<T>`。
- `print` 语句残留、unicode/bytes 混用、`xrange` 这种**早在迁移前就要清干净**，不要带病进 App。

### 1.3 PyQt5 vs PyQt6/PySide6 接口差异（如果 PC 端历史代码用 PyQt5）

| PyQt5 信号 | PyQt6/PySide6 | 安卓用 |
|---|---|---|
| `pyqtSignal(str)` | `Signal(str)` | Kotlin `Flow<String>` / `MutableStateFlow` |
| `QObject.emit(SIGNAL("xxx"))` | `signalName.emit()` | `MutableStateFlow.value = ...` |
| `pyqtSlot()` 装饰器 | `@Slot()` | 无需，写普通函数即可 |

**迁移原则**：不要在 Python 端先升级到 PyQt6——重写而不是翻译。

### 1.4 Python 包版本到 Android 库版本的不对齐（关键）

| PC PyQt5/Python 包 | Android 对应 | **坑点** |
|---|---|---|
| `requests` | OkHttp/Retrofit | 别用 Ktor 除非你有跨平台诉求，Supabase 官方 SDK 也用 OkHttp |
| `json`/`jsonlines` | kotlinx.serialization | **不要用 Gson**，跟 Kotlin null safety 配合差 |
| `threading.Thread`/`QThread` | Kotlin Coroutines | Qt 的事件循环语义跟协程不一样 |
| `logging` + `RotatingFileHandler` | `android.util.Log` + Timber + 文件 sink | Android Logcat 有 4MB 缓冲上限 |
| `QSettings` | EncryptedSharedPreferences / DataStore | 别用 SharedPreferences 直存敏感信息 |
| `QFileDialog` | `ActivityResultContracts.GetContent()` / Photo Picker | 别自己写文件选择 |
| `QSound` / `PyQt5.QtMultimedia` | ExoPlayer/MediaPlayer + `SoundPool` | Android MediaPlayer 不能并发播 |
| `pyttsx3` / espeak | Android TTS (`android.speech.tts.TextToSpeech`) | **必须先 `setLanguage`+`setOnInitListener`**，否则开机直接 null |
| `Pillow`/`opencv-python` | Android **不要装** OpenCV | 用 CameraX + ML Kit，或干脆不上 |
| `sqlite3` | Room | Room 就是 Kotlin 端的 sqlite3 |
| `reportlab`(PDF 生成) | 客户端改用腾讯/Adobe 在线预览 SDK，或 PdfRenderer | 别把 PDF 渲染做在客户端 |
| `pyinstaller` 打包 | Gradle `assembleRelease` | 走 Play Store 规范，不是 .exe |

### 1.5 题库 JSON Schema 版本对齐（PC → 云端 → App）

PC v15 的 `QUESTION_BANK` 是 Python 字典列表，跟云端 `questions` 表的 PostgREST 字段（如 `story`/`method_hint`/`hidden_traps`/`reference` 这些 jsonb）**不是 1:1 同构**。在 Android 端不要写「用 kotlinx.serialization 把 PC JSON 反序列化」——要：

1. 建一张对照表 `pc_field → cloud_field`（如 `pc.explanation_text` → `hidden_traps`）。
2. PC 推云端走 `push_to_supabase.py` 这条路时已经做了。
3. **App 端只信任云端 schema**，本地只缓存云端拉下来的体，反向不生成。

---

## 2. 依赖库差异（"我装了，编译过，能跑——那没事了"）

### 2.1 必修清单（PC 几乎从来不装的、Android 必须装的）

| Android 必装 | 用途 |
|---|---|
| `androidx.core:core-ktx` | KTX 扩展 |
| `androidx.lifecycle:lifecycle-viewmodel-compose` | Compose + ViewModel 绑定 |
| `androidx.activity:activity-compose` | Compose Activity 容器 |
| 单 Activity + when 状态路由（未引入 navigation-compose） | 页面路由（替代 PyQt `QStackedWidget`） |
| `androidx.room:*` | 本地数据库 |
| `io.coil-kt:coil-compose` + `coil-svg` | 图片加载，**SVG 必须装 coil-svg** |
| `org.jetbrains.kotlinx:kotlinx-coroutines-android` | 协程 |
| `org.jetbrains.kotlinx:kotlinx-serialization-json` | JSON |
| `androidx.datastore:datastore-preferences` | KV 持久化 |
| `androidx.security:security-crypto` | 加密 SharedPreferences（替代 QSettings 存敏感） |

### 2.2 PC 装的但 Android **严禁**装的

- ❌ `opencv-python` —— 用 ML Kit 或跳过视觉功能
- ❌ `pillow` —— 用 BitmapFactory / Glide transformations
- ❌ `pyqtgraph` / `matplotlib` —— Android 端图表用 MPAndroidChart 或干脆不做动态图
- ❌ `reportlab` —— 客户端只渲染不生成 PDF，或者接腾讯文档 SDK
- ❌ `pyinstaller` —— Android 不存在这一环节
- ❌ `requests-html` / `beautifulsoup4` / `selenium` —— 爬虫相关，App 端用户没预期也不允许
- ❌ `pywin32` / `pyobjc` —— 平台绑定，零意义

### 2.3 替换优先级（怎么决定"换"还是"不换"）

**规则：如果 PC 那段代码的功能在 Android 上 70% 用户的场景用不到，就别移植**——直接在 App 里换实现，少做就是少错。

| PC 有但 App 该砍的 | 理由 |
|---|---|
| `WorkerThread` 整体类 | Android 用 Coroutine + Room，`WorkerThread` 没宿主 |
| `QSettings.ini` | 改 EncryptedSharedPreferences + DataStore |
| `QFileDialog.getOpenFileName` 弹系统级文件选择 | Android 13+ 限制跨 App 文件访问，用 `OpenDocument` Contract |
| `RotatingFileHandler` 日志 | Android 开发者用 `adb logcat`，App 内文件 sink 只留崩溃/关键事件 |
| `pyttsx3` 本地 TTS | `TextToSpeech` + 中文语音包需要用户手动下载，**首次先检查引擎是否就绪**，没就绪先 disable 语音按钮 |
| PC 控制台/CLI 调试命令 | Android Studio logcat + Layout Inspector 替代 |

### 2.4 体积与 ABI（手机特别敏感）

- PC 端 PyQt5 + Python 解释器打出来一个项目动辄 100MB+；Android APK 单架构 `assembleRelease` 应该控制 ≤ 60MB。
- **必须打开 R8/ProGuard**（`minifyEnabled true`）。
- **必须按 `abiFilters` 出架构包**：`arm64-v8a`（主流）+ `armeabi-v7a`（老设备）+ `x86_64`（模拟器）；Play Store 上传用 App Bundle（`bundleRelease`）。

---

## 3. 平台 API 差异（PC 当成「桌面」用就行，App 当成「整部手机的租客」）

### 3.1 进程生命周期是 App 最大的原生概念

| PC 概念 | Android 对应 | 处理方式 |
|---|---|---|
| 进程自己说了算 | `Application`/`Activity` 生命周期 | 用 `viewModelScope`/`lifecycleScope`，别在 Activity 里持全局 Coroutine |
| 系统不会杀进程 | `onLowMemory` / `onTrimMemory` | ViewModel 接受 OnCleared 回调，缓存要可重建 |
| `sys.exit()` | **绝对不要调 `System.exit()`** | 用 `finish()` 或 `Activity.finishAffinity()` |
| `QApplication.quit()` | `finishAndRemoveTask()` | 配合用户主动操作，不要程序内自杀 |

### 3.2 窗口 & 屏幕（PC 固定的 1920×1080 vs App 任意屏）

- 不要假设宽度/高度比例——用 `BoxWithConstraints`、`Modifier.aspectRatio(...)`、`ContentScale.Fit`。
- 切横竖屏要重建 Activity（默认行为），需要状态保留用 `rememberSaveable`。
- 折叠屏/分屏多窗口：声明 `android:resizeableActivity="true"`、处理 `Configuration.smallestScreenWidthDp` 变化。
- 刘海屏：`WindowCompat.setDecorFitsSystemWindows(window, false)` + `Modifier.systemBarsPadding()` 处理。

### 3.3 输入方式

| PC 唯一 | Android 多样 |
|---|---|
| 鼠标点击 | 触摸 + 长按 + 双指捏合 + 三指截屏 + 物理键盘/手柄 |
| 物理键盘文字 | 软键盘（IME）会弹上来挡布局 → `imePadding()` |
| 鼠标滚轮 | `LazyColumn`/`Modifier.verticalScroll` |
| 拖拽文件 | 系统分享/SAF（`OpenDocument`） |
| 双击 | `detectTapGestures`/`combinedClickable` |
| 中键 | Android 默认无，别假设 |

### 3.4 通知 / 后台

- PC 用 toast：Android 用 `Snackbar`（短反馈）或 `Notification`（重要 + 跨页面）。
- PC 后台线程跑长任务：Android 上 **必须用 WorkManager** 或前台 Service，否则 8.0+ 直接杀。
- PC 启动项：Android 没有"启动项"概念，要保持后台就用 WorkManager `PeriodicWorkRequest`。

### 3.5 设备硬件能力探测（别假设）

- **相机权限**：`ActivityResultContracts.RequestPermission` 申请，**禁止**像 PC 那样「调用 0 就拒绝处理」。
- **麦克风**（语音输入用）：同上。
- **存储**（应用沙盒外）：Android 11+ 分区存储（Scoped Storage），**别写 `/sdcard/...`** 用 App 私有目录 `context.filesDir` 或 MediaStore。
- **定位**：几乎用不到，教育 App 别申请定位，会被审核拒。
- **TTS 引擎**：很多低端机没装中文语音包，要 fallback 到无语音。

---

## 4. 性能与线程安全（PC 端单线程 GUI 思维在 App 里是灾难的源头）

### 4.1 主线程「不要做这些」清单

| ❌ 不能在主线程做的 | Android 用什么 |
|---|---|
| 网络请求 `requests.post(...)` | `viewModelScope.launch { withContext(Dispatchers.IO){...} }` |
| 本地 JSON 读写 `json.dump()` | `Dispatchers.IO` 协程 |
| Room 查询 `db.questionDao().getAll()` (同步版本) | 协程版本 Dao |
| 图片解码（PC 是 QPixmap，App 是 BitmapFactory.decodeFile） | Coil 内部已做，**别自己手 decode 大图** |
| 加密/哈希 | `Dispatchers.Default` |
| 大列表过滤 | `Dispatchers.Default` + LazyColumn + `key()` |
| 文件压缩/解压 | WorkManager 长任务 |

> **金标准**：主线程跑过的每一行代码，脑里默念一句「这行做 50ms 就会被系统标 ANR」。

### 4.2 协程 vs Thread 的取舍（替代 PC 的 QThread）

- **Coil 加载图片**自动切线程，不用管。
- **Room DAO** 用 `suspend fun` + `Flow<List<Entity>>`，**不要**自己 `Thread { db.runInTransaction{...} }`。
- **多个并发请求** 用 `coroutineScope { val a = async{...}; val b = async{...}; a.await() to b.await() }`，比 `threading.Thread` 简单 10 倍。
- **取消语义**：协程被 `viewModelScope` 自动包住，Activity 销毁就取消，**不要自己 catch CancellationException 然后打印 stack**——吞掉就破坏了取消语义。

### 4.3 列表渲染（最容易掉帧的地方）

PC 是 `QListView` + model/view；Android 是 Compose `LazyColumn` + `key`：

```kotlin
LazyColumn {
    items(questionList, key = { it.id }) { q -> QuestionCard(q) }
}
```

- 必须有 `key = { it.id }`，否则重组时 DiffUtil 行为诡异，列表项闪。
- 不要在 item 内放重量级 Composable（巨大 `Column` 嵌套），超过 50 个节点就抽组件出去。
- 图片用 Coil `AsyncImage`，不要 `BitmapFactory.decodeResource`。

### 4.4 内存监控

PC 没限制的「本地缓存 100MB 题库图片」Android 会爆 `OutOfMemoryError`：

- 图片加载前查 `inSampleSize`，或用 Coil `size(Size.ORIGINAL)` 自己控。
- 全局用 `coil.imageLoader` 单例，配置 `diskCache`/`memoryCache`（默认 25% 内存上限）。
- LruCache 自定义（题库的题目文本/选项/解析）做两段式：内存 LruCache + 磁盘 Room。

### 4.5 内存泄漏（PC 没所谓，App 直接 OOM 崩溃）

PC 端 `self.thread = QThread()` 然后忘了 quit 的事，App 端同样会爆，但更敏感：

- Activity 持有 Context → 静态字段 → 内存泄漏。
- Coroutine 跑在 `GlobalScope` 又没 cancel → Activity 销毁还在跑 → 持有 ViewModel → 内存泄漏。
- **规则**：Coroutine 永远跟着 `viewModelScope` 或 `lifecycleScope`，**永远不要 `GlobalScope.launch`**。

---

## 5. UI 适配（WorkBuddy 创意设计模式：先重设计，再翻译代码）

> **WorkBuddy 设计哲学**（已经在 Android 工程里贯彻）：
> **「资源化的先于提问 / 有观点 / 用能力换信任 / 灵魂&记忆的连续性」**——落到 UI 上就是：**不照搬 PC 的版面，把信息按"孩子一低头就懂"重新打散**，**让孩子的主流程最短**（看题 → 答 → 看反馈 → 下一题），**让家长控制台在"功能完整但孩子够不着"那侧**。

### 5.1 PC 端 UI 现状（v15_views.py 简述）

PC 端用 `QStackedWidget` 装多个 `QWidget` 页（主页/答题/怪獣图鉴/设置），单窗口固定 1280×720，鼠标点击，键盘文字输入。**直接 1:1 抄到 Android 上必然割裂**——同一个版面在 6.5 寸手机上变成「滚动条地狱」。

### 5.2 重新设计原则（WorkBuddy 思路具体化）

1. **孩子视角优先**：孩子能看见的页面 ≤ 3 个——选年级/选单元、答题、看反馈。
2. **家长控制台挪出"主流程"**：6 位数字 PIN 或者长按 logo 10 次进入，参考 PC 端的「家长控制台」按钮但**不放在主界面**。
3. **语音输入比打字更顺**：PC 是键盘 + 中文拼音，App 直接调系统语音；听不清再 fallback 到键盘。
4. **核心反馈不上 Modal**：PC 端点"check"弹大窗，App 改成顶部 Snackbar/动画，因为 App 用户期待"操作-反馈-下一步" 0 跳转。
5. **错题本不是菜单项**：App 把"做过且错的题"自动沉到"训练营"页，PC 端当年是单独一栏，现在放底部 Tab。

### 5.3 重新设计的页面映射表

| PC 端页面 (PyQt5) | Android App 对应 | 改动点 |
|---|---|---|
| 主菜单 `MainWindow` | `MapScreen` (选年级学期) | PC 是下拉框，App 改成卡片网格让孩子能戳 |
| 答题 `QuestWidget` | `QuestScreen` | PC 横向布局，App 改成纵向 + sticky 进度条；动画反馈 |
| 判分弹窗 `ResultDialog` | 顶部结果条 + 怪兽动画反馈 | 不挡视野 |
| 怪兽图鉴 `CodexWindow` | `MonsterCodexScreen` (二级页) | PC 是左列表右详，App 改卡片陈列 + 点击进入 |
| 训练营/错题 | `TrainingCampScreen` | 复用作答组件 + 标记来源 |
| 日记 | `DiaryScreen` | PC 是 HTML 富文本，App 改成 Markdown 渲染 |
| 家长控制台 | `ParentConsoleScreen` (**PIN 门禁**) | **这是 WorkBuddy 模式的关键改动**——只有"长按 logo 10 次"才能进；PC 是裸按钮，小孩会点 |
| 设置 | 抽屉/Settings 入口 | App 顶部头像里的菜单 |

### 5.4 Compose 设计 Token（统一视觉）

不要在每个 Composable 写死颜色/字号。建 `ui/theme/`：

- `Color.kt`：主题色 + Material3 映射
- `Type.kt`：字号、字重、行高（含 PC 没处理的中文优先 Noto Sans CJK）
- `Shape.kt`：圆角
- `Dimens.kt`：间距 token（4/8/16/24/32dp）

PC 端如果调了配色，所有 `QSS`（`setStyleSheet("background-color: #...")`）要 1:1 映射到 `Color.kt`。

### 5.5 设计上对 PC 端应该"砍掉"的元素

- ❌ **状态栏**（PC 窗口顶部信息条）：手机上对应 `TopAppBar`，内容要克制
- ❌ **菜单栏**（File/Edit/View）：App 抽屉替代
- ❌ **工具栏图标 hover tooltip**：手机上没 hover，改成长按弹气泡
- ❌ **窗口大小可拖**：App 屏幕是固定的
- ❌ **右键菜单**：手机上"长按"替代，且**只放"删除这种非频繁操作"**，避免和滚动冲突

### 5.6 设计上对 PC 端应该"加"的元素

- ✅ **微动效**：进下一题弹性动画、怪兽出场抖动、PC 没有的"对错眼色变化"
- ✅ **离线空状态**：断网时 `MapScreen` 显示"上次同步于 X 分钟前"，PC 没所谓
- ✅ **空内容兜底**：题库空时不是个空白窗，是带怪兽图标的"还没题，去家长控制台上传"
- ✅ **首次启动引导**：孩子的上手成本 PC 端是"看说明文档"，App 改成 3 屏 swipeable onboarding

### 5.7 可访问性（家长端也不放过）

- 触控目标 **≥ 48dp×48dp**（Material A11y 准则），PC 端按钮是 32px 起步，要全改
- 颜色不作为唯一信息载体（红/绿对色盲用户），图标+文字组合
- 朗读支持：`contentDescription` 必填
- 系统字号跟随：不用 sp 全锁死，留 `LocalDensity` 余量

---

## 6. 权限与生命周期管理（PC 端一句「用户同意」就完事，App 要走完整流程）

### 6.1 权限申请流程（Android 6.0+ 动态权限）

| 权限 | PC 端映射 | 申请时机 | 必填 vs 可选 |
|---|---|---|---|
| `INTERNET` | 无（默认有） | **Manifest 声明即可，不需要 runtime request** | 必填 |
| `RECORD_AUDIO` | 麦克风（PC 系统级） | 首次按语音输入按钮时 | 可选，无麦克风=语音按钮置灰 |
| `READ_MEDIA_IMAGES` | 文件对话框 | 家长控制台上传题图时 | 可选 |
| `POST_NOTIFICATIONS` | 无 | Android 13+ 必须 runtime 申请 | 可选 |
| `FOREGROUND_SERVICE` | 无 | 同步长任务要前台 Service 时声明 | 必填 |
| `WAKE_LOCK` | 无 | 仅下载大文件用，避免电池投诉 | 按需 |
| `READ_EXTERNAL_STORAGE` | 无 | **Android 13+ 已废弃**，改用 Photo Picker | 别申 |

### 6.2 申请代码示例（Compose）

```kotlin
val micPermission = rememberPermissionState(android.Manifest.permission.RECORD_AUDIO)
Button(onClick = {
    if (micPermission.status.isGranted) startVoiceInput()
    else micPermission.launchPermissionRequest()
}) { Text("语音") }
```

依赖：`accompanist-permissions` 或 `androidx.activity.compose` 原生 `rememberLauncherForActivityResult`。

### 6.3 生命周期相关的状态保存

PC 端数据全在内存/硬盘，`QSettings` 一行命令就有。Android：

- **进程被杀也要保住的东西** → Room + DataStore
- **屏幕旋转要保住的东西** → `rememberSaveable`（不是 `remember`！）
- **ViewModel 短生命周期要保住** → `viewModelScope` 内的 `MutableStateFlow`
- **跨进程/跨 App 通信要保住** → `ContentProvider`/`WorkManager.Result`（极少用）

### 6.4 Activity 重建陷阱（Android 独有）

PC 转屏没事，App 转屏 Activity 重建，**所有未保存的 Compose 状态全丢**：

```kotlin
// ❌ remember 在转屏后丢
var text by remember { mutableStateOf("") }
// ✅ 转屏后还在
var text by rememberSaveable { mutableStateOf("") }
```

### 6.5 后台限制（App 一退出就被杀）

PC 端程序自己退就行。Android 上：

- 即使用户按 Home，App 进程还是可能活几十秒，之后被系统杀
- 同款 App 二次打开会冷启动 → 所有内存里的 Map/List 全丢
- **必须设计成"重启可恢复"**：进来先看本地缓存（Room），再做网络补全

### 6.6 应用商店合规（Play Store 上架硬性）

- 隐私政策 URL：必填（教育 App 对未成年人数据特别敏感）
- 目标 API level ≥ 34（每年升）
- 内容分级：IARC 问卷
- 数据安全声明：是否收集位置、麦克风、相机、通讯录——会显示在商店详情页
- 家庭政策：如果 App 13 岁以下，要走 Designed for Families 流程，**要明确声明不展示广告、不收集个人信息**

---

## 7. 数据持久化方案（PC 是「自己写文件」，App 是「沙盒 + Cloud」）

### 7.1 存储分层（PC 没这个概念，App 必须分清）

| 存储层级 | 用途 | 实现 |
|---|---|---|
| **用户偏好 KV** | 主题、声音开关、年级 | DataStore Preferences |
| **加密 KV** | 登录态 token | EncryptedSharedPreferences |
| **结构化业务数据** | 题库、进度、错题 | Room |
| **大文件** | 题图缓存、音频导出 | App 私有目录 `context.filesDir/xxx` |
| **公共媒体**（需要让用户在相册看见） | 答题截图导出 | MediaStore（Android 10+） |
| **跨进程同步** | WorkManager 任务 | Room 同步表 + WorkManager |
| **云端** | 跨设备数据 | Supabase PostgREST + Storage |

### 7.2 Room vs PC SQLite 的差异

- Room = SQLite + 编译期检查 DAO SQL + Flow 支持
- **Schema 升级**：`Migration` 对象链，不要自己写 `onUpgrade` 乱改
- **事务**：`db.withTransaction { ... }`（Kotlin 协程版），不要 PC 的 `BEGIN...COMMIT`
- **同步阻塞**：永远走 `suspend` 或 Flow，**禁止**主线程 `db.questionDao().getAll()` 同步版本
- **预填充**：用 `RoomDatabase.Callback` 启动时跑 SQL（替代 PC 的 `if not exists then create`）

### 7.3 题库图片持久化（PC 是硬盘静态文件，App 要跟随 App 生命周期）

- **打包时塞 APK**（Android 工程已自带 `assets/star_grade3/`）→ 用 `file:///android_asset/...` 引用（Coil 支持）
- **下载到本地缓存** → `context.cacheDir/questions/<id>.svg`
- **从云端拉** → Coil 内部 diskCache 帮你存一份（按 URL key），**别自己写文件管理**
- **导出/分享** → MediaStore.Images，让用户相册能看见

### 7.4 同步层（Supabase）现状 & 隐患

PC 端 `sync_manager.py` 这套已经验证能跑，Android 这边已做了对应实现（OkHttp + Retrofit 直调 PostgREST，非 supabase-kt）：

- ✅ 登录（`/auth/v1/token`）+ token 刷新
- ✅ 题库增量拉取（`updated_at=gt.`）
- ✅ 进度双向同步（`pendingUpload` 标志位）
- ⚠️ **删除策略**（schema.sql 没 DELETE RLS，要先建策略否则 RLS 静默拦截）
- ⚠️ **Storage bucket**（`question-assets` 没建的话 raster 图全 404）

### 7.5 加密与权限

- 云端 access_token 必走 EncryptedSharedPreferences，不走 SharedPreferences
- 题库进度 `progress` 表里 `user_answer` 字段如果有孩子手写答案，**别纯文本上云**（隐私 + 编码注入风险），最低做 base64
- 日志文件不写本地明文，落 `logcat -d` 后导出 txt 也只导出非敏感片段（用 Timber 的 `Tree` 过滤器）

### 7.6 PC JSON 文件 → Android Room 的迁移路线

1. 第一版：双轨——PC 端 JSON 文件继续写，Android 走 Room+云端。**别想"先兼容一段时间再说"，兼容代码会成为永久维护债**。
2. 把 PC 的 `inject_builtin_qs.py` 跑一遍，把 16 道内置题灌进云端。
3. Android 第一次启动时检查 Room 是否空，空就拉云端拉满。
4. PC 端不再写本地 JSON（保留旧版本作为只读回退 30 天）。
5. 30 天后下掉 PC 那段。

---

## 8. WorkBuddy 创意设计模式（应用层抽象——不只是迁移，是升级）

把 WorkBuddy SOUL 里的四条原则**翻译成 Android 工程的工程决策**：

### 8.1 「资源化的先于提问」→ 工程上叫 **「离线优先」**

| 原则 | Android 工程决策 |
|---|---|
| 别让用户等 | App 启动 → Room 缓存先渲染 → 后台同步增量 |
| 别让用户配网络 | 重试 3 次 + 安静降级 |
| 别让用户清理数据 | Room 里加 `pendingUpload` 标志位，永远不丢 |

### 8.2 「有观点」→ 工程上叫 **「家长控制台门禁」**

| 场景 | 决策 |
|---|---|
| 家长控制台入口 | **不放在主界面**，长按 logo 10 次 |
| 删除题目 | 二级确认，"是否要删除这道题？是/否" |
| 充值/订阅 | 不做——**教育 App 不收费**是 WorkBuddy 价值观之一 |
| AI 出题边界 | 默认保守，自动画图只画规则几何（方块+标签） |

### 8.3 「用能力换信任」→ 工程上叫 **「可观测性 + 测试覆盖」**

- 关键路径单元测试：判分逻辑、题库拉取重组、id 拼接规则
- 关键路径 instrumentation 测试：登录 → 拉题库 → 答题 → 上传进度
- Crash 上报：Firebase Crashlytics（**不要传任何 PII**，题面/答案脱敏）
- 灰度发布：Play Store internal testing → closed testing → production

### 8.4 「灵魂 & 记忆的连续性」→ 工程上叫 **「账号体系 + 题库云端」**

- 孩子用同一个账号在手机/平板/PC 之间无缝切换
- 错题本、训练营进度跨设备延续
- "这个孩子上一次做到第 12 题" —— 不管在哪个设备都能继续

---

## 9. 迁移实施步骤（具体到 PR 维度的可执行清单）

### Phase 1：准备（1 周）

- [ ] 跑通 `python --version` / `pip freeze > pc-deps.txt`
- [ ] 跑通 PC 当前版本 `python math_adventure_island_v15.py` 录一段 demo 视频
- [ ] 列 `v15_data.py` 的 `KNOWLEDGE_MAP`/`QUESTION_BANK` 全部键名，导出 `pc_field_map.json`
- [ ] 建分支 `feat/pc-to-android-migration`

### Phase 2：脚手架（1 周）

- [ ] 用 `Empty Compose Activity` 模板建项目，`minSdk=24` `targetSdk=36`
- [ ] 直接用 Retrofit + OkHttp 直调 Supabase PostgREST（不引入 supabase-kt）
- [ ] 建 `local.properties` 注入 `SUPABASE_URL` + `SUPABASE_ANON_KEY`（不入 git）
- [ ] 接 Room v3 + DataStore + EncryptedSharedPreferences
- [ ] 配协程 `viewModelScope`/`Dispatchers.IO`/`Dispatchers.Default`
- [ ] 建 `ui/theme/` 三个文件 + `Dimens.kt`

### Phase 3：单页迁移（3 周，按页面 PoC 顺序）

**顺序 1：登录页**（用已注册账号，跑通加密 token 落盘）
**顺序 2：选年级/学期页**（MapScreen 的网格版本，验证 UI Token 体系）
**顺序 3：答题页**（含题图渲染 + matching 题交互，这部分难度最高，放第三个）
**顺序 4：判分反馈**（顶条 + 怪兽动画 + 错题自动入库）
**顺序 5：训练营**（复用答题页，标记来源）
**顺序 6：怪兽图鉴**（装饰性，简单）
**顺序 7：日记**（Markdown 渲染）
**顺序 8：家长控制台**（门禁+上传题+AI出题+管理题）

每个 PR 一个页面，**别想着"一次 PR 迁完整个 App"**——会变成巨型 PR 没法 review。

### Phase 4：数据迁移（1 周）

- [ ] `inject_builtin_qs.py` 升级版，把 PC 内置 q001~q016 推到云端
- [ ] **验证 id 拼接规则** PC 和 Android 一致（`sync_<uuid>`）
- [ ] 跑一次全量同步，确认 16 道题在两端都能看到
- [ ] PC 端改成只读，30 天观察期

### Phase 5：打磨（1 周）

- [ ] 离线/弱网/无网 三态全跑通
- [ ] 横竖屏切换不丢状态
- [ ] 快速来回切换页面不漏判分
- [ ] 后台 5 分钟再回来不丢进度
- [ ] Crash 上无 NPE/ANR

### Phase 6：合规与发布（1 周）

- [ ] 隐私政策、用户协议、儿童合规声明
- [ ] IARC 分级
- [ ] Play Console 上 internal testing → closed → production
- [ ] App Bundle + R8 minify

---

## 10. 验证方案（每个 Phase 都要有可量化的"过线"标准）

### 10.1 功能验收

| 用例 | 通过标准 |
|---|---|
| 离线启动 | 杀掉网络启动 App，**至少能打开本地缓存过的题** |
| 跨端 id 一致 | PC 答对题目 `qid=sync_abc-123` 后云端 progress 一致，Android 同步后看到同一题被标记 |
| matching 题渲染 | 三年级上册"观察物体" 4 方位填空题，**4 张候选标签、点击空格弹出选择、提交判分** |
| 题图渲染 | SVG / Raster / Asset 本地图片三种来源都能显示 |
| 错题入库 | 答错后能在"训练营"页找到 |
| 家长门禁 | 短按 logo 5 次以下不出现 PIN 输入框；长按 logo 10 次才出现；输错 PIN 提示"再试一次" |
| TTS 边界 | 系统没装中文语音包时，语音按钮置灰，不崩 |
| 横竖屏 | 旋转后所有题面/输入框内容保留 |
| 网络抖动 | mock 50% 丢包率，同步成功率 ≥ 95%（3 次重试内） |

### 10.2 性能验收

| 指标 | 目标 | 测量方法 |
|---|---|---|
| 启动到首屏 | ≤ 1.5s（中端机） | Macrobenchmark |
| 答题切下一题 | ≤ 200ms | Compose Layout Inspector / Systrace |
| APK 大小（arm64-v8a） | ≤ 60MB | `unzip -l app-release.apk` |
| 内存峰值 | ≤ 300MB | Android Studio Profiler |
| 主线程 16ms 帧率 | ≥ 90% 帧 | GPU 渲染模式 / gfxinfo |

### 10.3 兼容验收（机海）

| 维度 | 最低 |
|---|---|
| minSdk | 24（Android 7.0，覆盖 96% 设备） |
| 屏幕 | 4.7" - 7.0"，foldable 单屏 |
| 架构 | arm64-v8a（主）+ armeabi-v7a（兜底） |
| OEM | 华为 EMUI / 小米 MIUI / 三星 OneUI 至少各跑过一台真机 |
| 弱网 | 2G/3G 降级（自动调画质） |

### 10.4 安全 & 隐私验收

| 用例 | 通过标准 |
|---|---|
| token 落盘 | EncryptedSharedPreferences（root 后也拿不到明文） |
| 日志脱敏 | 用户输入的答案、PIN、token 不进 logcat |
| 网络 | OkHttp cleartext 关闭（仅 HTTPS） |
| 子进程 | 不导出不需要的 `<service>`/`<receiver>`/`<provider>` |
| Cloud token 过期 | 401 自动 refresh，refresh 失败才 logout |
| 儿童数据 | 不向第三方分析 SDK 传 PII；Crashlytics 关 PII |

### 10.5 回归验收

- 和 PC 端跑同一份题库 → 同一道题判分结果 100% 一致
- 和 PC 端跑同一组进度 → 同步后两端的"已掌握"列表 100% 一致
- 不依赖 PC 的场景（如纯移动端 AI 出题）单独 PoC 跑通

### 10.6 灰度（生产前最后一步）

- internal testing：项目组 + 5 户家庭（校内老师学生）
- closed testing：30 户
- production rollout：5% → 25% → 100%
- 每个阶梯观察 7 天 crash-free rate ≥ 99.5% 才升

---

## 11. 反模式清单（迁移中最容易踩的坑，列出来方便排查）

| 反模式 | 后果 | 正确做法 |
|---|---|---|
| 把 Python 函数一比一翻成 Kotlin 函数 | 协程被忘掉，全跑在主线程 | 每个原 Python 函数先标"I/O 还是 CPU"，再选 Dispatcher |
| 用 `runBlocking { ... }` 在 Composable 里 | 转屏/切换页面卡死 | 全用 `viewModelScope.launch` |
| 把 PC 的 `QListView` 模型放到 Compose 当 `Column` 直接塞 1000 项 | 滚 1 帧 200ms | `LazyColumn` + `key = { it.id }` |
| 把 PC 的 `QPixmap` 缓存逻辑搬过来 | OOM | Coil 内置缓存够了 |
| 把 PC 的 `print` 调试满屏加 | logcat 刷屏 | Timber + 自写 `ReleaseTree` 只在 debug 树打印 |
| 把 PC 的 `with open('a.json') as f: json.load(f)` 整体搬过来 | ANR | `Dispatchers.IO + 协程` |
| 把 PC 的 `QSettings.value("k", default)` 搬过来 | 同名 key 覆盖、敏感信息明文 | DataStore + EncryptedSharedPreferences |
| 把 PC 的 `QFileDialog` 整套搬过来 | 跨 App 文件访问违规崩溃 | `ActivityResultContracts.OpenDocument` |
| 把 PC 的 `self.thread.start()` 搬过来当协程 | 内存泄漏 | 全 `viewModelScope.launch` |
| 把 PC 的 `pixmap.save("a.png")` 当截图分享 | 应用沙盒外路径全 403 | MediaStore + `Intent.ACTION_SEND` |
| PC UI 一比一搬 | 小屏炸裂、家长控制台小孩误触 | 工作量最大的重设计 |
| 在 PC 端先小改兼容 | 兼容代码变成永久债 | 设置里程碑日，老 PC 版本下架 |
| 用 `GlobalScope.launch` | 内存泄漏 + 编译期警告 | `viewModelScope`/`lifecycleScope` |
| 把 PC 那套 SQLite 直接 db.execSQL 搬 | 没有迁移管理 | Room + Migration |
| 装一个大而全的库（Glide 全家桶） | 体积膨胀 | Coil 一把梭 |

---

## 12. 总结——一句话总览

> **迁移的本质不是翻译，是重写一套「理解移动端运行环境」的系统。**
> PC 端那 4000 行 Python 是「功能蓝图」，Android 这边要先把「进程/权限/网络/手势/电量/版本兼容/可访问性/儿童合规」七件外衣穿对，最后再去翻译功能。
> WorkBuddy 的精神：**重设计不重翻译**——把 PC 端的家长控制台从主界面挪走、用怪兽动画替代判分弹窗、让孩子的主流程从 5 步压到 3 步，这些都不是"翻译"，是"再设计"。
> 
> 按 Phase 1→Phase 6 的节奏推进，每个 Phase 走完做对应验收，不要一次性迁完所有页面。
