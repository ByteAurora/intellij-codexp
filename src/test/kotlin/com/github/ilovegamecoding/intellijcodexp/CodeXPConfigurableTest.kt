package com.github.ilovegamecoding.intellijcodexp

import com.github.ilovegamecoding.intellijcodexp.models.CodeXPConfiguration
import com.github.ilovegamecoding.intellijcodexp.presentation.settings.CodeXPConfigurable
import com.github.ilovegamecoding.intellijcodexp.services.CodeXPService
import com.intellij.openapi.application.ApplicationManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.awt.Component
import java.awt.Container
import javax.swing.JCheckBox
import javax.swing.JComboBox

class CodeXPConfigurableTest : BasePlatformTestCase() {
    fun testSettingsLifecycle() {
        val service = ApplicationManager.getApplication().getService(CodeXPService::class.java)
        val original = service.state.codeXPConfiguration.copy()
        val configurable = CodeXPConfigurable()
        try {
            service.updateConfiguration(CodeXPConfiguration())
            assertFalse(configurable.isModified())
            val root = configurable.createComponent()
            assertFalse(configurable.isModified())

            val controls = descendants(root).toList()
            val gainedXP =
                controls.filterIsInstance<JCheckBox>().single {
                    it.text == CodeXPBundle.message("TEXT_SHOW_GAINED_XP")
                }
            val position = controls.filterIsInstance<JComboBox<*>>().last()
            assertTrue(position.isEnabled)
            gainedXP.doClick()
            assertFalse(position.isEnabled)
            assertTrue(configurable.isModified())
            assertTrue(service.state.codeXPConfiguration.showGainedXP)

            configurable.reset()
            assertTrue(gainedXP.isSelected)
            assertTrue(position.isEnabled)
            assertFalse(configurable.isModified())

            gainedXP.doClick()
            configurable.apply()
            assertFalse(service.state.codeXPConfiguration.showGainedXP)
            assertFalse(configurable.isModified())

            configurable.disposeUIResources()
            assertFalse(configurable.isModified())
            configurable.createComponent()
            assertFalse(configurable.isModified())
        } finally {
            configurable.disposeUIResources()
            service.updateConfiguration(original)
        }
    }

    private fun descendants(component: Component): Sequence<Component> =
        sequence {
            yield(component)
            if (component is Container) {
                component.components.forEach { yieldAll(descendants(it)) }
            }
        }
}
