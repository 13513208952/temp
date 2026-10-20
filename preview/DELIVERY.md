# DELIVERY

本目录是独立编码代理的**首次候选提交**，按 `preview/README.md` 的约定放在 `preview/` 下；
没有改动 `final/`、`TASKS.md`、根 `README.md`，也没有创建 PR、workflow 或 CI。

内容只对应 TASKS.md 的任务 A（浮动面板位置模型及存储）与任务 B（几何边界回归测试）。
没有第三方运行时依赖，没有 Android / 数据库 / 网络 / UI 代码。

## 文件清单（路径相对 `preview/`）

| 文件 | 内容 |
| --- | --- |
| `src/main/java/floatingpanel/FloatingPanelPosition.java` | 位置模型、坐标换算、布局策略、存储编解码；含嵌套类型 `LayoutArea`、`Placement`、`HorizontalLayout`、`PositionStorage`、`RestoreResult` |
| `src/test/java/floatingpanel/FloatingPanelPositionTest.java` | 任务 A 单测（37 个用例） |
| `src/test/java/floatingpanel/FloatingPanelPositionBoundaryTest.java` | 任务 B 边界回归测试（15 个用例） |
| `pom.xml` | 最小构建：`maven.compiler.release=17`，仅在 test 作用域引入 JUnit 4.13.2 |
| `.gitignore` | 忽略本目录下的 `target/` 与 `*.class` |

## 设计选择

### 归一化比例而非绝对像素

位置只保存 `xFraction` / `yFraction`（闭区间 [0, 1]），默认 `x=0.5, y=0.25`。
像素坐标在每次放置时用调用者传入的安全矩形重新换算，因此窗口尺寸、横竖比例或安全区域偏移变化后
不需要迁移数据。

### 尺寸与溢出安全

- `LayoutArea.of(...)` 把负宽高归一为 0；当 `left+width` 或 `top+height` 超出 `int` 上限时收缩尺寸，
  保证之后所有加法都不溢出（`right()` / `bottom()` 始终可表示）。
- 面板宽高先裁剪到 `[0, 可用尺寸]`，因此实际尺寸永不为负，也不会超出安全区域。
- 比例转像素用 `Math.round`（.5 向上），结果再夹到 `[0, slack]`；`slack = 可用尺寸 - 实际尺寸`。
  `slack == 0` 时偏移直接为 0，不做除法。

### 两种横向策略

- `CENTERED_X`：`left = area.left + (可用宽 - 实际宽) / 2`（整数除法，向下取整），忽略保存的 X，
  Y 仍按比例恢复。
- `FREE_X`：`left = area.left + round(xFraction * slackX)`。
- 这里只是通用策略枚举，不推断设备类型。

### 拖动

`dragTo(area, panelW, panelH, layout, targetLeft, targetTop)` 把调用者给出的期望像素坐标裁剪到
可移动区域，再除以 slack 得到新比例；返回新实例（不可变对象），不修改当前对象，也不触碰存储。

- `CENTERED_X` 下拖动不改变 X。
- 某个轴 `slack <= 0`（面板已填满该方向，或可用尺寸为 0）时，该轴保持原比例。

### 持久化与几何分离

- 几何方法（`place`、`dragTo`）只接受矩形/尺寸/坐标，完全不知道存储的存在。
- `PositionStorage` 只有 `read()` / `write(String)`，由调用者注入，本组件不提供任何实现。
- 编码格式为 `x=<数值>;y=<数值>`，使用 `Double.toString`，与区域设置无关且可无损往返。
- 只有显式调用 `persist(storage)` 才会写入；构造、`place`、`dragTo`、`restore`、`toStorageValue`
  都不会写（`FloatingPanelPositionTest.geometryAndRestoreNeverWriteToStorage` 用计数替身验证）。

### 坏数据的处理

`fromStorageValue` / `restore` 返回 `RestoreResult`，除位置外带一份人类可读的 `issues` 列表：

| 情况 | 行为 |
| --- | --- |
| `null` / 空串 | 两个轴都用默认值，并说明原因 |
| 缺 `x` 或 `y` | 只该轴用默认值，另一轴保留存储中的合法值 |
| 非数字（`abc`）、空值 | 只该轴回退到默认值 |
| `NaN`、`Infinity`、`1e400` | 只该轴回退到默认值 |
| 越界（`-0.4`、`1.4`） | 裁剪到 0 / 1，并说明被裁剪 |
| 未知键、无 `=` 的片段 | 忽略并记录一条说明，已识别的轴不受影响 |

按轴独立处理正是为了满足“不要悄悄清空其他数据”：一个轴坏掉不会连带重置另一个轴。
`of(x, y)` 是静默版本（同样的裁剪/回退规则），需要解释时用 `ofExplained(x, y)`。

## 确实执行过的测试

在本机（OpenJDK 20.0.2，`maven.compiler.release=17`，JUnit 4.13.2）执行，工作目录为本提交所在的 `preview/`：

```
cd preview && mvn -B test
```

结果：

```
Tests run: 15, Failures: 0, Errors: 0, Skipped: 0 -- floatingpanel.FloatingPanelPositionBoundaryTest
Tests run: 37, Failures: 0, Errors: 0, Skipped: 0 -- floatingpanel.FloatingPanelPositionTest
Tests run: 52, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

测试确实运行了（不是“未执行”）。没有使用 GitHub Actions、workflow 或任何远程 CI，全部在本地 JVM 完成。

覆盖点与 TASKS.md 的对应关系：

- 任务 A：默认值、首次无数据、保存/读取往返、编码稳定性；NaN / Infinity / 越界 / 非数字 / 缺失 / 未知键；
  `CENTERED_X` 与 `FREE_X` 的横向差异；窗口尺寸与横竖比例变化、安全区域非零偏移；
  面板大于可用区域、零尺寸、极窄区域、四向边界拖动；无 slack 轴保持比例、`CENTERED_X` 拖动不改 X；
  近 `Integer.MAX_VALUE` 的原点/尺寸、负数尺寸、null 参数；读取与计算不写存储。
- 任务 B：`round` 的 .5 向上行为、边界加法（非零原点）、`0/1` 恰好贴边；
  5000 组随机矩形/面板/比例组合的包含性断言；重复放置与 500 次“放置-拖回-再放置”循环无累计偏移；
  非有限比例仍产生合法放置；`int` 极限尺寸与拖动目标不溢出；零尺寸组合与“刚好填满”不计为裁剪。

## 未完成 / 未涉及

- 不涉及真实使用方：没有接入层、桥接代码、UI、悬浮窗权限、手势、媒体或登录逻辑（按 TASKS.md 明确排除）。
- `PositionStorage` 只有接口，没有默认实现（文件、SharedPreferences、注册表等都不提供）。
- 存储字符串由本组件独占；读取时会忽略未知键，写入时只写 `x`/`y`，不会保留同一条字符串里的其他键值。
  若将来需要与其他配置共用一条记录，应在外层自行做键的隔离。
- 未做多线程同步：不可变对象本身线程安全，但 `persist` 的并发写入语义由调用者的存储实现负责。
- 未改动 `final/`、`TASKS.md`、根 `README.md` 与 `preview/README.md`；本提交只新增 `preview/` 下的文件。
- 本目录是候选实现，是否合并进 `final/` 由评审方决定，本代理不做自动提升。
- 未提交任何 PR，未修改其他仓库，未创建 CI。
