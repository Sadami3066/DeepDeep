package com.wangzan101.mika.core.tool

/**
 * 工具做的是什么动作。按动作做判断的地方(守卫、裁剪、路线、批量、模型分工……)都看它,不看工具名;
 * 屏幕上的动作与读屏在几种界面上各有一个同动词的工具。
 */
enum class Verb {
    Observe, ListElements, Query, Collect, PhoneRun, PhoneFind, PhoneExtract, PhoneDialog, PhoneEvent, PhoneGesture, PhoneCommand, Screenshot, ViewPastScreens, ViewUserScreen, Describe, FindVisually, Diagnose,
    Tap, TypeText, ClearText, Scroll, Swipe, Back, PressKey,
    ListApps, LaunchApp, OpenLink, ShareText,
    SetAlarm, SetTimer, ShowAlarms, AddCalendarEvent, SetWheel,
    Navigate, ReadPage, EvaluateJs, SiteCommand, Tabs, Downloads, Upload, SiteMode, Script, Interact, Capture, WaitFor,
    RunCommand, RunBundledScript, ReadFile, WriteFile, SearchFiles, ShareFile,
    Wait, AskUser, RequestConfirmation, HandToUser, HandleObstacle, EndNotDone,
    KnowledgeSearch, KnowledgeRead, SkillSearch, SkillRead, MemorySearch, MemorySave, ProfileEdit,
    ScheduleTask,
    SpawnAgent,
}
