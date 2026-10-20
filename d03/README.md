# D03 · 25 个既有 Java 文件扩展

在 D02 复核基线上扩展 L49–L73（不新增 runtime 文件）。`app/` 为 40 Java + 6 运行资产；`context/` 为 6 个只读依赖（HistoryAdapter/DownloadsAdapter/SettingsSections 已因扩展移入 `app/`）。

- 验证：`./build.sh` → 43 classes / 180 tests / 0 failures；独立探针 13,641 + 3,635 断言通过。
- 证据：`evidence/d03-review-verification.json`、`d03-review-per-class-results.json`、`d03-review-build-r49.log`；原始 170 测试记录保留在 `evidence/d03-result-evidence.json`（未改写）。
- 复核报告：`review-evidence/DSH-VERIFICATION-R49.md`。
- 报告：`evidence/D03-REPORT.md`。
- Android/Gecko 真机、网页渲染及主工程构建未运行；未改 Manifest/Gradle/主仓库。

复现：

```sh
ANDROID_JAR=… GECKO_JAR=… JUNIT=… HAMCREST=… ./build.sh
```
