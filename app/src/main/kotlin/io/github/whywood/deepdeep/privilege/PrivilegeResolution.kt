package io.github.whywood.deepdeep.privilege

import io.github.whywood.deepdeep.shell.RootStatus

internal fun highestGrantedLevel(shizukuGranted: Boolean, rootGranted: Boolean): PrivilegeLevel = when {
    rootGranted -> PrivilegeLevel.ROOT
    shizukuGranted -> PrivilegeLevel.SHIZUKU
    else -> PrivilegeLevel.NORMAL
}

internal fun resolveEffectiveLevel(selected: PrivilegeLevel, granted: PrivilegeLevel): PrivilegeLevel =
    if (selected.ordinal <= granted.ordinal) selected else granted

internal fun resolveMigratedLevel(stored: PrivilegeLevel?, legacyRootEnabled: Boolean): PrivilegeLevel =
    stored ?: if (legacyRootEnabled) PrivilegeLevel.ROOT else PrivilegeLevel.NORMAL

internal fun parsePrivilegeLevel(raw: String): PrivilegeLevel =
    PrivilegeLevel.entries.firstOrNull { it.name == raw } ?: PrivilegeLevel.NORMAL

internal fun PrivilegeState.resolveEffective(): PrivilegeState = copy(
    effective = resolveEffectiveLevel(
        selected = selected,
        granted = highestGrantedLevel(
            shizukuGranted = shizuku.running && shizuku.granted,
            rootGranted = root == RootStatus.Granted,
        ),
    ),
)
