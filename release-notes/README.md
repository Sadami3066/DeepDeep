# Release notes

发布时把用户能看懂的更新说明写成本目录下的 `vX.Y.Z.md`，发布 workflow 会用 `--notes-file` 原样作为 Release body。

- 与 `app/build.gradle.kts` 的 `versionName` 和 tag 一致；先提交、打 tag，再手动触发 Release workflow。
- 按新增、改进、修复分点写，面向用户，不写 commit 列表、不写实现细节。
- body 会直接显示在 App 的「软件更新」页，不要写「见下方附件」这类需要跳去 GitHub 才能看懂的内容。
