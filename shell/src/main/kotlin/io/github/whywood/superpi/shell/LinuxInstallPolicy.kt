package io.github.whywood.superpi.shell

fun shouldAutoInstallLinux(supported: Boolean, state: LinuxInstallState): Boolean =
    supported && state is LinuxInstallState.NotInstalled
