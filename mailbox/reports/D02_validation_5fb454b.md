# D02 审查修正版 · 接续验证报告

- 验证基线（被审查交付）：`temp@5fb454b5cf93db5b4cd15a225dc0ff6ba3d4446e`（`Review D02 runtime boundaries and add focused validation mailbox`，短SHA `5fb454b`）
- 首次交付（历史，不改写）：`temp@8e124f4`（42文件、120项测试）
- 主仓库接口基线：`13513208952/ReBrowser@6a5b2e783e9543a513171a22f21c7df69053797a`（R47），验证时本地 main 与远端 main 均等于该提交，无更新差异。
- 本报告提交：新增报告与证据文件，**未修改任何预览源码或测试**（未发现需修复的可复现缺陷）。
- 环境：JDK 21.0.10（`/home/zhao/.local/opt/android-studio/jbr`），`javac --release 17`；Android 37.2 `android.jar`（SHA-256 前缀 `1db2419dbd45457f`）；GeckoView 158 `classes.jar`（`f42db6ed0c09a2aa`，与主仓库固定 SHA 一致）；JUnit 4.13.2；Hamcrest 1.3。全程离线，无设备、无网络。

## 1. 实际命令与结果（P0.1）

```sh
cd preview/rebrowser-d02
ANDROID_JAR=... GECKO_JAR=... JUNIT=... HAMCREST=... ./build.sh
# 退出码 0
```

- 实际重新编译 **36 个新 Java + 9 个只读 context**（45 个源文件）与 **36 个测试类**。
- 真实运行结果：**36 classes / 124 tests / 0 failures / 0 ignored**（`OK (124 tests)`）。
  原始交付的“120 tests passed”仅作为历史记录，未沿用。
- 逐类结果见下表；原始 JSON 见 `D02_validation_5fb454b-evidence/per-class-results.json`，完整日志见 `build-run.log`。

| 测试类 | 用例数 | 失败 |
| --- | ---: | ---: |
| io.github.rebrowser.bridge.BridgeBudgetTest | 3 | 0 |
| io.github.rebrowser.bridge.BridgeMessagesTest | 9 | 0 |
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
| io.github.rebrowser.music.MusicLyricsTest | 5 | 0 |
| io.github.rebrowser.music.MusicMediaSessionTest | 3 | 0 |
| io.github.rebrowser.music.MusicNotificationTest | 4 | 0 |
| io.github.rebrowser.music.MusicPreferencesTest | 3 | 0 |
| io.github.rebrowser.music.MusicQueueTest | 3 | 0 |
| io.github.rebrowser.music.MusicStateTest | 4 | 0 |
| io.github.rebrowser.outlook.MailObjectFormatTest | 5 | 0 |
| io.github.rebrowser.outlook.MailRetentionPolicyTest | 5 | 0 |
| io.github.rebrowser.outlook.MailSearchTest | 5 | 0 |
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

## 2. 依赖接口一致性（P0.2）

`context/java/CONTEXT.json` 的 9 份依赖逐一与 `ReBrowser@6a5b2e7` 对比：**9/9 哈希逐字一致**，因此脱机 HOST 编译通过代表当前 R47 接口兼容；主仓库没有比 R47 更新的提交。

## 3. P0 逐项结论

| 项 | 结论 | 证据 |
| --- | --- | --- |
| P0.1 重编译与全量测试 | **PASS** | 退出码 0；124/124 |
| P0.2 依赖与 R47 一致性 | **PASS** | 9/9 SHA 相同 |
| P0.3 `MailRetentionPolicy` 的 null 引用/未完成工作 | **PASS** | `dependents`/`pendingWork` 任一为 null → `UNKNOWN_KEEP`；`UNIQUE_ORIGINAL` 在全部 27 种观察组合下恒为 `KEEP`（独立探针 27 例） |
| P0.4 `BridgeMessages` 严格性 | **PASS** | 孤立代理项（高/低/末尾）、坏 UTF-8/CESU-8、`\u` 非 ASCII 十六进制、重复键、未知字段、前导零、`1e999`、小数/负数/超安全整数序列、过深数组、原始控制符、超限信封均拒绝；合法代理对保留；HELLO/READY 仍 `isVerified()==false`；信用窗口上限与一次性释放正确 |
| P0.5 `ForegroundPolicy` 与 `POST_NOTIFICATIONS` | **PASS**（仅建议矩阵） | DENIED/UNKNOWN 不再否决媒体前台服务；`PAUSED/STOPPED` → `UNKNOWN/BACKGROUND_START_NOT_PROVEN`；`NOT_STARTED` → `SERVICE_NOT_STARTED`；SDK 29 → `BLOCKED`；短任务前台条件仍生效 |
| P0.6 歌词 `DragModel` 双指取消 | **PASS**（模型层） | 多指 `move` → `cancel()`；`cancel()` 幂等；取消后 `end()` 抛 `IllegalStateException`（UI 已改为不调用）；`ACTION_POINTER_DOWN/UP`、`ACTION_UP/CANCEL` 分支静态核对无双重 end |

## 4. P1 Android 运行项

**全部 BLOCKED**：本环境无 adb 设备/模拟器（`adb` 不可用），且本交付不含 AndroidManifest/Gradle/APK，按任务约束不得伪造设备通过记录。`android.jar` 在 HOST JVM 上为桩：`android.os.Looper.getMainLooper()` 实测抛 `RuntimeException: Stub!`（见 `AndroidStubProbe.java` 输出）。

| 项 | 结论 | 说明 |
| --- | --- | --- |
| P1.1 六个 Activity 的 `onCreate` 中创建视图 | **PASS（静态）/BLOCKED（运行）** | 已确认字段初始化中不再存在 `new X(this)`；所有 Activity 均在 `onCreate` 内创建视图。实际创建/旋转/销毁/旧回调未运行 |
| P1.2 下载页点击不得批准 | **PASS（静态）/BLOCKED（运行）** | 列表点击监听已移除；批准仅由独立按钮触发，调用前即 `clearApproval()` 作废本次展示，一页一会话；未知结果提示不自动重试。真实点击/旋转/重放未运行 |
| P1.3 离线邮件迟到正文必须拒绝 | **PASS（静态）/BLOCKED（运行）** | 旧两参重载已删除；`showBodyText(expectedBinding,expectedPage,exactRowToken,...)` 对绑定/页/行/动作身份逐一比对并重新 `binding.check()`；`showUnavailable`/`onDestroy` 清空监听、文本与回收行视图。实际迟到回调未运行 |
| P1.4 歌词 View 拖动/取消/源锁定/detach | **BLOCKED（运行）** | 仅 `DragModel` 模型层 PASS；真实 MotionEvent/WindowManager 未执行 |
| P1.5 新标签背景释放与重绑保留 | **PASS（静态）/BLOCKED（运行）** | 重绑走 `clearBinding(false)` 保留已提供背景；`unbind()`/detach 释放 Drawable。内存生命周期与真实重绑未测量 |

## 5. P2 结论

| 项 | 结论 | 说明 |
| --- | --- | --- |
| P2.1 阅读器 CSP/无远程/纯文本 | **PASS（静态）/BLOCKED（渲染）** | 17 项静态断言全部通过（固定 CSP、无内联脚本/事件、无远程 URL、无表单/iframe/object、无 `url()`/`@import`、无 HTML 注入/网络/消息通道；三份音乐 CSS 作用域正确且不隐藏登录/二维码/错误层）。本地 Gecko origin+CSP 实际执行未运行 |
| P2.2 Gecko 历史/媒体 delegates | **PASS（API 编译）/BLOCKED（真实回调）** | 仅 SDK 158 签名编译与只读 DTO 测试；真实 runtime 线程与旧 session 过滤未运行 |
| P2.3 边界/异常/变形输入回归 | **PASS** | 独立探针 13,641 项断言：500 组随机 124B 头全部拒绝；0/1/32767/32768/32769/65536/1e6 packed 往返与存储长度一致；u32 边界帧数；随机 40k 字符文本 60 轮搜索偏移/片段/顺序/取消一致；全部通过，未发现可复现缺陷 |

## 6. 缺陷与源码改动

- **未发现可复现的源码缺陷**：本轮**没有修改** `preview/rebrowser-d02/` 下任何源码或测试。
- 仅记录两处非阻断观察（未修改）：
  1. `OutlookActivity` 的 `private int dp(...)` 缩进异常（纯格式）。
  2. `MusicArtwork.decode/plan` 对解码尺寸 >4096 像素的图片抛 `IllegalArgumentException` 而非返回 `null`；调用方必须捕获，属于既定“显式拒绝”风格，非本轮缺陷。
- 评审方上一轮修复的 4 处逻辑（UTF-16/ASCII 十六进制、空引用保留、通知权限不否决前台、双指取消幂等）均已被本轮独立探针与仓库测试双重覆盖。

## 7. 未验收项（保留）

- 真实 Android Activity/View/Notification/MotionEvent/WindowManager 行为与旋转、销毁、重放。
- 真实 Gecko 会话、delegate 回调、CSP 强制、官网样式回归、音频焦点/传感器停止。
- 任何真实账户、密钥、数据库、文件、网络、安装或删除操作。
- 主仓库全工程编译与整体回归由主 Agent 合入后执行；本目录 HOST 通过不代表产品通过。

## 8. 证据文件

- `D02_validation_5fb454b-evidence/build-run.log`：完整编译+测试输出（退出码 0）
- `D02_validation_5fb454b-evidence/per-class-results.json`：36 类逐项结果
- `D02_validation_5fb454b-evidence/D02Probes.java`：独立 P0/P2 探针（13,641 断言）
- `D02_validation_5fb454b-evidence/PerClassRunner.java`：真实逐类 JUnit 结果收集器
- `D02_validation_5fb454b-evidence/AndroidStubProbe.java`：HOST 上 `android.jar` 为桩的证据
- `D02_validation_5fb454b-evidence/asset-static-review.json`：17 项资产静态断言
