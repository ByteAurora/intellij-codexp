package com.github.ilovegamecoding.intellijcodexp.collection

import com.github.ilovegamecoding.intellijcodexp.CodeXPBundle
import com.github.ilovegamecoding.intellijcodexp.domain.CodeXPGrowthPolicy
import com.github.ilovegamecoding.intellijcodexp.enums.Event
import com.github.ilovegamecoding.intellijcodexp.models.CodeXPConfiguration
import com.github.ilovegamecoding.intellijcodexp.models.CodeXPState
import com.github.ilovegamecoding.intellijcodexp.presentation.collection.CollectionAnimationClock
import com.github.ilovegamecoding.intellijcodexp.presentation.collection.MiniCrestContextGesture
import com.github.ilovegamecoding.intellijcodexp.presentation.collection.MiniCrestController
import com.github.ilovegamecoding.intellijcodexp.presentation.collection.MiniCrestSurface
import com.github.ilovegamecoding.intellijcodexp.presentation.collection.lookupCoversMiniCrest
import com.github.ilovegamecoding.intellijcodexp.presentation.collection.miniCrestAnchor
import com.github.ilovegamecoding.intellijcodexp.presentation.collection.miniCrestBounds
import com.github.ilovegamecoding.intellijcodexp.presentation.collection.miniCrestWindowActive
import com.github.ilovegamecoding.intellijcodexp.presentation.overlay.XpGainEffectRenderer
import com.github.ilovegamecoding.intellijcodexp.services.CodeXPService
import com.github.ilovegamecoding.intellijcodexp.services.MemoryCollectionPersistence
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.awt.Dialog
import java.awt.Frame
import java.awt.KeyboardFocusManager
import java.awt.Point
import java.awt.Rectangle
import java.awt.Toolkit
import java.awt.Window
import java.awt.event.MouseEvent
import javax.swing.JLayeredPane
import javax.swing.JMenuItem
import javax.swing.JPanel
import javax.swing.SwingUtilities

class MiniCrestTest : BasePlatformTestCase() {
    fun testSettingsAndNestedDialogsKeepTheCrestActiveInTheOwningIde() {
        var ownerActive = true
        val owner =
            object : Frame() {
                override fun isActive() = ownerActive
            }
        val settings =
            object : Dialog(owner) {
                override fun isActive() = true
            }.apply { modalityType = Dialog.ModalityType.APPLICATION_MODAL }
        val nested =
            object : Dialog(settings) {
                override fun isActive() = true
            }
        val popup = Window(nested)
        val other = Frame()
        val unrelated = Dialog(other)
        try {
            assertTrue(miniCrestWindowActive(owner, owner))
            ownerActive = false
            assertTrue(miniCrestWindowActive(owner, settings))
            assertTrue(miniCrestWindowActive(owner, nested))
            assertTrue(miniCrestWindowActive(owner, popup))
            ownerActive = true
            assertTrue(miniCrestWindowActive(owner, owner))
            assertFalse(miniCrestWindowActive(other, settings))
            assertFalse(miniCrestWindowActive(owner, other))
            assertFalse(miniCrestWindowActive(owner, unrelated))
            ownerActive = false
            assertFalse(miniCrestWindowActive(owner, null))
        } finally {
            owner.dispose()
            other.dispose()
        }
    }

    fun testInactiveOwnedWindowsDoNotKeepTheCrestVisibleAfterAnAppSwitch() {
        val owner = Frame()
        val settings = Dialog(owner)
        val nested = Dialog(settings)
        try {
            for (focused in listOf(owner, settings, nested, null)) {
                assertFalse(miniCrestWindowActive(owner, focused))
            }
        } finally {
            owner.dispose()
        }
    }

    fun testMinimizedWindowStaysInactiveEvenWithStaleOwnedDialogFocus() {
        val owner =
            object : Frame() {
                override fun getExtendedState() = ICONIFIED

                override fun isActive() = true
            }
        val settings = Dialog(owner)
        try {
            assertFalse(miniCrestWindowActive(owner, settings))
            assertFalse(miniCrestWindowActive(owner, owner))
            assertFalse(miniCrestWindowActive(owner, null))
        } finally {
            owner.dispose()
        }
    }

    fun testTransientNullFocusUsesOwnerActivationButNeverOverridesAnotherProject() {
        val owner =
            object : Frame() {
                override fun isActive() = true
            }
        val other = Frame()
        try {
            assertTrue(miniCrestWindowActive(owner, null))
            assertFalse(miniCrestWindowActive(owner, other))
        } finally {
            owner.dispose()
            other.dispose()
        }
    }

    fun testTypingLookupOnlyDimsCrestWhenItsVisiblePopupActuallyOverlaps() {
        val pane = JLayeredPane().apply { setSize(1000, 700) }
        val crest = Rectangle(750, 430, 164, 164)
        val host = JPanel(null).apply { setBounds(500, 300, 460, 380) }
        pane.add(host, JLayeredPane.POPUP_LAYER)
        var showing = false
        val popup =
            object : JPanel() {
                override fun isShowing() = showing
            }.apply { setBounds(200, 100, 200, 180) }
        host.add(popup)
        repeat(20) {
            assertFalse(lookupCoversMiniCrest(null, pane, crest))
            assertFalse(lookupCoversMiniCrest(popup, pane, crest))
        }
        showing = true
        assertTrue(lookupCoversMiniCrest(popup, pane, crest))
        popup.setLocation(0, 0)
        assertFalse(lookupCoversMiniCrest(popup, pane, crest))
        popup.setLocation(200, 100)
        assertFalse(lookupCoversMiniCrest(popup, JLayeredPane(), crest))
        host.remove(popup)
        assertFalse(lookupCoversMiniCrest(popup, pane, crest))
    }

    fun testArtworkAlwaysAcceptsDraggingWhileTransparentCornersPassThrough() {
        val layer = JLayeredPane().apply { setSize(400, 300) }
        val editor = JPanel().apply { setBounds(0, 0, 400, 300) }
        val mini =
            MiniCrestSurface().apply {
                setBounds(200, 100, 96, 96)
                doLayout()
            }
        layer.add(editor, JLayeredPane.DEFAULT_LAYER, 0)
        layer.add(mini, JLayeredPane.PALETTE_LAYER, 0)
        assertSame(mini, SwingUtilities.getDeepestComponentAt(layer, 236, 155))
        assertSame(editor, SwingUtilities.getDeepestComponentAt(layer, 294, 101))
        mini.visibilityAlpha = .32f
        assertSame(mini, SwingUtilities.getDeepestComponentAt(layer, 248, 148))
        assertSame(editor, SwingUtilities.getDeepestComponentAt(layer, 294, 101))
        assertFalse(mini.isOpaque)
        assertFalse(mini.isFocusable)
        assertFalse(mini.control.isFocusable)
        assertFalse(mini.control.contains(48, 48))
    }

    fun testPopupControlsStayAboveTheVisibleCrestAndDraggingWorksAfterDismissal() {
        val layer = JLayeredPane().apply { setSize(400, 300) }
        val mini = MiniCrestSurface().apply { setBounds(200, 100, 96, 96) }
        val menuItem = JMenuItem("Editor action").apply { setBounds(220, 120, 100, 40) }
        layer.add(mini, JLayeredPane.PALETTE_LAYER, 0)
        layer.add(menuItem, JLayeredPane.POPUP_LAYER, 0)
        assertTrue(mini.isVisible)
        assertSame(menuItem, SwingUtilities.getDeepestComponentAt(layer, 248, 148))
        layer.remove(menuItem)
        assertTrue(mini.isVisible)
        assertSame(mini, SwingUtilities.getDeepestComponentAt(layer, 248, 148))
    }

    fun testLegacyPinAndCollapseAreClearedWithoutChangingSizePositionOrVisibility() {
        val service = CodeXPService(MemoryCollectionPersistence())
        val legacy = CodeXPConfiguration(miniCrestPinned = true, miniCrestCollapsed = true, miniCrestSize = 256, miniCrestX = .3, miniCrestY = .6, miniCrestEnabled = false, miniCrestWindowAnchor = true)
        val expected = legacy.copy(miniCrestPinned = false, miniCrestCollapsed = false)
        try {
            assertFalse(CodeXPConfiguration().miniCrestPinned)
            service.loadState(CodeXPState(codeXPConfiguration = legacy.copy()))
            assertEquals(expected, service.state.codeXPConfiguration)
            service.updateConfiguration(legacy.copy())
            assertEquals(expected, service.state.codeXPConfiguration)
            val window = Rectangle(0, 0, 1000, 800)
            assertEquals(miniCrestBounds(window, expected, 1.0), miniCrestBounds(window, legacy, 1.0))
        } finally {
            Disposer.dispose(service)
        }
    }

    fun testContextMenuOnlyOffersSizesAndVisibility() {
        val service = CodeXPService(MemoryCollectionPersistence())
        service.noStateLoaded()
        val mini = MiniCrestController(project, service) { _, _ -> false }
        try {
            assertEquals(
                listOf(CodeXPBundle.message("mini.size.current", 96), CodeXPBundle.message("mini.size", 128), CodeXPBundle.message("mini.size", 164), CodeXPBundle.message("mini.size", 256), CodeXPBundle.message("mini.hide")),
                mini.contextActions().keys.toList(),
            )
            mini.contextActions().getValue(CodeXPBundle.message("mini.size", 128)).invoke()
            assertEquals(128, service.state.codeXPConfiguration.miniCrestSize)
            mini.contextActions().getValue(CodeXPBundle.message("mini.hide")).invoke()
            assertFalse(service.state.codeXPConfiguration.miniCrestEnabled)
            mini.contextActions().getValue(CodeXPBundle.message("mini.show")).invoke()
            assertTrue(service.state.codeXPConfiguration.miniCrestEnabled)
            assertFalse(service.state.codeXPConfiguration.miniCrestPinned)
            assertFalse(service.state.codeXPConfiguration.miniCrestCollapsed)
        } finally {
            Disposer.dispose(mini)
            Disposer.dispose(service)
        }
    }

    fun testOnlyContextClicksOnTheCrestOpenTheMenuOnce() {
        val surface = MiniCrestSurface().apply { setSize(256, 256) }
        val opened = mutableListOf<Point>()
        val gesture = MiniCrestContextGesture({ it.point.takeIf(surface::onArtwork) }, { opened.add(it) })

        fun click(
            button: Int,
            point: Point,
            triggerOnPress: Boolean = false,
            triggerOnRelease: Boolean = false,
        ): Boolean {
            fun event(
                id: Int,
                trigger: Boolean,
            ) = MouseEvent(surface, id, 0L, 0, point.x, point.y, 1, trigger, button)
            val down = event(MouseEvent.MOUSE_PRESSED, triggerOnPress)
            val up = event(MouseEvent.MOUSE_RELEASED, triggerOnRelease)
            gesture.mousePressed(down)
            gesture.mouseReleased(up)
            assertEquals(down.isConsumed, up.isConsumed)
            return down.isConsumed
        }
        val center = Point(128, 128)
        assertFalse(click(MouseEvent.BUTTON1, center))
        assertFalse(click(MouseEvent.BUTTON3, Point(1, 1)))
        assertTrue(click(MouseEvent.BUTTON3, center, triggerOnRelease = true))
        assertTrue(click(MouseEvent.BUTTON1, center, triggerOnPress = true))
        assertEquals(listOf(center, center), opened)
    }

    fun testControlSupportsButtonAndAccessibilityPressWithoutStealingFocus() {
        val surface = MiniCrestSurface()
        var presses = 0
        surface.onControlRequested = { presses++ }
        surface.control.doClick()
        assertEquals(1, presses)
        assertTrue(
            surface.control.accessibleContext.accessibleAction
                .doAccessibleAction(0),
        )
        com.intellij.util.ui.UIUtil
            .dispatchAllInvocationEvents()
        assertEquals(2, presses)
        assertFalse(surface.control.isFocusable)
    }

    fun testBoundsAndDragAnchorsIgnoreLegacyCollapseAndClampAfterResizeAndDpiChanges() {
        for (scale in listOf(.75, 1.0, 1.5, 2.0, 3.0)) {
            for (size in listOf(96, 128, 164, 256)) {
                for (collapsed in listOf(false, true)) {
                    val config = CodeXPConfiguration(miniCrestSize = size, miniCrestCollapsed = collapsed, miniCrestWindowAnchor = true)
                    for (client in listOf(Rectangle(20, 40, 1000, 600), Rectangle(40, 50, 80, 65), Rectangle(0, 0, 12, 12))) {
                        for (position in listOf(-10.0, 0.0, .35, 1.0, 9.0, Double.NaN)) {
                            val bounds = miniCrestBounds(client, config.copy(miniCrestX = position, miniCrestY = position), scale)
                            assertTrue("$bounds outside $client", client.contains(bounds))
                            assertEquals(bounds.width, bounds.height)
                        }
                        for (x in listOf(-9999, 9999)) {
                            for (y in listOf(-9999, 9999)) {
                                val anchor = miniCrestAnchor(Point(x, y), client, config, scale)
                                val bounds = miniCrestBounds(client, config.copy(miniCrestX = anchor.first, miniCrestY = anchor.second), scale)
                                assertTrue(client.contains(bounds))
                                assertEquals(if (x < 0) client.x else client.x + client.width - bounds.width, bounds.x)
                                assertEquals(if (y < 0) client.y else client.y + client.height - bounds.height, bounds.y)
                            }
                        }
                    }
                }
            }
        }
    }

    fun testDefaultAndLegacyAnchorsKeepInsetsUntilDragged() {
        val window = Rectangle(0, 0, 1000, 800)
        val defaults = CodeXPConfiguration()
        assertEquals(Rectangle(856, 656, 96, 96), miniCrestBounds(window, defaults, 1.0))
        assertEquals(Point(48, 60), miniCrestBounds(window, defaults.copy(miniCrestX = 0.0, miniCrestY = 0.0), 1.0).location)
        val previous = defaults.copy(miniCrestX = .25, miniCrestY = .5, miniCrestPinned = false)
        val oldPosition = miniCrestBounds(window, previous, 1.0).location
        assertEquals(Point(250, 358), oldPosition)
        assertEquals(Rectangle(96, 48, 176, 176), miniCrestBounds(Rectangle(0, 0, 320, 260), defaults.copy(miniCrestSize = 256), 1.0))
        val target = Point(oldPosition.x + 3, oldPosition.y - 2)
        val (x, y) = miniCrestAnchor(target, window, previous, 1.0)
        val dragged = previous.copy(miniCrestX = x, miniCrestY = y, miniCrestWindowAnchor = true)
        assertEquals(target, miniCrestBounds(window, dragged, 1.0).location)
        assertEquals(target, miniCrestBounds(window, dragged.copy(miniCrestPinned = true), 1.0).location)
    }

    fun testManualEdgeAnchorPersistsAcrossWindowAndDisplayChanges() {
        val config = CodeXPConfiguration(miniCrestSize = 256, miniCrestX = 1.0, miniCrestY = 0.0, miniCrestWindowAnchor = true)
        for (window in listOf(Rectangle(0, 0, 1700, 950), Rectangle(0, 0, 110, 80))) {
            for (scale in listOf(1.0, 1.5, 2.0)) {
                for (collapsed in listOf(false, true)) {
                    val bounds = miniCrestBounds(window, config.copy(miniCrestCollapsed = collapsed), scale)
                    assertTrue(window.contains(bounds))
                    assertEquals(window.width, bounds.x + bounds.width)
                    assertEquals(0, bounds.y)
                }
            }
        }
    }

    fun testDistantXpStaysVisibleAndOnlySameWindowIntersectionsHideTheCrest() {
        val pane =
            object : JLayeredPane() {
                override fun isShowing() = true
            }.apply { setSize(1000, 700) }
        val otherWindow =
            object : JLayeredPane() {
                override fun isShowing() = true
            }.apply { setSize(1000, 700) }
        val renderer = XpGainEffectRenderer()
        try {
            renderer.renderWindow(123, pane, CodeXPConfiguration())
            assertEquals(1, renderer.activeCount)
            assertFalse(renderer.overlaps(pane, Rectangle(880, 550, 96, 116)))
            assertTrue(renderer.overlaps(pane, Rectangle(450, 610, 150, 80)))
            assertFalse(renderer.overlaps(otherWindow, Rectangle(450, 610, 150, 80)))
            renderer.clear()
            assertFalse(renderer.overlaps(pane, Rectangle(450, 610, 150, 80)))
        } finally {
            renderer.dispose()
        }
    }

    fun testProjectSurfaceCanStartBeforeFrameExistsAndDisposesAllGlobalListeners() {
        val service = CodeXPService(MemoryCollectionPersistence())
        service.noStateLoaded()
        val toolkit = Toolkit.getDefaultToolkit()
        val before = toolkit.awtEventListeners.size
        val focus = KeyboardFocusManager.getCurrentKeyboardFocusManager()
        val focusOwnerListeners = focus.getPropertyChangeListeners("focusOwner").size
        val focusedWindowListeners = focus.getPropertyChangeListeners("focusedWindow").size
        val clocks = CollectionAnimationClock.subscriberCount
        val mini = MiniCrestController(project, service) { _, _ -> false }
        try {
            assertEquals(before + 1, toolkit.awtEventListeners.size)
            assertEquals(focusOwnerListeners + 1, focus.getPropertyChangeListeners("focusOwner").size)
            assertEquals(focusedWindowListeners + 1, focus.getPropertyChangeListeners("focusedWindow").size)
            service.updateConfiguration(service.state.codeXPConfiguration.copy(reducedMotion = true))
            assertEquals(clocks, CollectionAnimationClock.subscriberCount)
        } finally {
            Disposer.dispose(mini)
            Disposer.dispose(service)
        }
        assertEquals(before, toolkit.awtEventListeners.size)
        assertEquals(focusOwnerListeners, focus.getPropertyChangeListeners("focusOwner").size)
        assertEquals(focusedWindowListeners, focus.getPropertyChangeListeners("focusedWindow").size)
        assertEquals(clocks, CollectionAnimationClock.subscriberCount)
        mini.scheduleRefresh()
    }

    fun testLevelFollowsSharedProgressEvenWithoutAnOriginWindowOrNotifications() {
        val service = CodeXPService(MemoryCollectionPersistence())
        val config = CodeXPConfiguration(showGainedXP = false, showLevelUpNotification = false, reducedMotion = true)
        service.loadState(CodeXPState(codeXPConfiguration = config))
        service.state.xp = CodeXPGrowthPolicy.xpAtLevel(10) - 1
        val mini = MiniCrestController(project, service) { _, _ -> false }
        try {
            assertEquals(9, mini.surface.level)
            service.eventOccurred(Event.TYPING, null)
            assertEquals(10, mini.surface.level)
            assertEquals(CodeXPBundle.message("mini.accessible", 10), mini.surface.accessibleContext.accessibleName)
            service.loadState(CodeXPState(xp = CodeXPGrowthPolicy.capXP + CodeXPGrowthPolicy.CHOICE_INTERVAL_XP))
            assertEquals(150, mini.surface.level)
            assertTrue(service.resetAllExceptNickname())
            assertEquals(1, mini.surface.level)
            assertNull(mini.surface.design)
        } finally {
            Disposer.dispose(mini)
            Disposer.dispose(service)
        }
    }
}
