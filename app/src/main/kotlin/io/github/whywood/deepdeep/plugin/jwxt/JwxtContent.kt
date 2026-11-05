package io.github.whywood.deepdeep.plugin.jwxt

internal object JwxtContent {
    fun skillMarkdown(): String = SKILL_MARKDOWN

    fun promptBody(ready: Boolean, account: String?, statusLabel: String): String = if (ready) {
        buildString {
            append("中山大学教务插件已安装并已登录")
            if (!account.isNullOrBlank()) append("（账号 $account）")
            appendLine("。在 bash 中用 `jwxt` 查询课表、成绩、学分、考试、课程、公告、空闲教室与请假记录；用法见技能 deepdeep-jwxt，不记得命令先跑 `jwxt --help`。")
            appendLine("成绩、绩点、请假记录与个人信息只在本机会话使用，不得外发或写进对外内容；不要输出账号、密码、验证码或 session.json 内容。")
            appendLine("请假（`jwxt leave apply`）前必须先用 ask_user_question 向用户确认，确认后才加 `--yes`，并先 `--dry-run` 预览；附件名只能 ASCII。")
            appendLine("会话失效时按登录流程重登：`jwxt login --send-code` → ask_user_question 取 4 位验证码 → `jwxt login --code 1234`；续期可能顶掉浏览器里的登录，提醒用户刷新即可。")
            appendLine("接口报错、证书链错误或查不到数据时不要反复重试，说明现象并让用户检查网络或更新插件。")
            append("不要自行创建定时任务或总结流程，除非用户明确要求。")
        }
    } else {
        buildString {
            appendLine("中山大学教务插件状态：$statusLabel")
            appendLine("登录流程：`jwxt login --send-code` 发送 4 位验证码到用户的企业微信 → 用 ask_user_question 请用户提供验证码 → `jwxt login --code <验证码>` 完成登录。")
            appendLine("如果状态提示缺少 Node.js 或版本过低，先让用户到 设置 → 运行环境 安装或升级 Node.js。")
            appendLine("不要向用户索要账号密码；账号密码由用户在插件详情页的「设置」里填写，未填写时先引导用户去填。")
            append("完成登录前如需使用教务能力，先向用户说明需要登录。")
        }
    }

    private val SKILL_MARKDOWN = """
        ---
        name: deepdeep-jwxt
        description: 中山大学教务系统 CLI——查课表、成绩、学分、考试、教学大纲、公告、空闲教室、请假记录与提交。用户问上课时间、某门课怎么样、成绩绩点、考试安排、空教室、教学大纲、请假审批时用；在 bash 中运行 jwxt。
        ---

        # 中山大学教务（jwxt）

        在 bash 中直接运行 `jwxt`。默认输出排版文本，加 `--json` 出结构化数据；完整选项看 `jwxt --help`。

        - 本周课表：`jwxt`
        - 指定周：`jwxt schedule --week 8`；`--today` / `--tomorrow` 只看今天或明天
        - 成绩：`jwxt scores`；`--all` 全部学期；`--semester 2025-2` 指定学期
        - 毕业学分进度：`jwxt credits`
        - 考试安排：`jwxt exams`
        - 一门课：`jwxt course <课程码或课名>`；`--intro` 简介、考核方式与教材；`--syllabus` 逐周进度
        - 公告：`jwxt notice`；`jwxt notice <id>` 看全文；`--num N` 条数
        - 空闲教室：`jwxt room`；`--building` `--room` `--week` `--day`；`--sections 9-11` 反过来找这节空着的教室
        - 助教信息：`jwxt ta`
        - 请假记录：`jwxt leave`

        ## 登录

        会话失效时会用 TGC 静默续期；真失效时按下面的流程登录，不要向用户索要账号密码（账号密码由用户在插件详情页的「设置」里填写）：

        1. `jwxt login --send-code`：把 4 位验证码发到用户的企业微信，命令随即返回；
        2. 用 ask_user_question 请用户提供 4 位验证码；
        3. `jwxt login --code 1234`：用验证码完成登录。

        如果第一步直接提示登录完成（没有要求验证码），跳过后面两步。验证码不要写进文件，也不要回显在对话里。

        ## 要记住的

        - **`leave apply` 是唯一的写操作，提交后撤不回来**，撤单只能找辅导员。先在 bash 用 `--dry-run` 预览，再用 ask_user_question 向用户确认，确认后才加 `--yes`。
        - 请假必须带附件（jpg/jpeg/png/gif/pdf），**附件名只能 ASCII**，中文文件名会被存成一串问号；附件放在工作区，用 proot 内可读的绝对路径传给 `--file`。
        - **课名会撞车**：同名课各学院各有一份。课程码最可靠；只用课名时，能唯一确定才给结果，否则列候选、让用户给课程码，不要自己猜一个。
        - **绩点是自算口径**（只算有绩点的课，缓考与 P/F 不计），不是学校官方口径，不要当权威数字汇报，说明口径即可。
        - `exams` 和 `course` 要几秒才出结果，不是卡死。
        - TGC 静默续期可能顶掉浏览器里的登录，告诉用户刷新一下就能免密回去。
        - 接口改版、证书链错误或一直报错时不要反复重试：说明报错内容，提示用户检查网络或更新插件。
        - 成绩、绩点、请假记录与个人信息只在本机会话使用，不得写到对外内容或外发；不要输出凭据、验证码与 session.json 内容。
        - 不要自行创建定时任务或总结流程，除非用户明确要求。
    """.trimIndent()
}
