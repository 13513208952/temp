# D03 审查修版 · dsh 复核执行报告（R49 接口）

## 基线与实际环境

- 被复核交付：`temp@6a4b2d2`（含 `0563779`、`7e0dc92`、`732f20e` 三批审查修复及本说明）；原始 dsh 交付为 `e47f892`。
- 主仓库参照：`ReBrowser@4f42f4c`（R49）。D03 `context/` 已从 R47 `6a5b2e7` 同步到 R49；两份差异均为**纯新增**（`BrowserToolbar.clearOwnedViewTree/boundedStatusReason`、`ComponentGate.DataEpochPin/captureDataEpoch`），D03 使用的接口未破坏。
- 环境：JDK 21、`javac --release 17`、Android 37.2 `android.jar`、GeckoView 158 `classes.jar`、JUnit 4.13.2；离线、无设备、无网络。

## 实际执行（两条命令，退出码 0）

```sh
cd d03 && ./build.sh   # 先以 R47 context，再同步 R49 context 各跑一次
```

- 编译 40 个 app Java + 6 个 context 源 + 43 个测试类。
- **43 classes / 180 tests / 0 failures / 0 ignored**（`evidence/d03-review-per-class-results.json`、`evidence/d03-review-build-r49.log`）。
  180 = 原 170 + 审查新增 10 个回归用例；**原始 170 的记录保留在 `evidence/d03-result-evidence.json`，未改写**。

## 独立探针（HOST）

| 探针 | 断言数 | 结果 | 覆盖 |
| --- | ---: | --- | --- |
| D02 回归 | 13,641 | PASS | JSON/歌词/保留策略/搜索/信用等 |
| D02 follow-up | 3,635 | PASS | 跨块搜索变形、歌词精度/排序、Unicode 数字 |
| D03 定向（新增） | 46 | PASS | 扫描预算/Reader 归属/片段边界、权限 offered、书签上限、媒体 offered、歌词严格性、采样上界 |

定向探针验证的审查修复：

1. **MailSearch**：8 Mi 扫描预算不会多读（`delivered <= MAX_SCANNED_CHARS`），`SCAN_LIMIT` 与扫描数一致；非法字面量/结果上限时 Reader 仍被关闭；`read()` 返回 -2 不再当 EOF；片段 ≤120 且不拆代理对；孤立高代理项拒绝。
2. **SitePermissions**：默认 Row 无任何 offered；旧 boolean 构造器只映射 REVOKE；显式集合原样保留；null 集合拒绝。
3. **BookmarkView**：书签不能作为父容器；单文件夹 >100 子项在构造期拒绝；搜索结果 >100 拒绝。
4. **MusicMediaSession**：旧重载只投影显式提供的 SKIP；PLAYING/时长不再推导 PAUSE/SEEK；显式集合保留；未知时长移除 SEEK。
5. **MusicLyrics**：非空不可解析输入为 `UNSUPPORTED`；空输入仍 `EMPTY`；TSV 必须严格三字段。
6. **MusicArtwork**：非整除采样（3000×2000、4096×257、4096×4096 等）采样后像素不超预算，采样为 2 的幂。

## 观察与提醒

- `BookmarkView.Tree` 以**对象身份**匹配 opaque token；调用方必须复用同一 token 对象（字符串同值不同实例会被视为未知父级）。这是既定身份语义，非缺陷；定向探针已按此构造。
- 审查者声明的“Android 行为未执行”经确认：`android.jar` 在 HOST 为桩，Activity/View/MotionEvent/MediaSession/Notification 的真实运行仍然 **BLOCKED**。
- 未改 Manifest、Gradle、依赖、sourceSet 或主仓库；未使用真实账户、密钥、文件、网络或安装/删除。

## 结论

- 审查修复版在 R49 context 下可编译且 180/180 测试、全部探针通过；审查者指出的 6 类边界均有独立断言覆盖。
- HOST 通过不等于 Android/Gecko 验收；合入与真机/网页验证仍由主 Agent 执行。
