# Mika / Phongent 本地画面识别

来源为 mylea1/phongent `cb58c10425a870d189ef39b8cc3dd1c8e8155d3e`。

- `a11y/PhoneOcr.kt` 原中英并行本机识别，在两个识别器完成后释放位图。
- `a11y/StillFrameRead.kt` 原识别前稳帧和切页后重认规则。
- `display/FrameMath.kt` 原帧采样、黑帧判别及整页变化判定。
- DeepDeep 局部截图、坐标回映和工具输出适配在 `io/github/whywood/deepdeep/screen/PhongentVisibleText.kt`。
