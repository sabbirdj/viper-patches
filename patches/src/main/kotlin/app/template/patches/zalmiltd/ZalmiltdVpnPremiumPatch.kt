package app.template.patches.zalmiltd

import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.patch
import app.morphe.patcher.util.returnEarly
import app.template.patches.shared.Constants.COMPATIBILITY_ZALMILTD_VPN
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.NarrowLiteralInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference

/**
 * Finds the first sget-object instruction loading the given enum field
 * (e.g. Lkh3;->d) and returns its index and register. The register is
 * read from the instruction itself so the replacement keeps the original
 * register.
 */
private fun findSgetObject(
    fingerprint: app.morphe.patcher.Fingerprint,
    definingClass: String,
    fieldName: String,
    errorLabel: String,
): Pair<Int, Int> {
    val instructions = fingerprint.method.implementation?.instructions
        ?: error("$errorLabel: no implementation")
    val index = instructions.indexOfFirst { instr ->
        instr.opcode == Opcode.SGET_OBJECT &&
            instr is ReferenceInstruction &&
            (instr.reference as? FieldReference)?.let {
                it.definingClass == definingClass && it.name == fieldName
            } == true
    }
    require(index >= 0) { "$errorLabel: sget-object $definingClass->$fieldName not found" }
    val register = (instructions[index] as OneRegisterInstruction).registerA
    return index to register
}

/**
 * Finds the single `const/4 _, 0x0` in a method and returns its index and
 * register. Used for the tier predicates, whose false branch is exactly
 * one const-0 (the true branch is const-1).
 */
private fun findConstZero(
    fingerprint: app.morphe.patcher.Fingerprint,
    errorLabel: String,
): Pair<Int, Int> {
    val instructions = fingerprint.method.implementation?.instructions
        ?: error("$errorLabel: no implementation")
    val index = instructions.indexOfFirst { instr ->
        instr.opcode == Opcode.CONST_4 &&
            instr is NarrowLiteralInstruction && instr.narrowLiteral == 0
    }
    require(index >= 0) { "$errorLabel: const/4 _, 0x0 not found" }
    val register = (instructions[index] as OneRegisterInstruction).registerA
    return index to register
}

@Suppress("unused")
val zalmiltdVpnPremiumPatch = patch(
    name = "Unlock Full Premium",
    description = "Unlocks Bangladesh VPN (Zalmiltd) Full Premium by forcing the local subscription tier to FULL in every place that reads it: the tier predicates (kh3.d/f), the Play Billing refresh, the persisted tier default, and the free-time limit. VIP servers connect, ads stay off and the connection is never cut for VIP time running out. No server change needed. Verified on 4.1.",
    default = true
) {
    compatibleWith(COMPATIBILITY_ZALMILTD_VPN)

    execute {
        // All edits are replaceInstruction (same-size swaps), except the
        // free-time check, which uses returnEarly -- see its note below.
        // This follows the lesson from the SAI patch: never use
        // addInstructions before a branch target -- the patcher reports
        // success but the inserted code is silently skipped.

        // 1) Lkh3;->f(): force the FULL-tier predicate to true.
        // Replace the `const/4 p0, 0x0` at :cond_0 with 0x1.
        ZalmiltdIsFullFingerprint.let {
            val (constIndex, register) = findConstZero(it, "kh3.f")
            it.method.replaceInstruction(constIndex, "const/4 v$register, 0x1")
        }

        // 2) Lkh3;->d(): force the "is BASIC or FULL" predicate to true.
        // Replace the `const/4 p0, 0x0` at :cond_0 with 0x1.
        ZalmiltdIsPaidFingerprint.let {
            val (constIndex, register) = findConstZero(it, "kh3.d")
            it.method.replaceInstruction(constIndex, "const/4 v$register, 0x1")
        }

        // 3) Lpx0;->invokeSuspend (billing refresh): force the refresh to
        // always resolve to FULL. Replace the BASIC fallback
        // `sget-object v3, Lkh3;->e` and the NONE fallback
        // `sget-object v3, Lkh3;->d` with `sget-object v3, Lkh3;->f`.
        // The "full_features" branch already loads kh3.f.
        ZalmiltdBillingRefreshFingerprint.let {
            val (basicIndex, basicRegister) = findSgetObject(
                it, "Lkh3;", "e", "px0.invokeSuspend"
            )
            it.method.replaceInstruction(
                basicIndex, "sget-object v$basicRegister, Lkh3;->f:Lkh3;"
            )
            val (noneIndex, noneRegister) = findSgetObject(
                it, "Lkh3;", "d", "px0.invokeSuspend"
            )
            it.method.replaceInstruction(
                noneIndex, "sget-object v$noneRegister, Lkh3;->f:Lkh3;"
            )
        }

        // 4) DataStore default (Lae4.<clinit>): force the default tier
        // to FULL. Replace `sget-object v2, Lkh3;->d` (NONE) with
        // `sget-object v2, Lkh3;->f` (FULL), so a fresh install (or
        // after clearing app data) starts at FULL instead of NONE.
        ZalmiltdDefaultTierFingerprint.let {
            val (sgetIndex, register) = findSgetObject(
                it, "Lkh3;", "d", "ae4.<clinit>"
            )
            it.method.replaceInstruction(
                sgetIndex, "sget-object v$register, Lkh3;->f:Lkh3;"
            )
        }

        // 5) Lhn1;->d(): force the free-time limit off. returnEarly is
        // used here instead of replaceInstruction: the method is
        // straight-line (no branches, so nothing can skip the inserted
        // return), there is no const-0 to replace, and the stored
        // "limited" value from an earlier unpatched run must be ignored
        // -- flipping the getBoolean default alone would not do that.
        ZalmiltdFreeTimeLimitedFingerprint.method.returnEarly(false)
    }
}
