-keep class io.github.whywood.deepdeep.plugin.api.** { *; }
-keep class io.github.whywood.deepdeep.design.** { *; }
-keep class kotlin.** { *; }
-keep class kotlinx.** { *; }
-keep class androidx.compose.** { *; }

# AndroidX libraries plugin UIs commonly reach through Compose.
-keep class androidx.lifecycle.** { *; }
-keep class androidx.activity.** { *; }
-keep class androidx.savedstate.** { *; }
