package com.github.ilovegamecoding.intellijcodexp.collection

import com.github.ilovegamecoding.intellijcodexp.CodeXPBundle
import com.github.ilovegamecoding.intellijcodexp.domain.CodeXPGrowthPolicy
import com.github.ilovegamecoding.intellijcodexp.domain.CodeXPProgressResult
import com.github.ilovegamecoding.intellijcodexp.domain.collection.CodeXPCollectionCatalog
import com.github.ilovegamecoding.intellijcodexp.domain.collection.CollectionKind
import com.github.ilovegamecoding.intellijcodexp.domain.collection.CollectionOutcome
import com.github.ilovegamecoding.intellijcodexp.domain.collection.CrestDesign
import com.github.ilovegamecoding.intellijcodexp.enums.CodeXPNotificationType
import com.github.ilovegamecoding.intellijcodexp.enums.Event
import com.github.ilovegamecoding.intellijcodexp.listeners.CodeXPEffectListener
import com.github.ilovegamecoding.intellijcodexp.models.CodeXPCollectionRecord
import com.github.ilovegamecoding.intellijcodexp.models.CodeXPConfiguration
import com.github.ilovegamecoding.intellijcodexp.models.CodeXPState
import com.github.ilovegamecoding.intellijcodexp.services.CodeXPCollectionCoordinator
import com.github.ilovegamecoding.intellijcodexp.services.CodeXPCollectionPersistence
import com.github.ilovegamecoding.intellijcodexp.services.CodeXPService
import com.github.ilovegamecoding.intellijcodexp.services.MemoryCollectionPersistence
import com.intellij.notification.Notification
import com.intellij.notification.Notifications
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.xmlb.XmlSerializer
import org.jdom.Element
import java.io.IOException

class CollectionCelebrationTest : BasePlatformTestCase() {
    fun testOnlyCommittedNewPostCapRightsProduceOneReceipt() {
        val storage = FailingStorage()
        val coordinator = CodeXPCollectionCoordinator(storage)
        val state = coordinator.load(CodeXPState())
        assertNull(coordinator.takeChoiceAward())
        state.xp = CodeXPGrowthPolicy.capXP + CodeXPGrowthPolicy.CHOICE_INTERVAL_XP - 1
        coordinator.reconcile()
        assertNull(coordinator.takeChoiceAward())
        storage.fail = true
        state.xp++
        assertFalse(coordinator.reconcile())
        assertNull(coordinator.takeChoiceAward())
        assertEquals(0L, coordinator.snapshot().credits)
        storage.fail = false
        assertTrue(coordinator.reconcile())
        val first = coordinator.takeChoiceAward()!!
        assertEquals(1L, first.count)
        assertEquals(1L, first.first)
        assertNull(coordinator.takeChoiceAward()) // A second project cannot drain the same receipt.
        repeat(3) { coordinator.reconcile() }
        assertNull(coordinator.takeChoiceAward())
        state.xp += 55000 + 60000 + 65000
        coordinator.reconcile()
        val next = coordinator.takeChoiceAward()!!
        assertEquals(first.generation, next.generation)
        assertEquals(2L, next.first)
        assertEquals(4L, next.last)
        assertEquals(3L, next.count)
    }

    fun testMigrationSpendReloadAndResetCannotCelebrateOldCredits() {
        val storage = MemoryCollectionPersistence()
        var coordinator = CodeXPCollectionCoordinator(storage)
        var state = coordinator.load(CollectionProductFixtures.profile(175, 0))
        assertEquals(1L, coordinator.snapshot().credits)
        assertNull(coordinator.takeChoiceAward())
        val chosen = coordinator.snapshot().entries.first { it.canAcquire }
        coordinator.choose(coordinator.snapshot().session, chosen.id)
        assertNull(coordinator.takeChoiceAward())
        state.xp += coordinator.snapshot().choiceIntervalXP
        coordinator.reconcile()
        assertEquals(1L, coordinator.takeChoiceAward()!!.count)
        // Reopening can reconcile XP saved by an older plugin, but must not replay it.
        state.xp += coordinator.snapshot().choiceIntervalXP
        coordinator = CodeXPCollectionCoordinator(storage)
        state = coordinator.load(state)
        assertNull(coordinator.takeChoiceAward())
        state.xp += coordinator.snapshot().choiceIntervalXP
        coordinator.reconcile()
        assertNotNull(coordinator.takeChoiceAward())
        state.xp += coordinator.snapshot().choiceIntervalXP
        coordinator.reconcile()
        assertTrue(coordinator.reset(CodeXPState()))
        assertNull(coordinator.takeChoiceAward())
        assertEquals(0L, coordinator.snapshot().credits)
    }

    fun testSeveralCommittedIncrementsAccumulateBeforeDeliveryButFailedIncrementsDoNot() {
        val storage = FailingStorage()
        val coordinator = CodeXPCollectionCoordinator(storage)
        val state = coordinator.load(CodeXPState())
        state.xp = CodeXPGrowthPolicy.capXP + CodeXPGrowthPolicy.CHOICE_INTERVAL_XP
        coordinator.reconcile()
        state.xp += 55000 + 60000
        coordinator.reconcile()
        storage.fail = true
        state.xp += coordinator.snapshot().choiceIntervalXP
        coordinator.reconcile()
        val award = coordinator.takeChoiceAward()!!
        assertEquals(1L, award.first)
        assertEquals(3L, award.last)
        assertEquals(3L, award.count)
        storage.fail = false
        coordinator.reconcile()
        assertEquals(4L, coordinator.takeChoiceAward()!!.first)
    }

    fun testEquipPublishesOnlySuccessfulNewCrestWithItsOrigin() {
        val storage = FailingStorage()
        val service = CodeXPService(storage)
        val scope = Disposer.newDisposable()
        val equips = mutableListOf<Triple<String, CrestDesign, DataContext?>>()
        val origin = DataContext { null }
        try {
            ApplicationManager.getApplication().messageBus.connect(scope).subscribe(
                CodeXPEffectListener.EFFECTS,
                object : CodeXPEffectListener {
                    override fun progressRecorded(
                        result: CodeXPProgressResult,
                        dataContext: DataContext?,
                    ) = Unit

                    override fun crestEquipped(
                        eventId: String,
                        design: CrestDesign,
                        dataContext: DataContext?,
                    ) {
                        equips.add(Triple(eventId, design, dataContext))
                    }
                },
            )
            service.loadState(CollectionProductFixtures.profile(175, 0))
            val item = service.collectionSnapshot().entries.first { it.kind == CollectionKind.CREST && it.owned }

            fun equip() = service.equipCollection(service.collectionSnapshot().session, item.kind, item.id, origin)
            storage.fail = true
            assertEquals(CollectionOutcome.SAVE_FAILED, equip().outcome)
            assertTrue(equips.isEmpty())
            storage.fail = false
            assertEquals(CollectionOutcome.APPLIED, equip().outcome)
            assertEquals(1, equips.size)
            assertSame(origin, equips.single().third)
            assertEquals(item.design, equips.single().second)
            assertEquals(CollectionOutcome.UNCHANGED, equip().outcome)
            service.updateConfiguration(service.state.codeXPConfiguration.copy(reducedMotion = true))
            service.equipCollection(service.collectionSnapshot().session, item.kind, null, origin)
            assertEquals(1, equips.size)
            equip()
            assertEquals(2, equips.size)
            assertFalse(equips[0].first == equips[1].first)
        } finally {
            Disposer.dispose(scope)
            Disposer.dispose(service)
        }
    }

    fun testProgressPublicationCarriesReceiptOnceWithoutAddingXp() {
        val service = CodeXPService(MemoryCollectionPersistence())
        val scope = Disposer.newDisposable()
        val progress = mutableListOf<CodeXPProgressResult>()
        try {
            ApplicationManager
                .getApplication()
                .messageBus
                .connect(scope)
                .subscribe(CodeXPEffectListener.EFFECTS, CodeXPEffectListener { result, _ -> progress.add(result) })
            service.loadState(CodeXPState())
            service.state.xp = CodeXPGrowthPolicy.capXP + CodeXPGrowthPolicy.CHOICE_INTERVAL_XP - 1
            service.eventOccurred(Event.TYPING, null)
            assertEquals(CodeXPGrowthPolicy.capXP + CodeXPGrowthPolicy.CHOICE_INTERVAL_XP + 1, service.state.xp)
            assertEquals(1L, progress.single().choiceAward!!.count)
            service.eventOccurred(Event.TYPING, null)
            assertNull(progress.last().choiceAward)
        } finally {
            Disposer.dispose(scope)
            Disposer.dispose(service)
        }
    }

    fun testNativeNotificationsReportOneCommittedBatchAndRespectTheLevelNotificationSetting() {
        val service = CodeXPService(MemoryCollectionPersistence())
        val received = mutableListOf<Notification>()
        project.messageBus.connect(testRootDisposable).subscribe(
            Notifications.TOPIC,
            object : Notifications {
                override fun notify(notification: Notification) {
                    if (notification.groupId == "CodeXP") received.add(notification)
                }
            },
        )
        val origin = SimpleDataContext.getProjectContext(project)
        val config = CodeXPConfiguration(notificationType = CodeXPNotificationType.INTELLIJ.storedValue, showGainedXP = false, showCompleteChallengeNotification = false)
        try {
            service.loadState(CodeXPState(xp = CodeXPGrowthPolicy.capXP, codeXPConfiguration = config))
            assertTrue(received.isEmpty())
            service.state.xp += 50000 + 55000 + 60000 - Event.TYPING.xpValue
            service.eventOccurred(Event.TYPING, origin)
            service.eventOccurred(Event.TYPING, origin)
            assertEquals(CodeXPBundle.message("notification.choice.title"), received.single().title)
            assertEquals(CodeXPBundle.message("effects.choice.earned", 3L), received.single().content)

            service.updateConfiguration(config.copy(showLevelUpNotification = false))
            service.state.xp += service.collectionSnapshot().choiceIntervalXP
            service.eventOccurred(Event.TYPING, origin)
            service.updateConfiguration(config)
            service.eventOccurred(Event.TYPING, origin)
            service.updateConfiguration(config.copy(notificationType = CodeXPNotificationType.CODEXP.storedValue))
            service.state.xp += service.collectionSnapshot().choiceIntervalXP
            service.eventOccurred(Event.TYPING, origin)
            assertEquals(1, received.size)
        } finally {
            received.forEach { it.expire() }
            Disposer.dispose(service)
        }
    }

    fun testOldConfigurationDefaultsRoundTripSanitizationAndFullReset() {
        val defaults = XmlSerializer.deserialize(Element("CodeXPConfiguration"), CodeXPConfiguration::class.java)
        assertTrue(defaults.miniCrestEnabled)
        assertEquals(96, defaults.miniCrestSize)
        assertEquals(1.0, defaults.miniCrestX)
        assertFalse(defaults.miniCrestWindowAnchor)
        val service = CodeXPService(MemoryCollectionPersistence())
        try {
            service.loadState(CodeXPState(nickname = "keep"))
            for (size in listOf(72, 96, 128, 164, 256)) {
                val old = defaults.copy(miniCrestSize = size, miniCrestX = .25, miniCrestY = .4, miniCrestPinned = true, miniCrestCollapsed = true, miniCrestEnabled = false, miniCrestWindowAnchor = size == 256)
                service.loadState(CodeXPState(nickname = "keep", codeXPConfiguration = old.copy()))
                val copy = XmlSerializer.deserialize(XmlSerializer.serialize(service.state), CodeXPState::class.java)
                assertEquals(old.copy(miniCrestSize = if (size == 72) 96 else size, miniCrestPinned = false, miniCrestCollapsed = false), copy.codeXPConfiguration)
            }
            service.updateConfiguration(defaults.copy(miniCrestSize = -1, miniCrestX = Double.NaN, miniCrestY = 9.0))
            assertEquals(96, service.state.codeXPConfiguration.miniCrestSize)
            assertEquals(1.0, service.state.codeXPConfiguration.miniCrestX)
            assertEquals(1.0, service.state.codeXPConfiguration.miniCrestY)
            assertTrue(service.resetAllExceptNickname())
            assertEquals("keep", service.state.nickname)
            assertEquals(CodeXPConfiguration(), service.state.codeXPConfiguration)
            assertNull(service.equippedCrest())
        } finally {
            Disposer.dispose(service)
        }
    }

    private class FailingStorage : CodeXPCollectionPersistence {
        val memory = MemoryCollectionPersistence()
        var fail = false

        override fun load(): CodeXPCollectionRecord? = memory.load()

        override fun save(record: CodeXPCollectionRecord) {
            if (fail) throw IOException("injected before durable commit")
            memory.save(record)
        }
    }
}
