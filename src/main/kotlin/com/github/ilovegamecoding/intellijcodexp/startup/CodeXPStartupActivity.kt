package com.github.ilovegamecoding.intellijcodexp.startup

import com.github.ilovegamecoding.intellijcodexp.model.CodeXPChallenge
import com.github.ilovegamecoding.intellijcodexp.services.CodeXPService
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity

/**
 * CodeXPStartupActivity class
 *
 * This class is used to initialize the plugin when the IDE starts.
 */
class CodeXPStartupActivity : StartupActivity {
    override fun runActivity(project: Project) {
        with(service<CodeXPService>()) {
            initializePlugin(
                listOf(
                    CodeXPChallenge(
                        type = CodeXPChallenge.Type.TYPING_COUNT.ordinal,
                        name = "Typing",
                        description = "Just type anything. We will give you XP for it.",
                        rewardXP = 100,
                        value = 0,
                        goal = 10
                    ),
                    CodeXPChallenge(
                        type = CodeXPChallenge.Type.ACTION_COUNT.ordinal,
                        name = "Action",
                        description = "Just do anything. We will give you XP for it.",
                        rewardXP = 100,
                        value = 0,
                        goal = 10
                    )
                )
            )
        }
    }
}