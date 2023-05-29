package com.github.ilovegamecoding.intellijcodexp.core

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.util.xmlb.XmlSerializerUtil

class CodeXPUser : PersistentStateComponent<CodeXPUser> {
    var xp: Long = 0
    var nickname: String = ""

    override fun getState(): CodeXPUser {
        return this
    }

    override fun loadState(state: CodeXPUser) {
        XmlSerializerUtil.copyBean(state, this)
    }
}