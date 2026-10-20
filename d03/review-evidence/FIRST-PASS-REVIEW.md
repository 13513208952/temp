# D03 第一轮独立审查与修复 · 交给主 Agent 的合入说明

日期：2026-10-20；目标：`13513208952/temp/d03/`。只修改临时仓库，不修改正式 `ReBrowser`。

## 证据边界与基线

- dsh 原提交 `e47f89284df7a0743d7ab674a3acebc93f48f157`：L49–L73 的 **25 个既有 Java 文件扩展**；40 个 app Java、6 个静态资产、6 个 context Java。其原始日志声明 43 个测试类 **170 tests PASS**，另有 13,641+3,635 探针断言。
- 以上测试是 **dsh 原始版本的记录**，并非本审查者独立复跑。修复后源码 SHA 改变，原始 D03 evidence SHA **不再代表修订版源码**；请勿把原始报告误用于替代修复版验收。
- 目前不能在本会话所用运行环境复现 Android 37.2 / GeckoView 158 / JUnit 离线依赖的全套编译、JUnit、真机和网页运行。新增回归用例 **未执行**；后续必须由主 Agent 对正式 app 源执行 AGP 和全部测试。
- D03 源码来源主项目 **R47 `6a5b2e7`**，而主项目已推进至 **R49 `4f42f4c`**。**必须逐文件三方合并、人工复审，禁止整目录覆盖 R48/R49**。

## 本审查者实际修复记录

### `0563779941583c759b966519d577ed8b98297376`

1. `outlook/MailSearch.java`：实际 Reader 读取限制为剩余扫描预算，不能在 8 MiChar 边界外多读、继续匹配或返回伪造的受限扫描数；异常 Reader count <-1 不再算成 EOF；结果限制时计入最后被消费的字符；片段严格 <=120 字符且不截断代理对；边界上孤立 high surrogate 拒绝。
2. Reader 从进入 `search` 起转移所有权：**包括参数非法时也会关闭**；close 之后再次检查 cancellation，避免 close 中途收到取消后发布旧结果。
3. `music/MusicArtwork.java`：修正非整除采样后的像素上界计算，校验 BitmapFactory **实际**解码结果像素，而不只相信估算；native recycle 抛异常/状态未知时不错误释放单槽额度；避免依赖 `Math::max`。
4. `music/MusicLyrics.java`：避免在 SDK 限制的编译路径中重新引入主 Agent R48 已修过的排序 Lambda；非空但无法识别的输入为 `UNSUPPORTED`；严格三字段 TSV、词时间行长度。
5. 这批新增 `MailSearchTest`、`MusicArtworkTest`、`MusicLyricsTest` 共 6 个回归测试。

### `7e0dc929f93d8c438656cd208ca0dfe0541ced0b`

6. `lyrics/LyricOverlayWindow.java`：FrameLayout 直接添加多个 TextView 会覆盖；现在归入纵向布局。仅 VISIBLE_UNLOCKED 接受拖动；使用 **raw** 触摸位置，验证 density/坐标，重新绑定几何时清旧手势，实际应用坐标按当前模型边界约束。
7. `music/MusicMediaSession.java`：兼容旧重载保留，但**不能仅由 PLAYING/PAUSED/时长推导 PLAY/PAUSE/SEEK 的“已提供操作”**；显式 `Set<Action>` 重载才可传递对应操作。
8. `music/MusicActivity.java`：新增接收原生已提供操作集的 `bind` 重载；旧重载保守禁用未经报告的播放/seek 控件，seek 从手势开始至结束检查同一绑定帧和 generation；队列行的旧渲染帧不能重新定向到新队列页。
9. 更新音乐 2 个测试类中的期望和增加 1 个回归用例，体现“观察到的事实不等于操作许可”。**注意新重载需由主 Agent 接真实 native owner 提供的 offered 集，不能凭 MusicState 伪造。**

### `732f20e7336f6eb58cff520966101050bfa1d823`

10. `ui/SitePermissionsActivity.java`：原来缺省 `nativeRevokeOffered=true`，且 GRANT/RESET 始终可点击；现在所有权限更改均由精确的原生 offered actions 显式提供，否则关闭按钮。旧 boolean 构造器保留且只映射 REVOKE。
11. `ui/BookmarkView.java`：拒绝普通书签作为父容器；单文件夹超过 100 项、搜索结果超过 100 项时明确拒绝，而不是在 Page State 渲染时才出现隐含失败。
12. 更改和扩展权限分组测试；下面的最新提交补齐书签树的 2 个回归测试。

## 待主 Agent 重点验证与处理

- **全套 AGP 再验证**：Gradle compile、JUnit、lint/Android 行为；dsh 原 170 tests 原样通过记录不能证明修订后通过。修订后预计新增 10 个左右测试（最终以真实 test runner 输出为准，不得沿用估计报数）。
- **兼容合并**：R48/R49 主项目的 `BridgeMessages`、`MailSearch`、`MusicArtwork`、`MusicLyrics`、`MusicMediaSession`、`LyricOverlayWindow`、`OfflineMailActivity` 和 `BrowserToolbar` 已存在更完整约束，不得用 D03 相对 R47 的上下文替代。只摘取 D03 有益新增能力。
- **实际 Android 行为 BLOCKED**：歌词窗口布局/触摸、MediaSession、下载/权限按钮、页面/Activity 销毁后监听和 SystemClock、Gecko 回调均未验证。浏览器与媒体行为需要真实设备/模拟器及原生所有者验证。
- **已识别但暂未扩写架构**：`SitePermissionsActivity.Groups` 按显示 `partitionLabel+hostLabel` 汇总，**不是可信 partition identity**；绝不能把分组字符串用作授权标识，必须由主 Agent 核对真实目标持有与 action token。
- **书签搜索拒绝超 100 条**：当前明确拒绝以免无界渲染，后续 UI 如需分页只能由原生 owner 提供页/继续键，不能把未显示结果误认为不存在。
- **播放器控制策略**：缺乏 native-offered 集时旧 `bind` 行为保守停用控件；不要为维持表面可点击性恢复“由状态推导权限”。
- **保留 D02 CSS 审核边界**：`music-window.css`、`music-desktop.css` 在 R48 被暂缓，不应凭 D03 文件存在就激活。

## 推荐验收步骤

1. 主 Agent 三方 diff：主仓库 R49、D02 已合入 R48 版本、D03 第一轮复修 HEAD。
2. 先将本轮测试迁入可验证的源码位置，复跑全部 HOST/AGP 测试；有失败时修复、保留失败记录和最后 PASS 的真实来源。
3. 再逐块引入 L49–L73 UI/领域增量；拒绝覆盖已经过 R48 修正的系统/来源边界，精确记录源代码哈希和不激活事项。
4. 最终区分 HOST-PASS、API-compiled、Android-runtime-tested 与 APK integration，不虚报阶段。

审查仓库最新提交：`temp/main`；主要改动不等于 D03 已可无条件合入。
