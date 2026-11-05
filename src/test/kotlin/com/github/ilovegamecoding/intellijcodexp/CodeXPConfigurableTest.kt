package com.github.ilovegamecoding.intellijcodexp

import com.github.ilovegamecoding.intellijcodexp.enums.EffectStyle
import com.github.ilovegamecoding.intellijcodexp.enums.PositionToDisplayGainedXP
import com.github.ilovegamecoding.intellijcodexp.models.CodeXPConfiguration
import com.github.ilovegamecoding.intellijcodexp.presentation.settings.CodeXPConfigurable
import com.github.ilovegamecoding.intellijcodexp.services.CodeXPService
import com.intellij.ide.ui.LafManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.xmlb.XmlSerializer
import org.jdom.Element
import java.awt.Component
import java.awt.Container
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JComboBox
import javax.swing.JEditorPane
import javax.swing.JLabel
import javax.swing.JList

class CodeXPConfigurableTest : BasePlatformTestCase() {
    fun testHelpWrapsInsideTheSettingsPageAndPositionsUseReadableLabels() {
        val configurable = CodeXPConfigurable()
        val manager = LafManager.getInstance()
        val previousTheme = manager.currentUIThemeLookAndFeel
        try {
            manager.defaultDarkLaf?.let { manager.setCurrentLookAndFeel(it, false) }
            val heights = mutableListOf<Int>()
            for (width in listOf(700, 430)) {
                val root = configurable.createComponent()
                val hints = descendants(root).filterIsInstance<JEditorPane>().toList()
                assertEquals(5, hints.size)
                root.setSize(width, 1200)
                repeat(4) { layout(root) }
                assertTrue("Settings require horizontal scrolling: ${root.minimumSize}", root.minimumSize.width <= width)
                hints.forEach { hint ->
                    assertTrue(hint.width > 0 && hint.height > 0)
                    assertTrue(hint.x + hint.width <= hint.parent.width)
                    assertFalse(hint.isEditable)
                }
                heights.add(hints.sumOf { it.height })
                val image = BufferedImage(width, root.preferredSize.height, BufferedImage.TYPE_INT_ARGB)
                image.createGraphics().let { g ->
                    root.paint(g)
                    g.dispose()
                }
                val file = File("build/reports/ui-release-review/settings-$width.png")
                file.parentFile.mkdirs()
                ImageIO.write(image, "png", file)
            }
            assertTrue("Help should wrap into more lines on narrow pages", heights.last() > heights.first())
            val root = configurable.createComponent()
            val position = descendants(root).filterIsInstance<JComboBox<String>>().single { it.getItemAt(0) == PositionToDisplayGainedXP.TOP.name }
            val list = JList<String>()
            PositionToDisplayGainedXP.entries.forEach { value ->
                val rendered = position.renderer.getListCellRendererComponent(list, value.name, value.ordinal, false, false) as JLabel
                assertEquals(CodeXPBundle.message("settings.caret.position.${value.name.lowercase()}"), rendered.text)
                assertFalse(rendered.text.contains('_'))
            }
        } finally {
            configurable.disposeUIResources()
            if (previousTheme != null) manager.setCurrentLookAndFeel(previousTheme, false)
        }
    }

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
            val position =
                controls.filterIsInstance<JComboBox<*>>().single {
                    it.getItemAt(0) ==
                        com.github.ilovegamecoding.intellijcodexp.enums.PositionToDisplayGainedXP.entries
                            .first()
                            .name
                }
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

    fun testEffectSettingsApplyResetSerializationAndFallback() {
        val service = ApplicationManager.getApplication().getService(CodeXPService::class.java)
        val original = service.state.codeXPConfiguration.copy()
        val configurable = CodeXPConfigurable()
        try {
            val legacy = XmlSerializer.deserialize(Element("CodeXPConfiguration"), CodeXPConfiguration::class.java)
            assertEquals(EffectStyle.CELEBRATORY.storedValue, legacy.effectStyle)
            assertFalse(legacy.reducedMotion)
            legacy.effectStyle = "future-style"
            service.updateConfiguration(legacy)
            assertEquals(EffectStyle.CELEBRATORY.storedValue, service.state.codeXPConfiguration.effectStyle)
            val root = configurable.createComponent()
            val controls = descendants(root).toList()
            val style = controls.filterIsInstance<JComboBox<*>>().single { it.itemCount == EffectStyle.entries.size && it.getItemAt(0) == CodeXPBundle.message("settings.effects.style.subtle") }
            val reduced = controls.filterIsInstance<JCheckBox>().single { it.text == CodeXPBundle.message("settings.effects.reduced.motion") }
            assertEquals(1, style.selectedIndex)
            assertFalse(configurable.isModified())
            style.selectedIndex = 0
            reduced.doClick()
            assertTrue(configurable.isModified())
            configurable.reset()
            assertEquals(1, style.selectedIndex)
            assertFalse(reduced.isSelected)
            style.selectedIndex = 0
            reduced.doClick()
            configurable.apply()
            assertFalse(configurable.isModified())
            val saved = service.state.codeXPConfiguration
            assertEquals(EffectStyle.SUBTLE.storedValue, saved.effectStyle)
            assertTrue(saved.reducedMotion)
            val reloaded = XmlSerializer.deserialize(XmlSerializer.serialize(saved), CodeXPConfiguration::class.java)
            assertEquals(saved, reloaded)
            service.updateConfiguration(reloaded)
            configurable.disposeUIResources()
            configurable.createComponent()
            assertFalse(configurable.isModified())
        } finally {
            configurable.disposeUIResources()
            service.updateConfiguration(original)
        }
    }

    fun testEveryMiniCrestSizeCanBeAppliedAndReopened() {
        val service = ApplicationManager.getApplication().getService(CodeXPService::class.java)
        val original = service.state.codeXPConfiguration.copy()
        val configurable = CodeXPConfigurable()
        try {
            service.updateConfiguration(original.copy(miniCrestSize = 96))
            val sizes = descendants(configurable.createComponent()).filterIsInstance<JComboBox<*>>().single { it.getItemAt(0) == "96 px" }
            val expected = listOf(96, 128, 164, 256)
            assertEquals(expected.map { "$it px" }, (0 until sizes.itemCount).map { sizes.getItemAt(it) })
            for (size in expected.reversed()) {
                sizes.selectedItem = "$size px"
                configurable.apply()
                assertEquals(size, service.state.codeXPConfiguration.miniCrestSize)
                configurable.reset()
                assertEquals("$size px", sizes.selectedItem)
                assertFalse(configurable.isModified())
            }
        } finally {
            configurable.disposeUIResources()
            service.updateConfiguration(original)
        }
    }

    fun testRestoreMiniPositionIsStagedAndKeepsOtherUnsavedSettings() {
        val service = ApplicationManager.getApplication().getService(CodeXPService::class.java)
        val original = service.state.codeXPConfiguration.copy()
        val configurable = CodeXPConfigurable()
        try {
            val initial = CodeXPConfiguration(miniCrestEnabled = false, miniCrestX = .2, miniCrestY = .4, miniCrestWindowAnchor = true)
            service.updateConfiguration(initial.copy())
            val controls = descendants(configurable.createComponent()).toList()
            val restore = controls.filterIsInstance<JButton>().single { it.text == CodeXPBundle.message("settings.mini.restore") }
            val reduced = controls.filterIsInstance<JCheckBox>().single { it.text == CodeXPBundle.message("settings.effects.reduced.motion") }
            val style = controls.filterIsInstance<JComboBox<*>>().single { it.getItemAt(0) == CodeXPBundle.message("settings.effects.style.subtle") }
            style.selectedIndex = 0
            reduced.doClick()
            restore.doClick()
            assertEquals(initial, service.state.codeXPConfiguration)
            assertTrue(reduced.isSelected)
            assertEquals(0, style.selectedIndex)
            assertTrue(configurable.isModified())
            configurable.reset() // Cancel/reopen discards the staged restore.
            assertEquals(initial, service.state.codeXPConfiguration)
            assertFalse(configurable.isModified())
            style.selectedIndex = 0
            reduced.doClick()
            restore.doClick()
            configurable.apply()
            val saved = service.state.codeXPConfiguration
            assertTrue(saved.reducedMotion)
            assertEquals(EffectStyle.SUBTLE.storedValue, saved.effectStyle)
            assertTrue(saved.miniCrestEnabled)
            assertFalse(saved.miniCrestPinned)
            assertFalse(saved.miniCrestCollapsed)
            assertFalse(saved.miniCrestWindowAnchor)
            assertEquals(1.0, saved.miniCrestX)
            assertEquals(1.0, saved.miniCrestY)
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

    private fun layout(container: Container) {
        container.invalidate()
        container.doLayout()
        container.components.filterIsInstance<Container>().forEach(::layout)
    }
}
