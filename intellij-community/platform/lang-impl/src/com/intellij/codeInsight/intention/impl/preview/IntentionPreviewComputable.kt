// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.intention.impl.preview

import com.intellij.codeInsight.daemon.impl.ShowIntentionsPass
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.impl.CachedIntentions
import com.intellij.codeInsight.intention.impl.IntentionActionWithTextCaching
import com.intellij.codeInsight.intention.impl.ShowIntentionActionsHandler
import com.intellij.diff.comparison.ComparisonManager
import com.intellij.diff.comparison.ComparisonPolicy
import com.intellij.diff.fragments.LineFragment
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.DumbProgressIndicator
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileFactory
import java.util.concurrent.Callable


internal class IntentionPreviewComputable(private val project: Project,
                                          private val action: IntentionAction,
                                          private val originalFile: PsiFile,
                                          private val originalEditor: Editor) : Callable<Pair<PsiFile?, List<LineFragment>>> {
  override fun call(): Pair<PsiFile?, List<LineFragment>> {
    val psiFileCopy = nonPhysicalPsiCopy(originalFile, project)
    ProgressManager.checkCanceled()
    val editorCopy = IntentionPreviewEditor(psiFileCopy, originalEditor.caretModel.offset)

    try {
      val action = findCopyIntention(project, editorCopy, psiFileCopy, action) ?: throw ProcessCanceledException()
      val fileEditorPair = ShowIntentionActionsHandler.chooseFileForAction(psiFileCopy, editorCopy, action)
                           ?: throw ProcessCanceledException()

      val writable = originalEditor.document.isWritable
      try {
        originalEditor.document.setReadOnly(true)
        ProgressManager.checkCanceled()
        action.invoke(project, fileEditorPair.second, fileEditorPair.first)
        ProgressManager.checkCanceled()
      }
      finally {
        originalEditor.document.setReadOnly(!writable)
      }

      return Pair<PsiFile?, List<LineFragment>>(
        psiFileCopy,
        ComparisonManager.getInstance().compareLines(originalFile.text, editorCopy.document.text, ComparisonPolicy.TRIM_WHITESPACES,
                                                     DumbProgressIndicator.INSTANCE)
      )
    }
    catch (e: IntentionPreviewUnsupportedOperationException) {
      throw ProcessCanceledException()
    }
    catch (e: Exception) {
      LOG.debug("There are exceptions on invocation the intention: '${action.text}' on a copy of the file.", e)
      throw ProcessCanceledException(e)
    }
  }

  private fun nonPhysicalPsiCopy(psiFile: PsiFile, project: Project): PsiFile {
    ProgressManager.checkCanceled()
    return PsiFileFactory.getInstance(project).createFileFromText(psiFile.name,
                                                                  psiFile.language,
                                                                  psiFile.text, false, true, false,
                                                                  psiFile.virtualFile)
  }

  companion object {
    private val LOG = Logger.getInstance(IntentionPreviewComputable::class.java)

    fun getFixes(cachedIntentions: CachedIntentions): Sequence<IntentionActionWithTextCaching> =
      sequenceOf<IntentionActionWithTextCaching>()
        .plus(cachedIntentions.intentions)
        .plus(cachedIntentions.inspectionFixes)
        .plus(cachedIntentions.errorFixes)

    private fun findCopyIntention(project: Project,
                                  editorCopy: Editor,
                                  psiFileCopy: PsiFile,
                                  originalAction: IntentionAction): IntentionAction? {
      val actionsToShow = ShowIntentionsPass.getActionsToShow(editorCopy, psiFileCopy, false)
      val cachedIntentions = CachedIntentions.createAndUpdateActions(project, psiFileCopy, editorCopy, actionsToShow)

      return getFixes(cachedIntentions).find { it.text == originalAction.text }?.action
    }
  }
}