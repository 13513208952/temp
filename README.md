# 独立审查暂存区

本仓库只用于提交**独立的代码增量**供远端审查，不包含任何运行中的产品、凭据或用户数据。

- 最新提交：[`preview/rebrowser-d02/`](preview/rebrowser-d02/) — ReBrowser v2 单轮42文件增量（36 Java + 6 运行资产）及其36个测试类。
- 该提交自带 `DELIVERY.md`、`evidence/` 和 `build.sh`；请在提交目录内阅读，不需要访问其他仓库。
- 仓库不使用 GitHub Actions/CI；所有测试均在本地产出并在 `DELIVERY.md` 中如实标注执行范围。
