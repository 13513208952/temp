# D02 · 接续测试与限定修复任务

## 目的和边界

此次应当 **验证、复现、修复实际缺陷**，不是制造更多抽象和文档，也不是重新实现高风险业务。上一轮审查者已经直接修改指定候选源码；你应当使用 **temp 最新 main 提交** 作为待测交付，不使用旧 D02 SHA 覆盖修复版。

主仓库 R47 当前继续由主 Agent 负责；不要修改 `13513208952/ReBrowser` 的 main，不读取真实账户/钥匙/私有邮件，不运行真实删除/安装/登录/权限提升。所有需要真实 Android/Gecko 行为的工作，应使用 **已获机主批准的合成测试环境**；没有设备/工具链就报 blocked，不造通过记录。

## 优先级 P0 — 自动化回归与编译

1. 逐文件读取候选补丁，使用 `preview/rebrowser-d02/build.sh` 所列离线 JDK 21、`javac --release 17`、Android 37.2 `android.jar`、GeckoView 158 固定 `classes.jar` 和 JUnit 4.13.2，实际重新编译 **36 新 Java + 9 只读 context** 和 36 测试类。运行全部测试并记录真实数量/失败名称/堆栈。不要把原始交付的“120 tests passed”沿用为修复后的结果。
2. 对比 `context/java/CONTEXT.json` 列出的九份依赖与 ReBrowser R47：逐字/哈希一致才将离线 HOST 通过标记为当前接口兼容；如主仓库后来有新提交，另说明最新接口差异。
3. 确认修复后的 `MailRetentionPolicy` 对 dependents、pending work 为 `null` 的情形永不返回可回收候选；原始唯一内容永不自动回收。这里的 candidate **始终不等于真实文件删除许可**。
4. 确认 `BridgeMessages.parse(String)` 拒绝孤立 UTF-16 代理项，`parse(byte[])` 拒绝坏 UTF-8，`\\uXXXX` 的十六进制只接受 ASCII；检查超限、重复键、过深层、流信用及已解析 DTO 仍 UNVERIFIED。
5. 确认 `ForegroundPolicy` 不会因 Android 13+ `POST_NOTIFICATIONS` 未授权而一票否决媒体前台服务；但对真实后台启动限制、Android 14+ service type、运行时 OS 拒绝 **不要假定已通过**。参照 Android 官方的通知权限与前台服务文档。
6. 确认歌词 DragModel 的双指取消、`ACTION_POINTER_DOWN/UP`、随后 `ACTION_UP/CANCEL` 不再触发双重 end；仍需 Android MotionEvent 真机/模拟器链路验证。

## 优先级 P1 — 需要真正 Android 运行的修复检查（不可用则明确 blocked）

1. 启动 `ApplicationsActivity`、`SettingsActivity`、`SitePermissionsActivity`、`SubapplicationSettingsActivity`、`OutlookActivity`、`MusicActivity`；确保 `new LinearLayout(this)` 在 `onCreate` 中而非 Context attach 前执行。模拟 Activity 创建/旋转/销毁、旧窗口回调、来源锁定、退役/重绑。仅 HOST javac 不能证明这一点。
2. 下载页面：列表任意点击**不应批准文件**；只有明确的独立批准按钮可提交，且同一请求仅允许一次；切换新页、取消绑定、未知结果、窗口销毁后旧批准事件不可重放。执行回调时原生所有者仍需重新认证与验证 token/revision，UI 不负责授予能力。
3. 离线邮件：`showUnavailable`、窗口销毁、切换账号/页面之后的**迟到正文**必须拒绝；只能使用新的 `showBodyText(expectedBinding,expectedPage,exactRowToken,subject,plainText)`，不保留旧无绑定重载。旧列表可见文本/回收视图/搜索监听应清理；不能因为 DTO 无数据库操作就忽视屏幕隐私。
4. 歌词 View：分别验证单指拖动、多指切换、取消、源锁定和 View detach，确认无异常且没有未经原生所有者批准的实际系统浮窗动作。单元测试只验证 DragModel，并非实际 MotionEvent/WindowManager 测试。
5. 新标签：解绑/Detach 时释放旧 Drawable；同一窗口重新绑定时不要悄悄丢弃仍获许可的已提供背景。评估大背景图内存的实际生命周期；不要主动读取用户图片。

## 优先级 P2 — 现有产品计划的兼容性与未完成边界

1. 静态审查 `reader.html/css/js` 的 CSP、无远程请求/HTML 插入、正文仅纯文本；在可隔离环境实测时，确认本地 Gecko origin + CSP 真正执行，**绝不加载真实邮箱信息**。
2. Gecko 历史与媒体 delegates 的 HOST API 编译不能替代真实 SDK 回调；用隔离真实 runtime 验证线程和旧 session 的回调筛选，不得把“观察到播放/历史”当官方登录、动作完成、持久化或授权证明。
3. 对 `MailObjectFormat`、`MusicLyrics`、`MusicQueue`、`MailSearch` 做边界/异常/变形输入回归（内存、上限、Unicode、流停止、帧长度）。只有发现可重现的缺陷才修改。不要为提高代码行数添加无意义测试。

## 提交原则

- 在 temp 独立工作区完成检查；必要修改限定于 D02 对应源码和测试，禁止为“绿灯”篡改原始 evidence 或放宽断言。
- 有真实缺陷：写失败复现，提供最小修正，重新运行受影响与全套测试；没有缺陷就保持源码不变。
- 若需要改变源闸门、密码学、管理员权限、身份、邮箱/音乐正式业务、AndroidManifest、主项目 Gradle 或 Gecko 宿主公共接口，**不要由低成本 Agent 擅自扩展**；报告给主 Agent。
- 保留清楚的未验收项；不给“总模块已完成”“真机验证通过”等无依据结论。

## 交付报告

在 `mailbox/reports/D02_validation_<日期或commit短SHA>.md` 记录：base/head SHA、JDK/SDK/Gecko/JUnit 版本、实际命令和退出码、36 个测试类各自结果与合计、逐项 P0/P1/P2 的 `PASS/FAIL/BLOCKED/NOT_RUN`、发现并修复的缺陷、修复后的第二次实测、所有未运行原因。

当合入主项目时，由主 Agent **重新编译全工程并运行整体回归**；本目录的独立 HOST 通过不能当成主仓库产品通过。
