package app.template.patches.apksinstaller

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.patch
import app.template.patches.shared.Constants.COMPATIBILITY_APKS_INSTALLER

@Suppress("unused")
val apksInstallerRemoveAdsPatch = patch(
    name = "Remove ads (APKS Installer)",
    description = "Disables all ads by forcing the master ads flag MyApplication.m to false and making the ad initializer a no-op. Verified on APKS Installer 2.8-Google.",
    default = true
) {
    compatibleWith(COMPATIBILITY_APKS_INSTALLER)

    execute {
        // 1) MyApplication.onCreate: force m=false at startup.
        // The sput-boolean to m is filter index 1; the value stored is in v1 (2.8-Google).
        ApksInstallerMyApplicationFingerprint.let {
            val sputIndex = it.instructionMatches[1].index
            it.method.addInstructions(
                sputIndex,
                """
                    const/4 v1, 0x0
                """
            )
        }

        // 2) l2/N.a (ad initializer): return immediately so it never sets m=true
        // and never calls MobileAds.initialize().
        ApksInstallerAdsInitFingerprint.let {
            it.method.addInstructions(
                0,
                """
                    return-void
                """
            )
        }
    }
}
