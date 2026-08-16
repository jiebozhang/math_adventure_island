# 数学冒险岛 — 构建环境速查（ENV_NOTES）

> 本文件记录本机已验证可用的构建环境。**新会话第一步先读这个文件**，不要重新翻查
> JAVA_HOME / Gradle wrapper 损坏等问题。
> 最后验证时间：2026-08-16（每项均为当天实测，非凭记忆抄写）。

---

## 1. JDK

| 项目 | 真实状态（2026-08-16 实测） |
|---|---|
| 系统默认 `JAVA_HOME` | `D:\Program Files (x86)\Java\jdk1.8.0_341` → **JDK 1.8.0_341**。PATH 上的 `java` 也是它 |
| 为什么不能用 | AGP 9.1.1 / Gradle 9.3.1 要求 **JVM 17+**，JDK 8 直接失败 |
| **可用 JDK 21** | `D:\AndroidSdk\jdk21clean\jdk-21.0.12+8`（Temurin 21.0.12+8 LTS，`bin/jlink.exe` 存在，AGP 的 JdkImageTransform 依赖它） |
| 已排除的目录 | `D:\AndroidSdk\jdk21test2\` 是**空目录**，不是 JDK；`C:\Program Files\Android\Android Studio\jbr` 不存在；IDE 自带 JBR（IntelliJ/PyCharm）之前验证过缺 `jlink.exe`，别用 |
| SDK 位置 | `local.properties` 里 `sdk.dir=D:/AndroidSdk`；`ANDROID_HOME`/`ANDROID_SDK_ROOT` 环境变量为空，不影响构建 |

**怎么指定 JDK（Git Bash）：** 每条 gradle 命令前加前缀，或先 export 一次：

```bash
# 方式 A：逐条命令前缀（推荐）
JAVA_HOME="/d/AndroidSdk/jdk21clean/jdk-21.0.12+8" ./gradlew ...

# 方式 B：会话内一次性设置
export JAVA_HOME="/d/AndroidSdk/jdk21clean/jdk-21.0.12+8"
```

> 注意：工作目录是中文路径，`cd` 记得加引号：
> `cd "/c/Users/zhang/PycharmProjects/PythonProject/数学冒险岛"`

---

## 2. Gradle

- **Wrapper 版本：9.3.1**（`gradle/wrapper/gradle-wrapper.properties` 的 `distributionUrl`）
- dist 缓存目录：`~/.gradle/wrapper/dists/gradle-9.3.1-bin/`，里面有两个 hash 目录：

| 目录 | 角色 | 状态（2026-08-16 实测） |
|---|---|---|
| `23ovyewtku6u96viwx3xl3oks/` | **活动解压目录**（gradle 真正用的） | 316 个文件，含 `gradle-9.3.1/bin/gradle.bat`、`.zip.ok` 标记，**无 `.part` 残留** → 完整可用 |
| `cache_hash/` | 手动备份（比活动目录多一个 `gradle-9.3.1-bin.zip` 文件本体，其余一致） | 317 个文件，完整 |

**当前结论：已修复 / 无需操作。** `JAVA_HOME="..." ./gradlew --version` 实测正常启动：
`Gradle 9.3.1`、`Launcher JVM: 21.0.12 (Temurin)`。之前某会话遇到过活动 hash 目录损坏，
现在已自愈（或已被完整副本覆盖过）。**不要再执行任何覆盖命令。**

> 若将来又出现 wrapper 解压损坏（报错形如 "Could not move file ... to ..." 或解压失败），
> 用下面命令从备份恢复，或直接删目录让 gradle 重新下载：
> ```bash
> # 从 cache_hash 备份覆盖活动目录（Git Bash 路径，~ = /c/Users/zhang）
> rm -rf ~/.gradle/wrapper/dists/gradle-9.3.1-bin/23ovyewtku6u96viwx3xl3oks/gradle-9.3.1
> cp -r ~/.gradle/wrapper/dists/gradle-9.3.1-bin/cache_hash/gradle-9.3.1 \
>       ~/.gradle/wrapper/dists/gradle-9.3.1-bin/23ovyewtku6u96viwx3xl3oks/
> # 或干脆重下：
> rm -rf ~/.gradle/wrapper/dists/gradle-9.3.1-bin/23ovyewtku6u96viwx3xl3oks
> JAVA_HOME="/d/AndroidSdk/jdk21clean/jdk-21.0.12+8" ./gradlew --version
> ```

---

## 3. 项目关键版本

全部来自 `gradle/libs.versions.toml` + gradle 缓存实测：

| 项 | 版本 |
|---|---|
| AGP | **9.1.1** |
| Kotlin | **2.2.10** |
| KSP | 2.3.5 |
| Compose BOM | **2024.09.00** |
| **material3 实际解析版本** | **1.3.0**（gradle 缓存 `androidx.compose.material3/material3/1.3.0/` 实测；BOM 2024.09.00 → material3 1.3.0。`gapSize`/`drawStopIndicator` 等参数 1.3.0 里就有，别再怀疑） |
| compileSdk / targetSdk | 36（compileSdk 用 `release(36) { minorApiLevel = 1 }`）/ **36** |
| minSdk | **24** |
| applicationId | `com.aistudio.mathadventure.kxmpzq` |
| source/target 兼容 | Java 11 |

`gradle.properties` 关键项：`-Xmx4g`、`configuration-cache=true`、`kotlin.compiler.execution.strategy=in-process`（防 Kotlin daemon 连不上）、`org.gradle.workers.max=4`、`android.overridePathCheck=true`（中文路径必需）。

---

## 4. 常用命令速查（Git Bash，可直接粘贴执行）

```bash
# 0) 一次设好 JDK（以下命令默认已设）
export JAVA_HOME="/d/AndroidSdk/jdk21clean/jdk-21.0.12+8"
cd "/c/Users/zhang/PycharmProjects/PythonProject/数学冒险岛"

# 编译（不打包）
./gradlew :app:compileDebugKotlin

# 打包 debug APK
./gradlew :app:assembleDebug
# 产物：app/build/outputs/apk/debug/app-debug.apk（约 24M）

# 安装到已连接设备/模拟器
./gradlew :app:installDebug
# 或手动：adb install -r app/build/outputs/apk/debug/app-debug.apk
# adb 已在 PATH（1.0.41），SDK platform-tools 在 D:/AndroidSdk/platform-tools

# 只跑 lint
./gradlew :app:lintDebug
# 报告：app/build/reports/lint-results-debug.html

# git 工作流：每个 diff 前先 commit 当前状态，改完再单独 commit
git status
git add -A
git commit -m "改动说明"

# 发布 GitHub Release（gh 不在 PATH，用完整路径，见第 6 节）
GH="/d/Program Files/GitHub CLI/gh.exe"
"$GH" release create vX.Y.Z app/build/outputs/apk/debug/app-debug.apk \
  --repo jiebozhang/math_adventure_island \
  --title "数学冒险岛 vX.Y.Z" \
  --notes "测试版本，使用debug签名，仅供内部测试安装。"
```

不加 `--console=plain` 时输出带彩色进度；需要干净日志可加。构建正常约 30s（配置缓存复用）。

---

## 5. 已知环境限制 / 注意事项

- **Bash 工具权限分类器偶尔临时不可用**：个别命令第一次会报分类失败，重试一次即可，不是项目问题。
- **无网络代理**（环境变量里没有 `http_proxy`/`https_proxy`）。Gradle 依赖访问均直连且在本机实测可用：
  - `services.gradle.org`（wrapper 下载）
  - `google()` → `dl.google.com` / `maven.google.com`（Google Maven，含 sources jar 下载）
  - `mavenCentral()`（Maven Central）
  - 本会话未遇到任何被拒绝访问的域名；若将来出现"无法访问仓库"，优先怀疑网络而非域名白名单。
- **Robolectric / Roborazzi 截图测试在本机跑不动**：test worker 会崩（`ClassNotFoundException` 类错误），`testDebugUnitTest` 不可用作验证手段。**UI 验证一律走"打 APK → 真机装"**。
- **Secrets 插件**依赖项目根目录 `.env` / `.env.example`；`google-services.json` 缺失时是 WARN 透传，不阻塞构建。
- 项目**已初始化 git**（2026-08-16），远程 GitHub `jiebozhang/math_adventure_island`，默认分支 main。**约定：每个 diff 前先 commit 当前状态，改完再单独 commit**，让 `git log -p` / `git diff` 始终可信。详见第 6 节。
- **gh CLI 不在 PATH**：装在 `D:\Program Files\GitHub CLI\gh.exe`，要用完整路径调用，或先 `export PATH="/d/Program Files/GitHub CLI:$PATH"`。
- **winget 未安装**（bash 与 cmd 均确认）：以后装新工具不能走 winget 路线。
- 系统默认 JDK 是 8，**任何新开的终端都必须重新指定 JDK 21**（环境变量不跨会话保留）。

---

## 6. GitHub 远程仓库与 gh CLI

（2026-08-16 实测）

| 项 | 值 |
|---|---|
| 远程仓库 | `https://github.com/jiebozhang/math_adventure_island.git`（origin） |
| 默认分支 | `main` |
| 首个 commit | `2aff821` 初始快照：ENV_NOTES.md写入后的当前状态 |
| **gh CLI 路径** | `D:\Program Files\GitHub CLI\gh.exe`（**不在 PATH**，必须用完整路径） |
| gh 版本 | 2.97.0 |
| 登录账号 | `jiebozhang`（token 在系统 keyring，不在 hosts.yml 明文里） |
| token scopes | `gist, read:org, repo, workflow` |
| 已有 Release | `v0.1.0`（附 `app-debug.apk` 24M，debug 签名测试版） |

**gh 用法（完整路径）：**
```bash
GH="/d/Program Files/GitHub CLI/gh.exe"

# 检查登录状态
"$GH" auth status

# 查看某个 Release
"$GH" release view v0.1.0 --repo jiebozhang/math_adventure_island

# 创建 Release（先确认版本号无冲突；有冲突就停下报告，不要改版本号或强制覆盖）
"$GH" release create vX.Y.Z app/build/outputs/apk/debug/app-debug.apk \
  --repo jiebozhang/math_adventure_island \
  --title "数学冒险岛 vX.Y.Z" \
  --notes "测试版本，使用debug签名，仅供内部测试安装。"
```

**注意事项：**
- 历史覆盖方式：本地为准，force push 覆盖远程 main（远程旧历史不要）。推送前用 `git ls-files` 复核，确保 `debug.keystore`、`.env`、`local.properties` 等敏感文件没被带上（`.gitignore` 已挡，仍应每次复核）。
- 发新版 Release 前先重编 APK（第 4 节 `assembleDebug`），产物固定路径 `app/build/outputs/apk/debug/app-debug.apk`。
- 发布的是 **debug 版 APK**（debug keystore 签名），notes 必须标注"测试版本，仅供内部测试"。
