package com.github.ilovegamecoding.intellijcodexp.services

import com.github.ilovegamecoding.intellijcodexp.presentation.guide.CodeXPGuideCatalog
import com.github.ilovegamecoding.intellijcodexp.presentation.guide.GuideRelease
import com.github.ilovegamecoding.intellijcodexp.presentation.guide.GuideRun
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

data class CodeXPGuideState(
    var initialized: Boolean = false,
    var introCompleted: Boolean = false,
    var seenReleases: MutableSet<String> = mutableSetOf(),
    var resumeSteps: MutableMap<String, String> = mutableMapOf(),
)

/** Presentation history is separate from XP, migrations and collection entitlements. */
@Service(Service.Level.APP)
@State(name = "CodeXPGuide", storages = [Storage("CodeXPGuide.xml")])
class CodeXPGuideService : PersistentStateComponent<CodeXPGuideState> {
    private var data = CodeXPGuideState()
    private var prompted = false

    @Synchronized override fun getState(): CodeXPGuideState = data.copy(seenReleases = data.seenReleases.toMutableSet(), resumeSteps = data.resumeSteps.toMutableMap())

    @Synchronized override fun loadState(state: CodeXPGuideState) {
        data = state.copy(seenReleases = state.seenReleases.toMutableSet(), resumeSteps = state.resumeSteps.toMutableMap())
    }

    @Synchronized internal fun initialize(existingInstallation: Boolean) {
        if (data.initialized) return
        data.initialized = true
        data.introCompleted = existingInstallation
    }

    @Synchronized internal fun pending(releases: List<GuideRelease> = CodeXPGuideCatalog.releases): GuideRun? {
        if (!data.introCompleted) return GuideRun(true, releases, CodeXPGuideCatalog.intro)
        val unread = releases.filter { it.id !in data.seenReleases }
        return unread.takeIf { it.isNotEmpty() }?.let { GuideRun(false, it, it.flatMap(GuideRelease::steps).distinctBy { step -> step.id }) }
    }

    @Synchronized internal fun claimNotice(): GuideRun? {
        if (prompted) return null
        return pending()?.also { prompted = true }
    }

    @Synchronized internal fun open(intro: Boolean? = null): GuideRun {
        prompted = true
        return when (intro) {
            true -> GuideRun(true, CodeXPGuideCatalog.releases, CodeXPGuideCatalog.intro)
            false -> pending()?.takeIf { !it.intro } ?: CodeXPGuideCatalog.releases.last().let { GuideRun(false, listOf(it), it.steps) }
            null -> pending() ?: GuideRun(true, CodeXPGuideCatalog.releases, CodeXPGuideCatalog.intro)
        }
    }

    @Synchronized internal fun page(run: GuideRun): Int = run.steps.indexOfFirst { it.id == data.resumeSteps[run.key] }.coerceAtLeast(0)

    @Synchronized internal fun remember(
        run: GuideRun,
        index: Int,
    ) {
        run.steps.getOrNull(index)?.let { data.resumeSteps[run.key] = it.id }
    }

    @Synchronized internal fun complete(run: GuideRun) {
        val firstIntro = run.intro && !data.introCompleted
        if (run.intro) data.introCompleted = true
        if (!run.intro || firstIntro) data.seenReleases.addAll(run.releases.map { it.id })
        data.resumeSteps.remove(run.key)
    }

    @Synchronized internal fun reset() {
        data = CodeXPGuideState(initialized = true)
        prompted = false
    }
}
