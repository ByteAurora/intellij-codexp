package com.github.ilovegamecoding.intellijcodexp

import com.intellij.DynamicBundle
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ApplicationManager
import com.intellij.util.DefaultBundleService
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.PropertyKey
import java.util.Locale
import java.util.ResourceBundle

@NonNls
private const val BUNDLE = "messages.CodeXPBundle"

object CodeXPBundle : DynamicBundle(BUNDLE) {
    override fun findBundle(
        pathToBundle: String,
        loader: ClassLoader,
        control: ResourceBundle.Control,
    ): ResourceBundle {
        if (ApplicationManager.getApplication() != null && ApplicationInfo.getInstance().build.baselineVersion >= 241) {
            return super.findBundle(pathToBundle, loader, control)
        }
        // 2023.3 uses the JVM locale for plugin-owned resources, even when the IDE uses another language.
        val locale = if (DefaultBundleService.isDefaultBundle()) Locale.ROOT else getLocale()
        return ResourceBundle.getBundle(pathToBundle, locale, loader, ResourceBundle.Control.getNoFallbackControl(ResourceBundle.Control.FORMAT_PROPERTIES))
    }

    @Suppress("SpreadOperator")
    @JvmStatic
    fun message(
        @PropertyKey(resourceBundle = BUNDLE) key: String,
        vararg params: Any,
    ) = getMessage(key, *params)
}
