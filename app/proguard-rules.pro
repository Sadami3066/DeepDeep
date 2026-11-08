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

# Wireless-debug shell daemon is launched via app_process reflection; R8 must keep the entry class and its main.
-keep class io.github.whywood.deepdeep.adb.ShellDaemon { *; }
-keep class io.github.whywood.deepdeep.vscreen.VirtualScreenHelperMain { *; }
