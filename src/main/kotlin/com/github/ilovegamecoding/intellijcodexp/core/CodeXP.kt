package com.github.ilovegamecoding.intellijcodexp.core

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.util.xmlb.XmlSerializerUtil

class CodeXP : PersistentStateComponent<CodeXP> {
    var hasExecuted: Boolean = false
    override fun getState(): CodeXP {
        return this
    }

    override fun loadState(state: CodeXP) {
        XmlSerializerUtil.copyBean(state, this)
    }
}