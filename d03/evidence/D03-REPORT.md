# D03 · 25 个既有 Java 文件的实质扩展（dsh 结果）

## 基线

- 代码基线：`temp@1d3b602`（经两轮审查的 D02 follow-up）；主仓库接口基线 `ReBrowser@6a5b2e7`（R47）。
- 任务契约：`planning/geckoview-v2/records/low-cost-agent-assignments.json` → L49–L73（25 个既有文件，无新增 runtime 文件）。
- 本轮 `app/` 运行源：**40 Java + 6 资产**；`context/` 只读依赖缩至 **6** 个（`HistoryAdapter`/`DownloadsAdapter`/`SettingsSections` 因本轮扩展已从 context 移入 app）。

## 25 项扩展与实际落地

| 任务 | 文件 | 实质改动 |
| --- | --- | --- |
| L49 | ui/HistoryActivity | 本页 title/displayUrl 字面筛选（不改 token/revision）、清除筛选、前后页可用性与“一页一次”翻页请求、未知结果只锁旧 frame |
| L50 | ui/HistoryAdapter | 每行原生 offered 动作集、保留/删除一页一次门闩（attempt 前消耗，UNKNOWN 不回滚）、busy/不可用原因接入按钮与 holder 清理、open 重复策略独立 |
| L51 | ui/DownloadsActivity | 全部/仍占用/已发布(原生提供)/待批准 四种筛选（依据 TaskSnapshot/offered，互不混淆）、原行与 revision 保留、分页可用性与一次请求 |
| L52 | ui/DownloadsAdapter | 批准/取消/交接地一页一次门闩、按钮可用性依据 snapshot/offered、未知总长真实 indeterminate 进度、100% 不代表完成 |
| L53 | ui/BookmarkView | 有界树（最大节点/深度/环/重复 token/未知父级拒绝）、文件夹路径导航、标题字面检索、离开路径清理私有 view/监听 |
| L54 | ui/SettingsActivity | 只读/布尔/固定选项/有界文本四类控件、per-row 稳定 ID + 原 revision 的编辑缓冲、整数批提交、attempt 后不可本地“取消成功” |
| L55 | ui/SettingsSections | Row 增加 ValueKind/固定选项/文本上限；搜索四 provider 明确选项；不支持值不选默认；不是存储 schema |
| L56 | ui/ApplicationsActivity | EntryState（状态+offered 动作+不可用原因）、打开/启停/设置分别按 offered 启用、启停一次请求等待原生刷新、SubapplicationRegistry 只读 |
| L57 | ui/SubapplicationSettingsActivity | Draft 多字段编辑合并为单份原子 Batch、脏/取消只撤销未提交、未知保存不显示为取消或已保存 |
| L58 | ui/SitePermissionsActivity | 按 partition+host 稳定分组（不跨 scope 合并）、能力排序、撤回请求精确 token+revision、attempt 后标记待重读、重绑清 pending |
| L59 | ui/NewTabView | 真实有界搜索输入 + 四固定 provider、显式提交才发 provider+原始关键词、空白/超限/坏 UTF-16 本地拒绝、键盘与按钮共享 in-flight 防双发、快捷方式 offered 管理 |
| L60 | music/MusicActivity | 队列 token+revision 的播放/移除/喜欢意图、原/译歌词与严格 [start,end) 逐字高亮、seek 仅用 offered 与手势起点 duration/generation |
| L61 | music/MusicQueue | 有界 window/filter（保留全队列 revision 与原 token 意图）、UNREAD/EMPTY/AVAILABLE/截断区分、不构造 current playing |
| L62 | music/MusicState | `estimatePosition(generation, now)` 纯展示估计：仅 PLAYING+已知观测时间，回退时钟/跨代/超时→stale，clamp 时长；reported 值仍独立 |
| L63 | music/MusicPreferences | Draft 多轴合并为一份 Change、冲突/未知/无变化显式、expectedRevision 不改写 |
| L64 | music/MusicLyrics | 精确时间翻译对齐（重复/歧义报告，不近似）、行查找二分且稳定 tie、activeWordAt 严格区间/gap 返回 -1、保留 latest-start 语义 |
| L65 | music/MusicArtwork | OwnedBitmap（AutoCloseable）+ 单实例单槽：真实 recycle 后才释放额度，异常/未知保持 busy，过代/无结果先清理 |
| L66 | music/MusicNotification | Snapshot 绑定单帧 title/subtitle/动作顺序、一次性 take、empty 清空旧私有文案、重复/超量动作拒绝、仍不构造 PendingIntent |
| L67 | music/MusicMediaSession | offered 动作与 reported 位置/时长/速度、展示估计分离；未知 duration 去掉 SEEK；UNKNOWN 不自动 PLAY/PAUSE；empty 投影 |
| L68 | lyrics/LyricOverlayController | Gesture token + 模式/隐藏/retire 失效旧 drag；仅当前 mode+generation 的应用观测可确认；失败不推断移除/穿透；generation 耗尽拒绝 |
| L69 | lyrics/LyricOverlayWindow | 首 pointerId 捕获、按 index 查询、第二指/POINTER_UP/CANCEL/unbind/模式变化结束或取消、px↔dp 与 bounds 重绑 |
| L70 | lyrics/LyricPositionStore | 相对锚点恢复（旋转/小窗/面板大于屏幕 clamp、防溢出比例）、exact 恢复保留、无路径/来源身份 |
| L71 | outlook/OutlookActivity | 账号观测/官方入口/离线 offered 分离；精确账号 token 一次重读；新页不沿用旧 summary/可用状态；仅 OfficialMailEntry 固定枚举 |
| L72 | outlook/OfflineMailActivity | Query(page#/literal)+PageNav、search/next/prev 绑定 frame+query+revision、busy/unknown/empty 分离、迟到正文必须匹配 frame/query/page/row |
| L73 | outlook/MailSearch | 流式 KMP 线性匹配、非重叠语义、UTF-16 增量校验、片段不拆代理对、8Mi 扫描/200 结果、取消在读前后与 close 后、Reader 入口转移/全出口 close、零进度上限 |

共 **25/25** 个文件相对基线发生实质变化（见 `evidence/d03-result-evidence.json`）。

## 实际验证（HOST）

- `./build.sh`：真实重编译 40 个 app Java + 6 个 context 源，编译 43 个测试类，**170 tests / 0 failures**（`evidence/d03-per-class-results.json`、`d03-build-run.log`）。
- 独立探针（复用 D02 证据工程）：**13,641 + 3,635** 项断言全部通过（含 MailSearch 跨块随机/1 字符变形、歌词精度/排序、JSON 数字）。
- 编译环境：JDK 21、`--release 17`、Android 37.2 `android.jar`、GeckoView 158 `classes.jar`、JUnit 4.13.2；离线。

## 本轮发现并修复的实现缺陷

1. `SettingsActivity.Change` 对 FIXED_OPTION 错误地拒绝 `stringValue`，且未校验选项存在 → 修正校验（不支持的选项显式抛错）。
2. `MusicArtwork.OwnedBitmap` 最初以 `boolean close()` 实现 `AutoCloseable`，无法编译 → 改为 `void close()` + `boolean release()`，未知 recycle 仍保持 busy。
3. `LyricOverlayController` 原先把每次坐标变化都当作 generation 变化并失效 gesture，导致拖拽首移即断 → 仅模式切换/新 gesture 递增 generation；旧 token 仍不可跨模式使用。
4. 独立探针随机语料生成器把 emoji 拆成孤立代理项（探针缺陷，非产品缺陷）→ 探针改为按完整码元/代理对追加。

## 未验证（BLOCKED，未伪造）

- Android 真机/模拟器：Activity/View/Notification/MotionEvent/WindowManager/PendingIntent、旋转/销毁/重放。
- Gecko 真实 session/delegate 回调、网页渲染与 CSP 强制、官网样式回归。
- 主仓库 AGP 整体构建与全量回归（由主 Agent 合入后执行）。
- 未使用真实账户、密钥、私有邮件、文件、网络、安装或删除操作；未改 Manifest/Gradle/依赖/sourceSet。

## 复现

```sh
cd dsh/result
ANDROID_JAR=… GECKO_JAR=… JUNIT=… HAMCREST=… ./build.sh
```
