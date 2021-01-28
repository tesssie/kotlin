/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.analysis.checkers.declaration

import org.jetbrains.kotlin.fir.FirSourceElement
import org.jetbrains.kotlin.fir.analysis.checkers.FirDeclarationInspector
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.fir.analysis.diagnostics.FirErrors
import org.jetbrains.kotlin.fir.declarations.*
import org.jetbrains.kotlin.fir.firLookupTracker

object FirConflictsChecker : FirBasicDeclarationChecker() {
    override fun check(declaration: FirDeclaration, context: CheckerContext, reporter: DiagnosticReporter) {
        val inspector = FirDeclarationInspector()

        when (declaration) {
            is FirFile -> checkFile(declaration, inspector, context)
            is FirRegularClass -> checkRegularClass(declaration, inspector)
            else -> return
        }

        inspector.functionDeclarations.forEachNonSingle { it, hint ->
            reporter.reportConflictingOverloads(it.source, hint)
        }

        inspector.otherDeclarations.forEachNonSingle { it, hint ->
            reporter.reportConflictingDeclarations(it.source, hint)
        }
    }

    private fun Map<String, List<FirDeclaration>>.forEachNonSingle(action: (FirDeclaration, String) -> Unit) {
        for (value in values) {
            if (value.size > 1) {
                val hint = value.joinToString { that -> that.toString() }

                value.forEach {
                    action(it, hint)
                }
            }
        }
    }

    private fun checkFile(file: FirFile, inspector: FirDeclarationInspector, context: CheckerContext) {
        val lookupTracker = context.session.firLookupTracker
        for (topLevelDeclaration in file.declarations) {
            inspector.collect(topLevelDeclaration)
            if (lookupTracker != null) {
                when (topLevelDeclaration) {
                    is FirSimpleFunction -> topLevelDeclaration.name
                    is FirVariable<*> -> topLevelDeclaration.name
                    is FirRegularClass -> topLevelDeclaration.name
                    is FirTypeAlias -> topLevelDeclaration.name
                    else -> null
                }?.let {
                    lookupTracker.recordLookup(it, topLevelDeclaration.source, file.source, file.packageFqName.asString())
                }
            }
        }
    }

    private fun checkRegularClass(declaration: FirRegularClass, inspector: FirDeclarationInspector) {
        for (it in declaration.declarations) {
            inspector.collect(it)
        }
    }

    private fun DiagnosticReporter.reportConflictingOverloads(source: FirSourceElement?, declarations: String) {
        source?.let { report(FirErrors.CONFLICTING_OVERLOADS.on(it, declarations)) }
    }

    private fun DiagnosticReporter.reportConflictingDeclarations(source: FirSourceElement?, declarations: String) {
        source?.let { report(FirErrors.REDECLARATION.on(it, declarations)) }
    }
}
