package com.github.ilovegamecoding.intellijcodexp.listeners

import com.github.ilovegamecoding.intellijcodexp.services.CodeXPService
import com.github.ilovegamecoding.intellijcodexp.model.CodeXPChallenge
import com.intellij.ide.FrameStateListener
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.editor.event.EditorFactoryListener

internal class CodeXPEventListener : DocumentListener, EditorFactoryListener, FrameStateListener {
    private val codeXPService = service<CodeXPService>()

    init {
        val editorFactory = EditorFactory.getInstance()
        for (editor in editorFactory.allEditors) {
            editor.document.addDocumentListener(this)
        }
        editorFactory.addEditorFactoryListener(this) { }
    }

    override fun documentChanged(event: DocumentEvent) {
        super.documentChanged(event)
        thisLogger().warn("CodeXPEventListener.documentChanged")
        codeXPService.increaseChallengeValue(CodeXPChallenge.Type.TYPING_COUNT, 1)
    }

    override fun editorCreated(event: EditorFactoryEvent) {
        event.editor.document.addDocumentListener(this)
    }

    override fun onFrameActivated() {

    }
}