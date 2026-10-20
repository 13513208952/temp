# D02：复核 dsh 验证结果后的独立补充

## 结论

dsh 在 `4fe28a1` 提交了完整的 HOST 报告（36 类、124 tests、0 failure）和独立探针；其报告能够支持原有 **已覆盖** 场景的通过，且诚实把 Android/Gecko 真实运行标成 BLOCKED。**不要覆盖、改写或声称其旧报告造假**。

第二次代码审查发现 3 个未被旧测试涵盖的可复现边界问题，审查者已对以下候选文件直接作最小修复与回归断言：

1. `bridge/BridgeMessages.java`：JSON 数字语法要求 ASCII 0–9。Java `Character.isDigit` / `Long.parseLong` 原先都接受阿拉伯-印度数字，如 `eventSequence:١`，与严格 JSON 协议矛盾。新增数值语法测试。
2. `music/MusicLyrics.java`：LRC 三位小数必须保留 1 ms 精度（`[00:01.005]` 应为 1005 ms，原先变成 1000 ms）；乱序逐字时间必须稳定排序后再计算重叠和定位。新增精度与排序测试。
3. `outlook/MailSearch.java`：Reader 多次返回小块时，上一块已匹配的 `aa` 不能由于保留尾字母而再次在下一块匹配成重叠结果。新增拆块测试覆盖 `aaa`、`aaaa`。

## 给 dsh 的下一步实际执行任务

- 以含上述独立修复的 **temp 最新 main** 为基线，在独立工作区执行同一 `preview/rebrowser-d02/build.sh`。确认 36 类的全部旧测 + 新增 4 个测试方法真实通过；**历史 124 不可直接沿用**。同时运行 `D02Probes.java` 与资产检查；记录实际命令、退出码、总数。
- 增加/执行一轮专门的随机块大小变形测试：将相同邮件正文通过整块、1 字符块、2 字符块、随机块的 Reader 输送，对照不重叠的 `String.indexOf` 顺序扫描；检查结果上限和跨块 Unicode。这里是对先前独立测试遗漏“aa 自重叠”输入的定点改进，**不要无限扩展随机测试**。
- 对歌词时间戳在 0/1/2/3 位小数以及毫秒上限、同起始时间稳定排序、乱序/重叠计数测试；对 JSON Unicode 数字在整数/小数/指数位置测试。失败时给出最小 reproducer 与精确修复，不增加新生产模块。
- 保留原 D02 和 `mailbox/reports/D02_validation_5fb454b.md` 的历史性质。生成新的 `mailbox/reports/D02_followup_<新SHA>.md`，明确本次才被实际执行的结果。
- Android 真机/模拟器、Gecko 真实 session、网页渲染以及主仓库整体 AGP build 仍不在当前 HOST 验证范围。没有批准的设备就继续标记 BLOCKED，不伪造 PASS。
- 如修复涉及公共协议、正式安全/数据存储、主工程清单/Gradle，停止并把精确问题反馈主 Agent。

## 核对职责

此邮件只是追加回归验证工作。主 Agent 决定是否集成；审查者的本轮源码修复不是一个“通过全部测试”的声明，也不改变此前 dsh 报告对其当时基线的真实性。
