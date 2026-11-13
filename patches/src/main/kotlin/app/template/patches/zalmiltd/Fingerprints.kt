package app.template.patches.zalmiltd

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.methodCall
import app.morphe.patcher.string
import com.android.tools.smali.dexlib2.Opcode

/**
 * Bangladesh VPN (bdvpn.zalmiltd.vpn) 4.1, versionCode 41.
 * A Kotlin white-label VPN (Hydra/Xray, OpenVPN, WireGuard, OpenConnect).
 * Premium is a Play Billing subscription (products `base_features` /
 * `full_features`, weekly/monthly/semesterly/yearly), but every gate is
 * evaluated LOCALLY -- there is no server entitlement check at connect
 * time (only `log-connect` afterwards). The subscription tier is the
 * obfuscated enum `Lkh3;`:
 *   UNKNOWN (c), NONE (d), BASIC (e), FULL (f)
 * with `d()` = "is BASIC or FULL" and `f()` = "is FULL".
 * Verified on 4.1 by hand-built .apks (JADX + Apktool re-decode of the
 * rebuilt APK, apksigner verify): kh3 returns true, the billing refresh
 * loads FULL in all three branches, the DataStore default is FULL, and
 * the free-time check returns false.
 *
 * The tier is a *value* flowing through a StateFlow, not just method
 * calls, so it must be forced at every place that produces or reads it:
 *
 * 1) `Lkh3;->d()` / `Lkh3;->f()` -- the tier predicates. They gate VIP
 *    server connections (MainFragment, ServersFragment), the "Go
 *    Premium" menu, and the premium prompts. Forcing their false-branch
 *    const to 1 makes both always true.
 *
 * 2) `Lpx0;->invokeSuspend` (case 3) -- the startup billing refresh
 *    (`kj2.g()`). It collects every purchased product id from
 *    `Purchase.getProducts()` and maps it: contains "full_features" ->
 *    FULL, contains "base_features" -> BASIC, else NONE; then saves
 *    the result to DataStore and to the in-memory tier StateFlow. The
 *    tier observer (`hv` case 7) then disables ads (`w7.f = false`)
 *    and clears the "limited" flag itself. Forcing the BASIC and NONE
 *    fallbacks to FULL makes the refresh always produce FULL.
 *
 * 3) `Lae4;-><clinit>` -- the DataStore key "current_subs" holding the
 *    persisted tier, with default NONE. Forcing the default to FULL
 *    covers a fresh install and a failed billing query (when the
 *    refresh falls back to the stored value).
 *
 * 4) `Lhn1;->d()` (static) -- "is the free-time limit active?". It
 *    reads SharedPreferences "free_time" key "limited" (default true);
 *    while limited, a Handler disconnects the VPN when the free balance
 *    runs out. Forcing it to false means the countdown never starts,
 *    even if the tier somehow ends up NONE.
 *
 * Every fingerprint pins its defining class and method name. The app is
 * heavily R8-obfuscated (single-letter classes/methods), so pinning is
 * essential -- without it the patcher could match the wrong method.
 * Note this is a different app from the `bdvpn` patch in this repo
 * (com.banglatech.bangladeshvpn 67.26): different package, different
 * version, different obfuscated class names.
 */

/**
 * `Lkh3;->f()Z` -- the FULL-tier predicate.
 *
 * Original:
 *   sget-object v0, Lkh3;->f:Lkh3;
 *   if-ne p0, v0, :cond_0
 *   const/4 p0, 0x1
 *   return p0
 *   :cond_0
 *   const/4 p0, 0x0
 *   return p0
 * The patch replaces the `const/4 p0, 0x0` at :cond_0 with 0x1, so f()
 * always returns true.
 */
object ZalmiltdIsFullFingerprint : Fingerprint(
    definingClass = "Lkh3;",
    name = "f",
    isStatic = false,
    returnType = "Z",
    parameters = listOf(),
    filters = listOf(
        fieldAccess(
            opcode = Opcode.SGET_OBJECT,
            definingClass = "Lkh3;",
            name = "f",
            type = "Lkh3;",
        ),
    )
)

/**
 * `Lkh3;->d()Z` -- the "is BASIC or FULL" predicate.
 *
 * Original:
 *   sget-object v0, Lkh3;->e:Lkh3;
 *   if-eq p0, v0, :cond_1
 *   sget-object v0, Lkh3;->f:Lkh3;
 *   if-ne p0, v0, :cond_0
 *   goto :goto_0
 *   :cond_0
 *   const/4 p0, 0x0
 *   return p0
 *   :cond_1
 *   :goto_0
 *   const/4 p0, 0x1
 *   return p0
 * The patch replaces the `const/4 p0, 0x0` at :cond_0 with 0x1, so d()
 * always returns true.
 */
object ZalmiltdIsPaidFingerprint : Fingerprint(
    definingClass = "Lkh3;",
    name = "d",
    isStatic = false,
    returnType = "Z",
    parameters = listOf(),
    filters = listOf(
        fieldAccess(
            opcode = Opcode.SGET_OBJECT,
            definingClass = "Lkh3;",
            name = "e",
            type = "Lkh3;",
        ),
        fieldAccess(
            opcode = Opcode.SGET_OBJECT,
            definingClass = "Lkh3;",
            name = "f",
            type = "Lkh3;",
        ),
    )
)

/**
 * `Lpx0;->invokeSuspend` (case 3) -- the startup billing refresh.
 *
 * Original (relevant part):
 *   ... collect Purchase.getProducts() into an ArrayList ...
 *   const-string v3, "full_features"
 *   invoke-virtual {v5, v3}, ArrayList.contains
 *   if-eqz v3, :cond_5b
 *   sget-object v3, Lkh3;->f:Lkh3;   // FULL
 *   goto :goto_3e
 *   :cond_5b
 *   const-string v3, "base_features"
 *   invoke-virtual {v5, v3}, ArrayList.contains
 *   if-eqz v3, :cond_5c
 *   sget-object v3, Lkh3;->e:Lkh3;   // BASIC
 *   goto :goto_3e
 *   :cond_5c
 *   sget-object v3, Lkh3;->d:Lkh3;   // NONE
 * The patch replaces the BASIC and NONE sgets with FULL, so the refresh
 * always resolves to FULL (the "full_features" branch already loads FULL).
 */
object ZalmiltdBillingRefreshFingerprint : Fingerprint(
    definingClass = "Lpx0;",
    name = "invokeSuspend",
    returnType = "Ljava/lang/Object;",
    parameters = listOf("Ljava/lang/Object;"),
    filters = listOf(
        methodCall(
            definingClass = "Lcom/android/billingclient/api/Purchase;",
            name = "getProducts",
            returnType = "Ljava/util/List;",
        ),
        string("full_features"),
        string("base_features"),
    )
)

/**
 * `Lae4;-><clinit>` -- the DataStore key "current_subs" holding the
 * persisted `Lkh3;` tier, with default NONE.
 *
 * Original:
 *   new-instance v0, Lae4;
 *   const-string v1, "current_subs"
 *   sget-object v2, Lkh3;->d:Lkh3;   // NONE (default)
 *   const-class v3, Lkh3;
 *   invoke-direct {v0, v1, v3, v2}, Lyv;-><init>(...)V
 * The patch replaces the `sget-object v2, Lkh3;->d` with
 * `sget-object v2, Lkh3;->f`, so the default tier is FULL.
 */
object ZalmiltdDefaultTierFingerprint : Fingerprint(
    definingClass = "Lae4;",
    name = "<clinit>",
    returnType = "V",
    parameters = listOf(),
    filters = listOf(
        string("current_subs"),
        fieldAccess(
            opcode = Opcode.SGET_OBJECT,
            definingClass = "Lkh3;",
            name = "d",
            type = "Lkh3;",
        ),
    )
)

/**
 * `Lhn1;->d()Z` (static) -- "is the free-time limit active?".
 *
 * Original:
 *   invoke-static {}, Lhn1;->e()Landroid/content/SharedPreferences;
 *   move-result-object v0
 *   const-string v1, "limited"
 *   const/4 v2, 0x1
 *   invoke-interface {v0, v1, v2},
 *       Landroid/content/SharedPreferences;->getBoolean(Ljava/lang/String;Z)Z
 *   move-result v0
 *   return v0
 * The patch makes it always return false (see the patch file: this one
 * uses returnEarly, because there is no const-0 to replace and the
 * stored "limited" value from an earlier unpatched run must be ignored).
 */
object ZalmiltdFreeTimeLimitedFingerprint : Fingerprint(
    definingClass = "Lhn1;",
    name = "d",
    isStatic = true,
    returnType = "Z",
    parameters = listOf(),
    filters = listOf(
        string("limited"),
        methodCall(
            definingClass = "Landroid/content/SharedPreferences;",
            name = "getBoolean",
            returnType = "Z",
        ),
    )
)
