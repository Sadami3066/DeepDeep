-keep class io.github.whywood.superpi.plugin.api.** { *; }
-keep class io.github.whywood.superpi.design.** { *; }
-keep class kotlin.** { *; }
-keep class kotlinx.** { *; }
-keep class androidx.compose.** { *; }

# AndroidX libraries plugin UIs commonly reach through Compose.
-keep class androidx.lifecycle.** { *; }
-keep class androidx.activity.** { *; }
-keep class androidx.savedstate.** { *; }
