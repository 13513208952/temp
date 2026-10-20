# 独立代码任务暂存区

本仓库只用于委派和存放**独立的小型 Java 组件及其测试**。请按 [TASKS.md](TASKS.md) 完成任务。

## 工作边界

- 只使用**本仓库当前文件**中明示的要求；不调查其他仓库、组织、旧项目、提交历史、关联项目或账号资料。此处没有需要继承的外部代码，也不要求兼容任何未提供的接口。
- 编写独立、可测试的通用代码，不推断使用方的应用架构，不添加集成层、业务系统或额外功能。
- 仅在本仓库提交成果。不要创建 Pull Request、不要修改其他仓库，不自动发布。
- **禁止 GitHub Actions、workflow、CI 触发或重跑**；不要新建 `.github/workflows/`，不要使用任何在线运行功能。为减少通知，尽量合并提交。
- 可以在自己的本地环境运行测试，但不可调用 GitHub 远程执行。无法运行的测试必须说明“未执行”。
- 不使用任何真实账号、用户数据、密码、密钥或个人设备。
- 只完成 TASKS.md 指定的任务；不做自由扩展。

交付时提供代码、单元测试和简短的 `DELIVERY.md`，写明实现范围、确实执行过的测试及未解决的问题。**提交到这里不意味着被任何其他软件采用。**

## Folder roles

- [`preview/`](preview/README.md): landing area for independent coding-agent submissions; currently a placeholder only.
- [`final/`](final/README.md): reviewed, optimized standalone candidate code and local regression tests.

These folders are deliberately separate. Do not automatically promote `preview` submissions to `final`, and do not run GitHub Actions or other remote CI.
