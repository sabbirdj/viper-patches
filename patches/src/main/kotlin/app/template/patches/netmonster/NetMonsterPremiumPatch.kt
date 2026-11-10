package app.template.patches.netmonster

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.patch
import app.template.patches.shared.Constants.COMPATIBILITY_NETMONSTER
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.NarrowLiteralInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction

@Suppress("unused")
val netMonsterPremiumPatch = patch(
    name = "Unlock NetMonster Premium",
    description = "Activates NetMonster Premium and removes ads by forcing the local Adapty subscription checks to true and giving the premium card a real expiry date. Verified on NetMonster 5.3.1.",
    default = true
) {
    compatibleWith(COMPATIBILITY_NETMONSTER)

    execute {
        // v1.1.0 taught us the premium state lives in TWO places, and
        // forcing only one of them leaves the menu's PURCHASE button up:
        //  - ojhg.wqffeyl (via tzvqghas.emit) drives ads suppression and
        //    the features gated by ojhg.klnnokp.
        //  - cpysz (via fajpup + muov) drives the menu's premium card.
        //    Its boolean (bvmpacozx) comes from the active-subscription
        //    count, and its expiry (bmpaywgdk) is the max expiry of active
        //    subs — null when there are none. The card renderer
        //    (byjcq.cztaxulh) only hides the PURCHASE button when the
        //    expiry is non-null, and its non-null path dereferences the
        //    expiry, so that branch cannot just be inverted.
        // So this patch forces all three, each with the proven
        // replaceInstruction approach (same-size, no offset shift, no
        // label) — never addInstructions before a branch target.

        // 1) tzvqghas.emit: force the "has premium" boolean stored in
        // ojhg.wqffeyl to TRUE (ads suppression + klnnokp features).
        // Replace the single `const/4 v0, 0x0` with `const/4 v0, 0x1`.
        // (MutableMethod has no `instructions` property; they live on its
        // implementation.)
        NetMonsterPremiumFingerprint.let {
            val instructions = it.method.implementation?.instructions
                ?: error("tzvqghas.emit: no implementation")
            val constIndex = instructions.indexOfFirst { instr ->
                instr.opcode == Opcode.CONST_4 &&
                    instr is OneRegisterInstruction && instr.registerA == 0 &&
                    instr is NarrowLiteralInstruction && instr.narrowLiteral == 0
            }
            require(constIndex >= 0) { "tzvqghas.emit: const/4 v0, 0x0 not found" }
            it.method.replaceInstruction(constIndex, "const/4 v0, 0x1")
        }

        // 2) fajpup.invoke (:pswitch_7): force the card's premium boolean
        // cpysz.bvmpacozx to TRUE. Replace the single `const/4 v0, 0x0`
        // (the only one in the whole file) with `const/4 v0, 0x1`.
        NetMonsterCardFingerprint.let {
            val instructions = it.method.implementation?.instructions
                ?: error("fajpup.invoke: no implementation")
            val constIndex = instructions.indexOfFirst { instr ->
                instr.opcode == Opcode.CONST_4 &&
                    instr is OneRegisterInstruction && instr.registerA == 0 &&
                    instr is NarrowLiteralInstruction && instr.narrowLiteral == 0
            }
            require(constIndex >= 0) { "fajpup.invoke: const/4 v0, 0x0 not found" }
            it.method.replaceInstruction(constIndex, "const/4 v0, 0x1")
        }

        // 3) muov.emit: force a far-future premium expiry date (2099)
        // so the card hides the PURCHASE button and reads "Active till
        // 2099". Insert a fixed 2099 date just before the Ltiedq.emit
        // call, overwriting v0 (the max-or-null expiry, null when there
        // are no active subs) with OffsetDateTime.parse("2099-12-31").
        // The emit call is NOT a branch target (it is only reached by
        // falling through from the preceding iput), so inserting before
        // it is safe. The pzkxkxo call itself is left alone — its
        // result is overwritten by the inserted parse.
        // (instructionMatches[1] is the Ltiedq.emit call; [0] is the
        // pzkxkxo call that identifies the method.)
        NetMonsterExpiryFingerprint.let {
            val emitIndex = it.instructionMatches[1].index
            it.method.addInstructions(
                emitIndex,
                """
                    const-string v0, "2099-12-31T23:59:59Z"
                    invoke-static {v0}, Ljava/time/OffsetDateTime;->parse(Ljava/lang/CharSequence;)Ljava/time/OffsetDateTime;
                    move-result-object v0
                """
            )
        }
    }
}
