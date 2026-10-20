# D02 独立跟进复核 · 第二轮（temp 最新 main）

## 基线

- 被复核基线：`temp@1d3b602`（`Fix uncovered D02 parser, lyric timing and chunked search edge cases`）——审查者在此提交了 3 处修复；本报告**不覆盖**此前 `D02_validation_5fb454b.md` 对其当时基线的真实性。
- 主仓库接口基线：`ReBrowser@6a5b2e783e9543a513171a22f21c7df69053797a`（R47），本地/远端一致；9 份 `context` 依赖再次逐字哈希比对 **9/9 一致**。
- 环境：JDK 21.0.10；`javac --release 17`；Android 37.2 `android.jar`；GeckoView 158 `classes.jar`（`f42db6ed…`）；JUnit 4.13.2；Hamcrest 1.3。离线、无设备、无网络。

## 1. 实际执行与结果

```sh
cd preview/rebrowser-d02
ANDROID_JAR=… GECKO_JAR=… JUNIT=… HAMCREST=… ./build.sh   # 退出码 0
```

- 真实重编译 36 个新 Java + 9 个只读 context + 36 个测试类。
- **36 classes / 129 tests / 0 failures / 0 ignored**（`OK (129 tests)`）。历史 124 项未沿用。

| 测试类 | 用例 | 失败 |
| --- | ---: | ---: |
| io.github.rebrowser.bridge.BridgeBudgetTest | 3 | 0 |
| io.github.rebrowser.bridge.BridgeMessagesTest | 10 | 0 |
| io.github.rebrowser.core.DomainResultTest | 3 | 0 |
| io.github.rebrowser.crypto.KeyPurposeTest | 3 | 0 |
| io.github.rebrowser.engine.GeckoHistoryDelegateTest | 2 | 0 |
| io.github.rebrowser.engine.GeckoMediaBridgeTest | 2 | 0 |
| io.github.rebrowser.lyrics.LyricOverlayControllerTest | 4 | 0 |
| io.github.rebrowser.lyrics.LyricOverlayWindowTest | 5 | 0 |
| io.github.rebrowser.lyrics.LyricPositionStoreTest | 4 | 0 |
| io.github.rebrowser.lyrics.OverlayPermissionFlowTest | 4 | 0 |
| io.github.rebrowser.music.MusicActivityTest | 2 | 0 |
| io.github.rebrowser.music.MusicArtworkTest | 3 | 0 |
| io.github.rebrowser.music.MusicLyricsTest | 8 | 0 |
| io.github.rebrowser.music.MusicMediaSessionTest | 3 | 0 |
| io.github.rebrowser.music.MusicNotificationTest | 4 | 0 |
| io.github.rebrowser.music.MusicPreferencesTest | 3 | 0 |
| io.github.rebrowser.music.MusicQueueTest | 3 | 0 |
| io.github.rebrowser.music.MusicStateTest | 4 | 0 |
| io.github.rebrowser.outlook.MailObjectFormatTest | 5 | 0 |
| io.github.rebrowser.outlook.MailRetentionPolicyTest | 5 | 0 |
| io.github.rebrowser.outlook.MailSearchTest | 6 | 0 |
| io.github.rebrowser.outlook.OfficialMailEntryTest | 3 | 0 |
| io.github.rebrowser.outlook.OfflineMailActivityTest | 2 | 0 |
| io.github.rebrowser.outlook.OutlookActivityTest | 2 | 0 |
| io.github.rebrowser.settings.PersonalizationPolicyTest | 4 | 0 |
| io.github.rebrowser.system.ApkHandoffActivityTest | 2 | 0 |
| io.github.rebrowser.system.DefaultBrowserFlowTest | 3 | 0 |
| io.github.rebrowser.system.ForegroundPolicyTest | 4 | 0 |
| io.github.rebrowser.system.MemoryPressurePolicyTest | 4 | 0 |
| io.github.rebrowser.ui.ApplicationsActivityTest | 3 | 0 |
| io.github.rebrowser.ui.DownloadsActivityTest | 2 | 0 |
| io.github.rebrowser.ui.HistoryActivityTest | 3 | 0 |
| io.github.rebrowser.ui.NewTabViewTest | 3 | 0 |
| io.github.rebrowser.ui.SettingsActivityTest | 3 | 0 |
| io.github.rebrowser.ui.SitePermissionsActivityTest | 3 | 0 |
| io.github.rebrowser.ui.SubapplicationSettingsActivityTest | 2 | 0 |

- 追加探针：`D02Probes` **13,641 项断言 PASS**；新增 `D02FollowupProbes` **2,523 项断言 PASS**；资产静态断言 **17/17 PASS**。

## 2. 复现并确认审查者的 3 处修复

| 修复 | 结论 | 本轮证据 |
| --- | --- | --- |
| JSON 数字仅 ASCII 0–9 | **PASS** | 阿拉伯-印度、扩展阿拉伯-印度、天城文、全角数字分别在整数/负号后/小数/指数/指数符号后/`0` 后/`args` 内被拒绝；ASCII `0`、`-0`、`0.5`、`1e-2` 正常 |
| LRC 1/2/3 位小数毫秒精度 | **PASS** | `[00:01]`→1000、`.5`→1500、`.05`→1050、`.005`→1005、`.999`→1999、`[00:59.999]`→59999、`[999:59.999]`→59,999,999 |
| 逐字时间稳定排序后再算重叠/定位 | **PASS** | 乱序 4 段排序正确、同起点保持输入顺序、重叠检测为真、相邻区间不为重叠、`wordIndexAt` 依排序结果 |
| `MailSearch` 跨块自重叠不重复发出 | **PASS** | `aaa/aa`→1、`aaaa/aa`→2（0,2）、`aaaaa/aa` 上限语义正确、1/2/3/7/随机块、整块、Unicode 与代理对跨块均与不重叠 `indexOf` 扫描一致 |

## 3. 本轮新发现并修复的缺陷（2 处）

1. **构建阻断（测试代码）**：审查者新增的 `overlappingLiteralIsNotReEmittedAcrossReaderChunks` 调用了会抛 `IOException` 的 Reader 辅助方法却未声明 `throws`，`javac` 直接失败（3 errors）。
   - 最小修复：该方法签名加 `throws java.io.IOException`；**未放宽任何断言**。
2. **歌词时间戳过宽（生产代码）**：`[00:01.]`（点号后无小数位）被接受为 1000 ms，属于对格式错误输入猜测时间。
   - 最小修复：`MusicLyrics.stamp` 改为 `digits(fraction, dot<0?0:1, 3)`，点号必须携带 1–3 位数字；新增回归测试 `dotRequiresAtLeastOneFractionDigit`，同时覆盖 `.5`/`.005` 精度。
   - 修复后重跑：129 tests 全通过，13,641 + 2,523 项探针全通过。

## 4. 变形测试覆盖（按邮件要求，未无限扩展）

- **邮件搜索**：12 组固定语料（含 `aaa/aa`、`aaaa/aa`、`aaaaa/aa`、`aba`、`中文`、`😀`、跨块代理对、跨块多字符）+ 每组的整块/1/2/3/7/随机块 + 1 条 3,000 字符伪随机语料 × 5 种自重叠 needle（随机块与 1 字符块）；逐项对照不重叠 `String.indexOf` 顺序扫描，校验 offset、截断语义、片段包含与长度上限。
- **歌词**：0/1/2/3 位小数、0 ms、毫秒上限、3 位分钟、4 位小数/秒 60 的拒绝、乱序/同起点稳定排序、重叠与相邻、`wordIndexAt`、非法与超限逐字时间、8,000 条上限。
- **JSON 数字**：4 种 Unicode 十进制数字 × 7 个位置 + `args` 嵌套数字；ASCII 合法/非法整数、小数、指数。

## 5. 未运行（保留 BLOCKED）

- Android 真机/模拟器（无 adb 设备；交付无 Manifest/APK/Gradle 装配）、Activity/View/Notification/MotionEvent/WindowManager、旋转/销毁/重放。
- Gecko 真实 session/delegate 回调、Web 渲染与 CSP 强制、官网样式回归。
- 主仓库整体 AGP 构建与全量回归（由主 Agent 合入后执行）。
- 未使用任何真实账户、密钥、私有邮件、文件、网络、安装或删除操作。

## 6. 产物

- 源码变更：`MailSearchTest`（仅 `throws`）、`MusicLyrics`（点号后必须有 1–3 位小数）、`MusicLyricsTest`（新增 1 个测试）。
- 新增证据：`mailbox/reports/D02_followup_1d3b602-evidence/`（构建日志、逐类 JSON、两套探针、资产检查）。
- 结果代码同步到 `/home/zhao/projects_2026/re_outlook/dsh/result/`（含 `result-evidence.json`、`context/`、`REPORT.md`）。
