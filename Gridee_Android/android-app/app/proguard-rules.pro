# Gridee R8/ProGuard rules.
#
# `minifyEnabled` is currently false for release, so nothing here is active yet. They are kept
# correct and in place so turning shrinking on is a one-line change rather than a debugging
# session — the failures these prevent (missing mediation adapters, Gson fields renamed to
# single letters) are all silent at runtime.

# ---------------------------------------------------------------------------
# Google Mobile Ads + mediation adapters
# ---------------------------------------------------------------------------
# Adapters are resolved by class name from the ad response, so nothing referenced only by
# reflection may be renamed or stripped.
-keep class com.google.android.gms.ads.** { *; }
-keep class com.google.ads.mediation.** { *; }
-keep public class * extends com.google.android.gms.ads.mediation.Adapter { *; }
-keep public class * implements com.google.android.gms.ads.mediation.MediationAdapter { *; }
-keep class com.google.android.gms.ads.initialization.** { *; }
-dontwarn com.google.android.gms.ads.**
-dontwarn com.google.ads.mediation.**

# Meta Audience Network (bundled via com.google.ads.mediation:facebook)
-keep class com.facebook.ads.** { *; }
-dontwarn com.facebook.ads.**

# Unity Ads (SDK + adapter are both explicit Gradle dependencies)
-keep class com.unity3d.ads.** { *; }
-dontwarn com.unity3d.ads.**

# User Messaging Platform (UMP consent)
-keep class com.google.android.ump.** { *; }
-dontwarn com.google.android.ump.**

# ---------------------------------------------------------------------------
# Cashfree PG checkout
# ---------------------------------------------------------------------------
# The SDK reflects over its own model classes and drives checkout through a WebView bridge.
-keep class com.cashfree.pg.** { *; }
-dontwarn com.cashfree.pg.**
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Apache Commons, pulled in transitively by Cashfree.
-dontwarn org.apache.commons.**
-dontwarn java.awt.**

# ---------------------------------------------------------------------------
# Gson-backed API models
# ---------------------------------------------------------------------------
# Field names are the wire format; renaming them silently breaks every response that does not
# carry an explicit @SerializedName.
-keep class com.gridee.parking.data.model.** { *; }
-keepattributes Signature, *Annotation*, InnerClasses, EnclosingMethod
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
-dontwarn sun.misc.**

# ---------------------------------------------------------------------------
# Retrofit + OkHttp
# ---------------------------------------------------------------------------
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn retrofit2.**
