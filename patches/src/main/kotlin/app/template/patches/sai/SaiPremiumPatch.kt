package app.template.patches.sai

import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
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
        // NOTE: use addInstructionsWithLabels, not addInstructions.
        // Plain addInstructions inserts BEFORE the instruction at the index,
        // but a branch label stays attached to the original instruction.
        // Two of these three targets are branch targets (:cond_0 in fk0.h,
        // :goto_0 in dk0.invoke), so a plain insert would be skipped whenever
        // control reaches them by a jump -- which is exactly the path taken
        // when the user does NOT have premium. addInstructionsWithLabels
        // moves the label onto the inserted code, so the forced boolean runs
        // on every path into the target.

        // 1) ck0.invoke: force the boolean passed to Finished(hasActiveEntitlement=...).
        // The <init>(Z)V call is filter index 2; its boolean argument is in v1 (SAI 2.4.3).
        SaiPremiumCk0Fingerprint.let {
            val initIndex = it.instructionMatches[2].index
            it.method.addInstructionsWithLabels(
                initIndex,
                """
                    const/4 v1, 0x1
                """
            )
        }

        // 2) fk0.h: force the boolean wrapped by Boolean.valueOf and published to the UI.
        // The valueOf call is filter index 1; its boolean argument is in v1 (SAI 2.4.3).
        // valueOf is the :cond_0 branch target -- the label fix above is essential here.
        SaiPremiumFk0Fingerprint.let {
            val valueOfIndex = it.instructionMatches[1].index
            it.method.addInstructionsWithLabels(
                valueOfIndex,
                """
                    const/4 v1, 0x1
                """
            )
        }

        // 3) dk0: force the boolean so it takes the premium branch.
        // The fk0.h call is filter index 1; the boolean it branches on is in v3 (SAI 2.4.3).
        // The h call is the :goto_0 branch target -- the label fix above is essential here.
        SaiPremiumDk0Fingerprint.let {
            val hCallIndex = it.instructionMatches[1].index
            it.method.addInstructionsWithLabels(
                hCallIndex,
                """
                    const/4 v3, 0x1
                """
            )
        }
    }
}
