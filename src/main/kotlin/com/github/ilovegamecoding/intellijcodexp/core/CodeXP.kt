package com.github.ilovegamecoding.intellijcodexp.core

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil

@State(
    name = "com.github.ilovegamecoding.intellijcodexp.core.CodeXP",
    storages = [Storage("CodeXP.xml")]
)
class CodeXP : PersistentStateComponent<CodeXP> {
    var hasExecuted: Boolean = false
    override fun getState(): CodeXP {
        return this
    }

    override fun loadState(state: CodeXP) {
        XmlSerializerUtil.copyBean(state, this)
    }
}