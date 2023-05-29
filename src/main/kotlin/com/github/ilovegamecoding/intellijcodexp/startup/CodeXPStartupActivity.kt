package com.github.ilovegamecoding.intellijcodexp.startup

import com.github.ilovegamecoding.intellijcodexp.core.CodeXP
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity

class CodeXPStartupActivity : StartupActivity {
    override fun runActivity(project: Project) {
        val state = service<CodeXP>()
        if (!state.hasExecuted) {
            thisLogger().info("CodeXP first execution")
            // TODO: Add plugin initialize logic

            state.hasExecuted = true
        } else {
            thisLogger().info("CodeXP has already executed")
        }
    }
}