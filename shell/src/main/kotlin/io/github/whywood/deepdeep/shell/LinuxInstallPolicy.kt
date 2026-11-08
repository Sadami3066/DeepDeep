package io.github.whywood.deepdeep.shell

fun shouldAutoInstallLinux(supported: Boolean, state: LinuxInstallState): Boolean =
    supported && state is LinuxInstallState.NotInstalled
