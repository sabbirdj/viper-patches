package app.template.patches.sai

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.patch
import app.template.patches.shared.Constants.COMPATIBILITY_SAI

@Suppress("unused")
val saiPremiumPatch = patch(
    name = "Unlock SAI Premium",
    description = "Activates SAI Premium by forcing the local entitlement check to true in all three places that read it (ck0, fk0, dk0). Verified on SAI 2.4.3.",
    default = true
) {
    compatibleWith(COMPATIBILITY_SAI)

    execute {
        // 1) ck0.invoke: force the boolean passed to Finished(hasActiveEntitlement=...).
        // The <init>(Z)V call is filter index 2; its boolean argument is in v1 (SAI 2.4.3).
        SaiPremiumCk0Fingerprint.let {
            val initIndex = it.instructionMatches[2].index
            it.method.addInstructions(
                initIndex,
                """
                    const/4 v1, 0x1
                """
            )
        }

        // 2) fk0.h: force the boolean wrapped by Boolean.valueOf and published to the UI.
        // The valueOf call is filter index 1; its boolean argument is in v1 (SAI 2.4.3).
        SaiPremiumFk0Fingerprint.let {
            val valueOfIndex = it.instructionMatches[1].index
            it.method.addInstructions(
                valueOfIndex,
                """
                    const/4 v1, 0x1
                """
            )
        }

        // 3) dk0: force the boolean so it takes the premium branch.
        // The fk0.h call is filter index 1; the boolean it branches on is in v3 (SAI 2.4.3).
        SaiPremiumDk0Fingerprint.let {
            val hCallIndex = it.instructionMatches[1].index
            it.method.addInstructions(
                hCallIndex,
                """
                    const/4 v3, 0x1
                """
            )
        }
    }
}
