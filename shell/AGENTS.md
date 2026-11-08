# shell · Linux 与 Root 执行

两条命令执行通道：proot 里的 Alpine 用户空间，以及 su 的 Root 执行；管 rootfs 安装、进程树回收与输出截断。不含 agent 逻辑与界面。

## 文件

| 文件 | 职责 |
|---|---|
| `LinuxEnvironment.kt` | 模块门面：rootfs 安装、工作区目录与 proot 执行 |
| `LinuxExecutor.kt` | 执行结果模型与执行器接口（默认 60s） |
| `LinuxShellExecutor.kt` | 适配 pi 的 ShellExecutor 接口 |
| `LinuxInstallState.kt` | 安装状态：未装/安装中/已装/失败 |
| `LinuxInstallPolicy.kt` | 启动自动准备判定：仅 supported 且未安装时静默安装，已装/安装中跳过 |
| `LinuxRootfsInstaller.kt` | 解压内置 rootfs，校验 SHA256 并原子安装 |
| `RootfsExtractor.kt` | 安全 tar 解包，拦截路径穿越与越界软链 |
| `PRootExecutor.kt` | 起 proot 进程执行，流式取行、超时杀进程树 |
| `CommandOutput.kt` | 输出环形缓冲，超限转写日志并做 UTF-8 截断 |
| `RootShell.kt` | su 探测与 root 命令执行，带超时与双流截断 |
| `PosixTimeZone.kt` | 把系统时区转成 POSIX TZ 字符串传给命令 |

## 要点

- rootfs 压缩包、`libproot.so` 与加载器是本模块资源，装到 App `filesDir/linux/rootfs`；只支持 arm64-v8a。
- 一个 `LinuxEnvironment` 绑定一个工作目录，App 按 cwd 各建实例；proot 的 `-w` 与该目录一致。
- proot 不是安全沙箱；输出统一截到 64 KiB、超出后写日志并回传完整路径；取消必须杀整棵进程树。
- `sharedStorageEnabled` 与 `extraBinds` 控制宿主目录映射，App 侧传入 `/data` 绑定与存储开关。
- 测试在 `src/test/kotlin`（JUnit4 + coroutines-test），tar 夹具在 `TarFixtures.kt`。
