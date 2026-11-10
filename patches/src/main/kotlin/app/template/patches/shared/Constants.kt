package app.template.patches.shared

import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility

object Constants {
    val COMPATIBILITY_SAI = Compatibility(
        name = "SAI",
        packageName = "com.mtv.sai",
        appIconColor = 0x1976D2,
        targets = listOf(
            AppTarget(version = "2.4.3")
        )
    )

    val COMPATIBILITY_APKS_INSTALLER = Compatibility(
        name = "APKS Installer",
        packageName = "com.inwepo.apkinstaller",
        appIconColor = 0x4CAF50,
        targets = listOf(
            AppTarget(version = "2.8-Google")
        )
    )
}
