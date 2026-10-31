package io.github.whywood.deepdeep.privilege

import io.github.whywood.deepdeep.shell.RootStatus

internal fun resolveAvailableBackends(adbAvailable: Boolean, rootAvailable: Boolean): Set<PrivilegeLevel> =
    buildSet {
        if (adbAvailable) add(PrivilegeLevel.ADB)
        if (rootAvailable) add(PrivilegeLevel.ROOT)
    }

internal fun resolveEffectiveLevel(
    selected: PrivilegeLevel,
    adbAvailable: Boolean,
    rootAvailable: Boolean,
): PrivilegeLevel = when (selected) {
    PrivilegeLevel.NORMAL -> PrivilegeLevel.NORMAL
    PrivilegeLevel.ADB -> when {
        adbAvailable -> PrivilegeLevel.ADB
        rootAvailable -> PrivilegeLevel.ROOT
        else -> PrivilegeLevel.NORMAL
    }

    PrivilegeLevel.ROOT -> when {
        rootAvailable -> PrivilegeLevel.ROOT
        adbAvailable -> PrivilegeLevel.ADB
        else -> PrivilegeLevel.NORMAL
    }
}

internal fun resolveMigratedLevel(stored: PrivilegeLevel?, legacyRootEnabled: Boolean): PrivilegeLevel =
    stored ?: if (legacyRootEnabled) PrivilegeLevel.ROOT else PrivilegeLevel.NORMAL

internal fun parsePrivilegeLevel(raw: String): PrivilegeLevel = when (raw) {
    "NORMAL" -> PrivilegeLevel.NORMAL
    "ADB", LEGACY_ADB_NAME -> PrivilegeLevel.ADB
    "ROOT" -> PrivilegeLevel.ROOT
    else -> PrivilegeLevel.NORMAL
}

internal fun PrivilegeState.resolveEffective(): PrivilegeState = copy(
    effective = resolveEffectiveLevel(
        selected = selected,
        adbAvailable = adb.daemon,
        rootAvailable = root == RootStatus.Granted,
    ),
)

private const val LEGACY_ADB_NAME = "SHIZUKU"
