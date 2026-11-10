package app.template.patches.netmonster

import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.patch
import app.template.patches.shared.Constants.COMPATIBILITY_NETMONSTER
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.StringReference

@Suppress("unused")
val netMonsterMapPatch = patch(
    name = "Fix map via MicroG-RE",
    description = "Draws the live map through MicroG-RE's built-in MapLibre renderer instead of Google Play Services, so the map works without real Play Services. Requires MicroG-RE 7.0.0 or later (it added the Maps renderer in 7.0.0). Tiles come from OpenFreeMap, so they look different from Google Maps and there is no satellite view. Verified on NetMonster 5.3.1.",
    default = true
) {
    compatibleWith(COMPATIBILITY_NETMONSTER)

    execute {
        // NetMonster's map is Google Maps, and the renderer is NOT in
        // the APK — it is loaded from Play Services as a Dynamite
        // module (zq.acppncjbu). microG has no Maps Dynamite module,
        // so that load fails and the method falls back to
        //   createPackageContext("com.google.android.gms", 3)
        // But MicroG-RE is installed as app.revanced.android.gms,
        // NOT com.google.android.gms, so the fallback throws
        // NameNotFoundException, the method returns null, and the
        // MapView renders black. MicroG-RE 7.0.0+ ships its own
        // MapLibre renderer (CreatorImpl) for exactly this redirect.
        //
        // The fix is a single same-size replaceInstruction: change
        // the fallback package string to "app.revanced.android.gms".
        // The caller then loads CreatorImpl out of MicroG-RE's
        // classloader and the map draws (OpenFreeMap tiles). When
        // MicroG-RE is absent the fallback still throws and returns
        // null, exactly as before — so this is a no-op without it,
        // never a new failure.
        //
        // The string "com.google.android.gms" appears exactly once
        // in this method (the other const-strings are the
        // maps_*_dynamite module names and the CreatorImpl class
        // name), so matching on the exact string is unambiguous.
        // The register is read from the found instruction rather
        // than assumed, so the replacement stays correct.
        NetMonsterMapsFingerprint.let {
            val instructions = it.method.implementation?.instructions
                ?: error("zq.acppncjbu: no implementation")
            val constIndex = instructions.indexOfFirst { instr ->
                instr.opcode == Opcode.CONST_STRING &&
                    instr is ReferenceInstruction &&
                    (instr.reference as? StringReference)?.string == "com.google.android.gms"
            }
            require(constIndex >= 0) { "zq.acppncjbu: const-string com.google.android.gms not found" }
            val reg = (instructions[constIndex] as OneRegisterInstruction).registerA
            it.method.replaceInstruction(constIndex, "const-string v$reg, \"app.revanced.android.gms\"")
        }
    }
}
