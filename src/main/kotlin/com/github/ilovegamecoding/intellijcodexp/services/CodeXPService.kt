package com.github.ilovegamecoding.intellijcodexp.services

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger

@Service(Service.Level.APP)
class CodeXPService {
    init {
        thisLogger().info("CodeXP plugin service activated")
    }
}
