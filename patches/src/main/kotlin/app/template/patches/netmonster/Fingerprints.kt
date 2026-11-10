package app.template.patches.netmonster

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.methodCall

/**
 * NetMonster (cz.mroczis.netmonster) premium is decided by Adapty. The app
 * reads the subscription list from the Adapty profile and turns it into a
 * single "has premium" boolean in one central place, then stores it in a
 * MutableStateFlow (ojhg.wqffeyl). The same boolean feeds the
 * ads-suppression chain, so forcing it true activates premium AND
 * suppresses ads.
 *
 * The subscription list itself comes from Adapty's servers, so this
 * fingerprint matches on the local boolean derivation, not on the server
 * data. The boolean register forced by the patch (v0) is correct for
 * NetMonster 5.3.1; the patch is pinned to that version in Constants.
 *
 * The fingerprint pins its defining class and method name. Without that,
 * the patcher searches every method in the app, and the generic
 * single-letter names (tzvqghas, emit) can match the wrong method. Pinning
 * makes the fingerprint land on exactly the method it was written for.
 */

/**
 * tzvqghas.emit — the central premium check. It iterates the Adapty
 * subscription list and computes the boolean in v0:
 *   const/4 v0, 0x0                      (initial FALSE)
 *   ... for each subscription ...
 *   invoke-virtual {p2}, AdaptyProfile$Subscription;->isActive()Z
 *   if-nez v1, :cond_2
 *   invoke-virtual {p2}, AdaptyProfile$Subscription;->isInGracePeriod()Z
 *   if-eqz p2, :cond_1
 *   :cond_2
 *   const/4 v0, 0x1                      (any active-or-grace sub -> TRUE)
 *   :goto_0
 *   invoke-static {v0}, Boolean.valueOf(Z)
 *   invoke-virtual {p0, p1}, Lemuie;->klnnokp(Ljava/lang/Object;)V
 *     (stores into ojhg.wqffeyl)
 *
 * The patch replaces the single `const/4 v0, 0x0` with `const/4 v0, 0x1`,
 * so the stored premium boolean is always TRUE once the profile loads.
 */
object NetMonsterPremiumFingerprint : Fingerprint(
    definingClass = "Ltzvqghas;",
    name = "emit",
    returnType = "Ljava/lang/Object;",
    parameters = listOf("Ljava/lang/Object;", "Llnzwbdtfp;"),
    filters = listOf(
        methodCall(
            definingClass = "Lcom/adapty/models/AdaptyProfile\$Subscription;",
            name = "isActive",
            parameters = listOf(),
            returnType = "Z",
        ),
        methodCall(
            definingClass = "Lcom/adapty/models/AdaptyProfile\$Subscription;",
            name = "isInGracePeriod",
            parameters = listOf(),
            returnType = "Z",
        ),
    )
)
