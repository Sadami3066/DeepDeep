package io.github.whywood.deepdeep.shell

sealed interface LinuxInstallState {
    data object NotInstalled : LinuxInstallState

    data object Installing : LinuxInstallState

    data class Installed(
        val version: String,
        val sizeBytes: Long,
    ) : LinuxInstallState

    data class Failed(
        val message: String,
    ) : LinuxInstallState
}
