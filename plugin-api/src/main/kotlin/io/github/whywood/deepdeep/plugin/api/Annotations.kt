package io.github.whywood.deepdeep.plugin.api

@RequiresOptIn(
    level = RequiresOptIn.Level.WARNING,
    message = "Experimental plugin API: it may change before it becomes stable.",
)
@Retention(AnnotationRetention.BINARY)
@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY,
    AnnotationTarget.CONSTRUCTOR,
)
public annotation class PluginExperimentalApi
