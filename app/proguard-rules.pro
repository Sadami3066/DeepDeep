-keep class io.github.whywood.deepdeep.plugin.api.** { *; }
-keep class io.github.whywood.deepdeep.design.** { *; }
-keep class org.lsposed.hiddenapibypass.** { *; }
-keep class com.github.muntashirakon.adb.** { *; }
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.internal.mlkit_vision_barcode.** { *; }
-keep class com.google.android.gms.internal.mlkit_vision_common.** { *; }
-dontwarn com.google.mlkit.**

-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**
