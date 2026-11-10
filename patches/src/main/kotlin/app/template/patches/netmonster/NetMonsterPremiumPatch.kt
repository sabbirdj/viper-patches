package app.template.patches.netmonster

import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.patch
import app.template.patches.shared.Constants.COMPATIBILITY_NETMONSTER
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.NarrowLiteralInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction

@Suppress("unused")
val netMonsterPremiumPatch = patch(
    name = "Unlock NetMonster Premium",
    description = "Activates NetMonster Premium and removes ads by forcing the local Adapty subscription check to true. Verified on NetMonster 5.3.1.",
    default = true
) {
    compatibleWith(COMPATIBILITY_NETMONSTER)

    execute {
        // The app computes one "has premium" boolean from the Adapty
        // subscription list in tzvqghas.emit and stores it in ojhg.wqffeyl
        // (a MutableStateFlow<Boolean?>). That same boolean feeds the
        // ads-suppression chain, so forcing it true activates premium AND
        // suppresses ads. There is no separate ads gate to patch.
        //
        // The boolean starts FALSE (const/4 v0, 0x0) and is set TRUE
        // (const/4 v0, 0x1) only if some subscription isActive() or
        // isInGracePeriod(). Replace the single init const with one that
        // sets it to 1 -- a 1-code-unit for 1-code-unit swap, so nothing
        // shifts and no label is involved at all (the proven approach from
        // the SAI and APKS Installer patches: replaceInstruction only,
        // never addInstructions before a branch target).
        //
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
    }
}
