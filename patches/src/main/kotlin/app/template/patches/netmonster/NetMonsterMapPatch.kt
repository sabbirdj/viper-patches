package app.template.patches.netmonster

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.patch
import app.template.patches.shared.Constants.COMPATIBILITY_NETMONSTER
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference

@Suppress("unused")
val netMonsterMapPatch = patch(
    name = "Fix map via MicroG-RE",
    description = "Draws the live map through MicroG-RE's built-in MapLibre renderer instead of Google Play Services, so the map works without real Play Services. Requires MicroG-RE 7.0.0 or later (it added the Maps renderer in 7.0.0). Tiles come from OpenFreeMap, so they look different from Google Maps and there is no satellite view. Also neutralises the maps_dynamite retry, which MicroG's Dynamite loader claims and which would otherwise return a filtered Dynamite module context instead of the MicroG package context. Verified on NetMonster 5.3.1.",
    default = true
) {
    compatibleWith(COMPATIBILITY_NETMONSTER)

    execute {
        // NetMonster's map is Google Maps, and the renderer is NOT in
        // the APK — it is loaded from Play Services as a Dynamite
        // module. On a de-Googled device with only MicroG-RE, the map
        // is black for TWO independent reasons, and both must be
        // fixed (v1.2.0 fixed only the second, so it did nothing):
        //
        // 1) THE GATE (zq.bmpaywgdk). Before the module-context
        // loader is ever reached, this method runs
        // Lrqatw.zeulwnh (GooglePlayServicesUtil
        // .isGooglePlayServicesAvailable) with the required version
        // 0xcc77c0 and throws GooglePlayServicesNotAvailableException
        // when it returns non-zero:
        //   const v0, 0xcc77c0
        //   invoke-static {p0, v0}, Lrqatw;->zeulwnh(Context, I)I
        //   move-result v0
        //   if-nez v0, :cond_3        (non-zero -> throw)
        // Lrqatw.zeulwnh looks for the literal package
        // "com.google.android.gms", which MicroG-RE does not claim
        // (it is app.revanced.android.gms). So the gate throws and
        // the redirect in (2) never runs. The fix inserts
        // `const/4 v0, 0x0` before the if-nez so it never branches
        // to the throw and execution proceeds to load the renderer.
        // The if-nez is NOT a branch target (reached only by
        // fall-through from move-result), so inserting before it is
        // safe — the SAI lesson about addInstructions only applies
        // before branch targets.
        //
        // 2) THE REDIRECT (zq.acppncjbu). When the Dynamite load
        // fails — microG has no Maps Dynamite module — the method
        // falls back to createPackageContext("com.google.android.gms").
        // But MicroG-RE is installed as app.revanced.android.gms,
        // so the fallback throws NameNotFoundException and the
        // method returns null. MicroG-RE 7.0.0+ ships its own
        // MapLibre renderer (CreatorImpl) for exactly this redirect.
        // The fix is a single same-size replaceInstruction changing
        // the fallback package string to "app.revanced.android.gms".
        // The caller then loads CreatorImpl out of MicroG-RE's
        // classloader and the map draws (OpenFreeMap tiles). When
        // MicroG-RE is absent the fallback still throws and returns
        // null, exactly as before — so this is a no-op without it,
        // never a new failure.
        //
        // 3) THE RETRY (zq.acppncjbu, v1.2.2). v1.2.1 still failed
        // on device even though the gate and the redirect both land
        // in the dex. The remaining hole is the retry inside the
        // Dynamite catch: after maps_core_dynamite fails, the
        // method retries with "com.google.android.gms.maps_dynamite"
        // — and MicroG-RE's Dynamite loader *claims* that module
        // (its DynamiteLoaderImpl.getModuleVersion returns 1 for
        // maps_dynamite, "returning v1 for maps"). So the retry can
        // SUCCEED and return a DynamiteContext whose classloader is
        // a FilteredClassLoader over the module's merged classes —
        // a context that never reaches the createPackageContext
        // fallback (and its redirect) at all. The fix is another
        // same-size replaceInstruction changing the retry's module
        // name to "com.google.android.gms.maps_core_dynamite",
        // which MicroG reports version 0 for, so the retry fails
        // exactly like the first load and execution always falls
        // through to the redirected fallback in (2). The string is
        // also the equals-check operand, so for the common
        // maps_core_dynamite case the retry is skipped entirely and
        // it goes straight to the fallback. With real Play Services
        // the first load succeeds, so this path never runs — no
        // behaviour change there. Without any GMS the fallback
        // still throws and returns null, exactly as before.
        //
        // The string "com.google.android.gms" appears exactly once
        // in acppncjbu (the other const-strings are the
        // maps_*_dynamite module names and the CreatorImpl class
        // name), so matching on the exact string is unambiguous.
        // The register is read from the found instruction rather
        // than assumed, so the replacement stays correct.

        // 1) Bypass the Play Services availability gate in
        // zq.bmpaywgdk. Find the invoke-static to Lrqatw.zeulwnh;
        // the if-nez is two instructions later (invoke, move-result,
        // if-nez). Insert `const/4 v0, 0x0` before it so the gate
        // result is ignored and execution always proceeds to load
        // the renderer.
        NetMonsterMapsGateFingerprint.let {
            val instructions = it.method.implementation?.instructions
                ?: error("zq.bmpaywgdk: no implementation")
            val invokeIndex = instructions.indexOfFirst { instr ->
                instr.opcode == Opcode.INVOKE_STATIC &&
                    instr is ReferenceInstruction &&
                    (instr.reference as? MethodReference)?.let { ref ->
                        ref.definingClass == "Lrqatw;" && ref.name == "zeulwnh"
                    } == true
            }
            require(invokeIndex >= 0) { "zq.bmpaywgdk: Lrqatw.zeulwnh call not found" }
            val ifNezIndex = invokeIndex + 2
            require(ifNezIndex < instructions.size) { "zq.bmpaywgdk: if-nez missing after gate call" }
            val ifNez = instructions[ifNezIndex]
            require(ifNez.opcode == Opcode.IF_NEZ) {
                "zq.bmpaywgdk: expected IF_NEZ at ${ifNezIndex}, got ${ifNez.opcode}"
            }
            it.method.addInstructions(ifNezIndex, "const/4 v0, 0x0")
        }

        // 2) Redirect the fallback package in zq.acppncjbu to
        // MicroG-RE (see above).
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

        // 3) Kill the maps_dynamite retry in zq.acppncjbu (see
        // above): the retry's module name is the const-string
        // "com.google.android.gms.maps_dynamite", which MicroG-RE's
        // Dynamite loader claims (version 1), so the retry can
        // succeed and return a filtered DynamiteContext instead of
        // ever reaching the redirected fallback. Replacing it with
        // "com.google.android.gms.maps_core_dynamite" (version 0
        // under MicroG) makes the retry fail like the first load,
        // so execution always falls through to the fallback.
        NetMonsterMapsFingerprint.let {
            val instructions = it.method.implementation?.instructions
                ?: error("zq.acppncjbu: no implementation")
            val retryIndex = instructions.indexOfFirst { instr ->
                instr.opcode == Opcode.CONST_STRING &&
                    instr is ReferenceInstruction &&
                    (instr.reference as? StringReference)?.string == "com.google.android.gms.maps_dynamite"
            }
            require(retryIndex >= 0) { "zq.acppncjbu: const-string maps_dynamite not found" }
            val reg = (instructions[retryIndex] as OneRegisterInstruction).registerA
            it.method.replaceInstruction(retryIndex, "const-string v$reg, \"com.google.android.gms.maps_core_dynamite\"")
        }
    }
}
