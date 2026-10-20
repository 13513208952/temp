# ReBrowser v2 · D02 单轮42文件交付

## 1. 范围与结论

本提交实现任务记录中的 **L07–L48 共42个新目标**，不包含也不重新计算此前 L01–L06 的6个交付文件：

- **36 个 Java 运行源文件** + **36 个对应测试类**（120 项 JUnit HOST 测试）
- **6 个运行资产**：离线邮件 `reader.html/css/js`，音乐样式 `music-phone.css`、`music-window.css`、`music-desktop.css`

其中 **14 个整项低副作用任务**（静态目录、纯解析/格式化、只读模型、无副作用几何）与 **28 个限定低风险切片**。
切片任务只实现了被明确允许的低副作用部分；完整文件的高风险责任（真实持久化、授权、密钥、传感器、安装、官方站点行为）**未实现，仍归集成方**，本提交不声称完成。

## 2. 目录

```
app/src/main/java/io/github/rebrowser/   36个本提交实现的源文件（路径与目标树一致）
app/src/test/java/io/github/rebrowser/   36个测试类
app/src/main/assets/                     6个运行资产
context/                                 9个"既有"生产文件的逐字只读副本（见 context/CONTEXT.json）
evidence/delivery-evidence.json          每个目标的路径/SHA-256/测试映射与真实执行标志
evidence/asset-checks.json               资产静态检查结果
build.sh                                 本地复现编译+测试的脚本
```

`context/` 中文件**不是本提交的成果**，仅为了让新增源码能够编译与审查；每一份都在 `CONTEXT.json` 中记录了来源路径与 SHA-256。

## 3. 已实际执行

在本机离线执行（JDK 21，`javac --release 17`）：

```
android-37.2/android.jar + geckoview-158.0.20261007201254/classes.jar + junit-4.13.2 + hamcrest-1.3
javac -proc:none -d classes  (45 sources = 36 new + 9 context)
javac -proc:none -d test-classes (36 test classes)
java org.junit.runner.JUnitCore <36 classes>
→ OK (120 tests), 0 failures
```

另执行资产静态断言：CSP 固定为 `default-src 'none'; script-src 'self'`，HTML 无内联脚本/事件属性/远程 URL/表单/iframe；`reader.css` 与三份音乐 CSS 无 `@import`/`url()`/远程字体；`reader.js` 无 `innerHTML`/`document.write`/`eval`/网络/`postMessage`；音乐 CSS 未隐藏登录、二维码或错误层。

## 4. 明确未执行（不得据此认为产品可用）

- 未运行任何 **Android 设备/模拟器生命周期、Activity/View/Notification 实例化或渲染**。
- 未执行 **GeckoView 真实 session、MediaSession/HistoryDelegate 回调或 JNI**；`GeckoMediaBridge` / `GeckoHistoryDelegate` 只做了 SDK 158 签名 API 编译与只读模型测试。
- 未执行真实 **WebView/Gecko 页面渲染、CSP 强制或官网 CSS 回归**；`music-phone.css` 从仓库内两份批准参考 CSS 机械迁移（哈希记录在文件头），`music-window.css`/`music-desktop.css` **没有独立批准参考**，只是同一批 selector 的限定派生，需官网实测。
- 未读取/写入任何真实账户、Cookie、数据库、偏好、密钥、剪贴板、APK 或用户文件；未连接网络。
- 未新增 AndroidManifest 组件声明、未接入 Gradle、未生成 APK。

## 5. 安全边界（实现上刻意保留）

- 所有 UI 只回传**精确行 token + 页 revision + 用户意图**；`Binding` 不是管理员授权，展示 DTO 不是领域凭据。
- 不做“100% 即完成”“Java 回调返回即工作结束”“系统计时结束即传感器停止”“任务结束即文件已发布”等推断；`TaskSnapshot` 各轴在下载列表中分别展示。
- 取消/交接/安装/权限/背景/浮层流程只产生**请求**，不自行执行，也不伪造成功。
- 未知状态一律显式 `UNKNOWN`（未知大小、未知时长、未知账号、未知授权、未知启用、未知期限），不会被默认成 0/false/已启用。
- 不提供 `mayPlay()`、`isAuthorized()`、`ownerGrant()` 等正向授权 API；`KeyPurpose` 只是词汇，不生成/派生/销毁密钥。
- `BridgeMessages` 是严格有界解析器（重复键/未知字段/非法 UTF-8/BOM/非整数安全整数/深度/超限全部拒绝），解析结果显式 `isVerified()==false`，不铸造 `DocumentLease` 或 READY 权限。

## 6. 集成注意事项

1. 42 个目标路径按主工程树写入；本次**未**修改主工程、清单、Gradle、既有 6 个交付文件或公共协议。
2. 若集成方已有的 `BrowserToolbar`/`DownloadsAdapter` 签名与本提交 `context/` 快照不同，以集成方当前版本为准并重新编译；本提交的 UI 只依赖 `Binding`、`IntentOutcome`、`invokeNativeIntent` 三个公开面。
3. 切片文件中带有“待原生所有者”注释的路径（保存、授权、会话、发布、安装、系统窗口）需要在领域层补齐后才能并入产品路径。
4. 建议审查顺序：先看 `evidence/delivery-evidence.json` 的标志位，再看各文件头部注释中的“NOT/不得”边界，最后看测试是否真的只断言了允许范围。

## 7. 复现

```sh
ANDROID_JAR=/path/to/android-37.2/android.jar \
GECKO_JAR=/path/to/geckoview-158/classes.jar \
JUNIT=/path/to/junit-4.13.2.jar \
HAMCREST=/path/to/hamcrest-core-1.3.jar \
./build.sh
```
