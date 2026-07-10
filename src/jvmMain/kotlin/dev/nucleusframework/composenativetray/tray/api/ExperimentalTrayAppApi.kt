package dev.nucleusframework.composenativetray.tray.api

/**
 * Marks the TrayApp API as experimental. This API is subject to change and may
 * be modified or removed in future releases without prior notice.
 *
 * Consumers must explicitly opt in:
 * - @OptIn(ExperimentalTrayAppApi::class)
 * or use the compiler flag:
 * - -Xopt-in=dev.nucleusframework.composenativetray.tray.api.ExperimentalTrayAppApi
 */
@RequiresOptIn(
    message = "TrayApp is experimental and may change or be removed without notice.",
    level = RequiresOptIn.Level.WARNING,
)
@Retention(AnnotationRetention.BINARY)
@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY,
    AnnotationTarget.TYPEALIAS,
)
annotation class ExperimentalTrayAppApi
