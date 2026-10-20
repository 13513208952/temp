# ReBrowser 低成本 Agent 独立交付

本仓库只用于提交独立的代码增量供审查，不包含运行中的产品、凭据或用户数据。

| 目录 | 内容 | HOST 验证 |
| --- | --- | --- |
| [`d02/`](d02/) | 首轮 42 个目标（36 Java + 6 运行资产）及审查修正后的最终版 | 36 classes / 129 tests / 0 failures |
| [`d03/`](d03/) | 第二轮 25 个既有 Java 文件扩展（L49–L73） | 43 classes / 180 tests / 0 failures |

两轮均自带 `app/`、`context/`、`build.sh`、`evidence/` 与报告：

- `d02/REVIEW-REPORT.md`、`d02/DELIVERY.md`；`d03/evidence/D03-REPORT.md`
- `d02/review-evidence/` 保存复核与追加修复的实测记录（历史原始证据不改写）

## 历史目录

- `preview/rebrowser-d02/`：D02 首次提交与两轮审查时使用的原始路径，保留以维持旧报告引用。
- `mailbox/`：审查邮件与审查者/执行者的历史报告（只追加，不改写）。

## 边界

仅 HOST 编译/测试与静态检查。Android 设备、Activity/View/Notification/MotionEvent、Gecko 真实回调、网页渲染/CSP 强制、主工程 AGP 构建均**未运行**。未使用真实账户、密钥、私有邮件、文件、网络、安装或删除操作。

## 复现

```sh
cd d02   # 或 d03
ANDROID_JAR=… GECKO_JAR=… JUNIT=… HAMCREST=… ./build.sh
```

仓库不使用 GitHub Actions/CI；所有测试均在本地产出并在各目录报告中注明执行范围。
