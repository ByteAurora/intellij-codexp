package com.github.ilovegamecoding.intellijcodexp.core

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil

@State(
    name = "com.github.ilovegamecoding.intellijcodexp.core.CodeXPUser",
    storages = [Storage("CodeXPUser.xml")]
)
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