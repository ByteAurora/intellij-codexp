package com.github.ilovegamecoding.intellijcodexp.collection

import com.github.ilovegamecoding.intellijcodexp.CodeXPBundle
import com.github.ilovegamecoding.intellijcodexp.domain.collection.CodeXPCollectionCatalog
import com.github.ilovegamecoding.intellijcodexp.presentation.collection.MiniCrestOptionsAction
import com.intellij.AbstractBundle
import com.intellij.DynamicBundle
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.util.Locale

class CollectionLocalizationTest : BasePlatformTestCase() {
    fun testKeyboardOptionsAreRegisteredUnderTheLocalizedActionName() {
        val action = ActionManager.getInstance().getAction("CodeXP.MiniCrestOptions")
        assertTrue(action is MiniCrestOptionsAction)
        assertEquals(CodeXPBundle.message("action.CodeXP.MiniCrestOptions.text"), action.templatePresentation.text)
    }

    fun testIdeResolvesTheWholeShippingCatalogAndRewardCountsInEnglishAndKorean() {
        val loader = CodeXPBundle::class.java.classLoader
        val en = DynamicBundle.getResourceBundle(loader, "messages.CodeXPBundle", Locale.ENGLISH)
        val ko = DynamicBundle.getResourceBundle(loader, "messages.CodeXPBundle", Locale.KOREAN)
        for (item in CodeXPCollectionCatalog.content.items) {
            val key = "collection.item.${item.definition.id}"
            assertTrue("Missing translation for $key", en.getString(key) != ko.getString(key))
        }
        assertEquals("Locked crest", en.getString("collection.locked.crest"))
        assertEquals("미획득 인장", ko.getString("collection.locked.crest"))
        assertEquals("Acquire for 1 credit", AbstractBundle.message(en, "collection.acquire", 1L))
        assertEquals("선택권 1개로 획득", AbstractBundle.message(ko, "collection.acquire", 1L))
        assertEquals("Earned 1 choice credit", AbstractBundle.message(en, "effects.choice.earned", 1L))
        assertEquals("Earned 3 choice credits", AbstractBundle.message(en, "effects.choice.earned", 3L))
        assertEquals("선택권 3개 획득", AbstractBundle.message(ko, "effects.choice.earned", 3L))
        assertEquals("선택됨, 기록가", AbstractBundle.message(ko, "collection.selected.accessible", ko.getString("collection.item.title.recorder")))
    }
}
