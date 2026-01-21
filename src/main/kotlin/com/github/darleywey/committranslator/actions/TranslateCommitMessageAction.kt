package com.github.darleywey.committranslator.actions

import com.github.darleywey.committranslator.CommitTranslatorBundle
import com.github.darleywey.committranslator.services.TranslationService
import com.github.darleywey.committranslator.settings.CommitTranslatorSettings
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.vcs.ui.CommitMessage
import com.intellij.ui.AnimatedIcon

class TranslateCommitMessageAction : AnAction() {

    @Volatile
    private var isTranslating = false

    private val COMMIT_MESSAGE_CONTROL = DataKey.create<CommitMessage>("Vcs.CommitMessage.Control")

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val commitMessage = e.getData(COMMIT_MESSAGE_CONTROL) ?: return
        
        if (isTranslating) return
        
        val getTextMethod = commitMessage::class.java.methods.find { it.name == "getText" }
        val originalText = getTextMethod?.invoke(commitMessage) as? String ?: return
        if (originalText.isBlank()) {
            Messages.showWarningDialog(
                project,
                CommitTranslatorBundle.message("action.translate.emptyMessage"),
                CommitTranslatorBundle.message("action.translate.title")
            )
            return
        }

        val settings = CommitTranslatorSettings.getInstance()
        if (settings.apiKey.isBlank()) {
            val result = Messages.showYesNoDialog(
                project,
                CommitTranslatorBundle.message("action.translate.noApiKey"),
                CommitTranslatorBundle.message("action.translate.title"),
                CommitTranslatorBundle.message("action.translate.openSettings"),
                CommitTranslatorBundle.message("action.translate.cancel"),
                Messages.getWarningIcon()
            )
            if (result == Messages.YES) {
                com.intellij.openapi.options.ShowSettingsUtil.getInstance()
                    .showSettingsDialog(project, CommitTranslatorBundle.message("settings.displayName"))
            }
            return
        }

        isTranslating = true

        ProgressManager.getInstance().run(object : Task.Backgroundable(
            project,
            CommitTranslatorBundle.message("action.translate.progress"),
            true
        ) {
            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = true
                
                val result = TranslationService.getInstance().translateToEnglish(originalText)
                
                ApplicationManager.getApplication().invokeLater {
                    isTranslating = false
                    result.fold(
                        onSuccess = { translatedText ->
                            val setMethod = commitMessage::class.java.methods.find { 
                                it.name == "setCommitMessage" && it.parameterCount == 1 
                            }
                            setMethod?.invoke(commitMessage, translatedText)
                        },
                        onFailure = { error ->
                            val errorMessage: String = CommitTranslatorBundle.message("action.translate.error", error.message ?: "Unknown error")
                            val title: String = CommitTranslatorBundle.message("action.translate.title")
                            Messages.showErrorDialog(project, errorMessage, title)
                        }
                    )
                }
            }

            override fun onCancel() {
                isTranslating = false
            }
        })
    }

    override fun update(e: AnActionEvent) {
        val commitMessage = e.getData(COMMIT_MESSAGE_CONTROL)
        e.presentation.isEnabledAndVisible = commitMessage != null
        
        if (isTranslating) {
            e.presentation.icon = AnimatedIcon.Default.INSTANCE
            e.presentation.isEnabled = false
        } else {
            e.presentation.icon = IconLoader.getIcon("/icons/translate.svg", javaClass)
            e.presentation.isEnabled = true
        }
    }
}
