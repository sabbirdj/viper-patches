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
 * NOTE (v1.1.0): forcing wqffeyl alone did NOT hide the menu's PURCHASE
 * button — the card reads a separate state object (cpysz), not wqffeyl.
 * So v1.1.1 adds the two fingerprints below.
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

/**
 * fajpup.invoke — the lambda that updates the premium card state (cpysz).
 * The menu reads cpysz, never ojhg.wqffeyl, so this is the boolean that
 * actually drives the card. Case :pswitch_7 (byte 0x15) sets
 * cpysz.bvmpacozx = (active-subscription-count > 0):
 *   invoke-virtual {p2}, Integer.intValue()I
 *   if-lez p0, :cond_0        (count <= 0 -> :cond_0)
 *   :goto_0  move v7, v0      (v0 is 1, set at invoke start)
 *   :cond_0  const/4 v0, 0x0  (the ONLY const/4 v0, 0x0 in the file)
 *   goto :goto_0
 *   invoke-static {v3..v8}, cpysz.bmpaywgdk(..., v7, ..., mask 0x7)
 *
 * The patch replaces that single `const/4 v0, 0x0` with `const/4 v0, 0x1`,
 * so bvmpacozx is always TRUE regardless of the subscription count.
 * fajpup has only one invoke method; the bmpaywgdk methodCall filter
 * confirms it is the cpysz-updating lambda.
 */
object NetMonsterCardFingerprint : Fingerprint(
    definingClass = "Lfajpup;",
    name = "invoke",
    returnType = "Ljava/lang/Object;",
    parameters = listOf("Ljava/lang/Object;", "Ljava/lang/Object;"),
    filters = listOf(
        methodCall(
            definingClass = "Lcpysz;",
            name = "bmpaywgdk",
            parameters = listOf(
                "Lcpysz;",
                "Ljava/time/OffsetDateTime;",
                "Ljava/lang/Integer;",
                "Z",
                "Z",
                "I"
            ),
            returnType = "Lcpysz;",
        ),
    )
)

/**
 * zq.acppncjbu — the Google Maps module-context loader. The Maps SDK
 * ships only a thin client; the renderer lives in Play Services and is
 * loaded as a Dynamite module (maps_legacy_dynamite /
 * maps_core_dynamite / maps_dynamite) via Loautvs.zeulwnh
 * (DynamiteModule.load). When that fails — microG has no Maps Dynamite
 * module — the method falls back to
 *   createPackageContext("com.google.android.gms", 3)
 * and returns that package's Context, from which the caller loads
 * com.google.android.gms.maps.internal.CreatorImpl.
 *
 * MicroG-RE is NOT installed as com.google.android.gms — its package
 * is app.revanced.android.gms — so the fallback throws
 * NameNotFoundException, the method returns null, and the MapView
 * renders black. MicroG-RE 7.0.0+ ships its own MapLibre renderer
 * (CreatorImpl, OpenFreeMap tiles) precisely for this redirect, so
 * the patch changes the fallback package string to
 * "app.revanced.android.gms". The caller then loads CreatorImpl out
 * of MicroG-RE's classloader and the map draws. Returns null (same
 * as before) when MicroG-RE is absent, so the patch is a no-op
 * without it — never a new failure.
 *
 * The fingerprint pins definingClass Lzq; + name acppncjbu + the
 * (Context, Laefjnw) signature, and the createPackageContext call
 * confirms it is the module-context loader (not another zq method).
 */
object NetMonsterMapsFingerprint : Fingerprint(
    definingClass = "Lzq;",
    name = "acppncjbu",
    returnType = "Landroid/content/Context;",
    parameters = listOf("Landroid/content/Context;", "Laefjnw;"),
    filters = listOf(
        methodCall(
            definingClass = "Landroid/content/Context;",
            name = "createPackageContext",
            parameters = listOf("Ljava/lang/String;", "I"),
            returnType = "Landroid/content/Context;",
        ),
    )
)

/**
 * zq.bmpaywgdk — the Maps renderer entry point, and the FIRST gate.
 * Before it ever reaches acppncjbu (the module-context loader), it
 * runs Lrqatw.zeulwnh (GooglePlayServicesUtil
 * .isGooglePlayServicesAvailable) with the required version 0xcc77c0
 * and throws GooglePlayServicesNotAvailableException when that
 * returns non-zero:
 *   const v0, 0xcc77c0
 *   invoke-static {p0, v0}, Lrqatw;->zeulwnh(Context, I)I
 *   move-result v0
 *   if-nez v0, :cond_3        (non-zero -> throw)
 *   const/4 v0, 0x0
 *   invoke-static {p0, v0}, Lzq;->zeulwnh(Context, Laefjnw;)Lae0;
 *
 * Lrqatw.zeulwnh looks for the literal package
 * "com.google.android.gms", which MicroG-RE does not claim (it is
 * app.revanced.android.gms). So on a de-Googled device with only
 * MicroG-RE, the gate throws BEFORE acppncjbu is ever reached —
 * which is why v1.2.0 (redirect only) did nothing: the redirect
 * never ran. The patch inserts `const/4 v0, 0x0` before the if-nez
 * so it never branches to the throw and execution proceeds to
 * load the renderer (which acppncjbu then redirects to MicroG-RE).
 * The if-nez is NOT a branch target (reached only by fall-through
 * from move-result), so inserting before it is safe.
 *
 * The fingerprint pins definingClass Lzq; + name bmpaywgdk + the
 * (Context) -> Lae0; signature, and the Lrqatw.zeulwnh call
 * confirms it is the gate method.
 */
object NetMonsterMapsGateFingerprint : Fingerprint(
    definingClass = "Lzq;",
    name = "bmpaywgdk",
    returnType = "Lae0;",
    parameters = listOf("Landroid/content/Context;"),
    filters = listOf(
        methodCall(
            definingClass = "Lrqatw;",
            name = "zeulwnh",
            parameters = listOf("Landroid/content/Context;", "I"),
            returnType = "I",
        ),
    )
)

/**
 * muov.emit — computes the premium expiry date (cpysz.bmpaywgdk). It
 * collects the expiry dates of active subscriptions, takes the max with
 * Lqqlbfq.pzkxkxo (max-or-null), and emits it via Ltiedq.emit. With no
 * active subs the max is null, so the card's expiry is null — and the
 * card renderer (byjcq.cztaxulh) only hides the PURCHASE button when
 * the expiry is non-null (if-eqz p0, :cond_d -> fkvgubg renders
 * PURCHASE; the non-null path dereferences p0, so that branch cannot
 * just be inverted).
 *
 * v1.1.1 replaced pzkxkxo with OffsetDateTime.now(), which gave a real
 * (non-null) expiry — but it showed as "today", so the card read
 * "Active till <today>". v1.1.2 instead inserts a fixed far-future
 * date (2099-12-31) just before the Ltiedq.emit call, so the card
 * reads "Active till 2099". The emit call is NOT a branch target
 * (nothing jumps to it; it is only reached by falling through from
 * the preceding iput), so inserting before it is safe — the SAI
 * lesson about addInstructions only applies before branch targets.
 */
object NetMonsterExpiryFingerprint : Fingerprint(
    definingClass = "Lmuov;",
    name = "emit",
    returnType = "Ljava/lang/Object;",
    parameters = listOf("Ljava/lang/Object;", "Llnzwbdtfp;"),
    filters = listOf(
        methodCall(
            definingClass = "Lqqlbfq;",
            name = "pzkxkxo",
            parameters = listOf("Ljava/util/ArrayList;"),
            returnType = "Ljava/lang/Comparable;",
        ),
        methodCall(
            definingClass = "Ltiedq;",
            name = "emit",
            parameters = listOf("Ljava/lang/Object;", "Llnzwbdtfp;"),
            returnType = "Ljava/lang/Object;",
        ),
    )
)
