/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.scopes.impl

import org.jetbrains.kotlin.fir.declarations.*
import org.jetbrains.kotlin.fir.resolve.FirSymbolProvider
import org.jetbrains.kotlin.fir.scopes.FirScope
import org.jetbrains.kotlin.fir.scopes.ProcessorAction
import org.jetbrains.kotlin.fir.scopes.ProcessorAction.NEXT
import org.jetbrains.kotlin.fir.scopes.ProcessorAction.STOP
import org.jetbrains.kotlin.fir.symbols.impl.FirCallableSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirClassifierSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirFunctionSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirVariableSymbol
import org.jetbrains.kotlin.name.Name

class FirClassDeclaredMemberScope(
    klass: FirClass<*>,
    useLazyNestedClassifierScope: Boolean = false,
    existingNames: List<Name>? = null,
    symbolProvider: FirSymbolProvider? = null
) : FirScope() {
    private val nestedClassifierScope = if (useLazyNestedClassifierScope) {
        lazyNestedClassifierScope(klass.symbol.classId, existingNames!!, symbolProvider!!)
    } else {
        nestedClassifierScope(klass)
    }

    private val functionIndex = mutableMapOf<Name, MutableList<FirFunctionSymbol<*>>>()

    private val propertyIndex = mutableMapOf<Name, MutableList<FirVariableSymbol<*>>>()

    init {
        loop@ for (declaration in klass.declarations) {
            when (declaration) {
                is FirVariable<*> -> {
                    propertyIndex.getOrPut(declaration.name) { mutableListOf() } += declaration.symbol
                }
                is FirMemberFunction<*> -> {
                    val name = when (declaration) {
                        is FirConstructor -> if (klass is FirRegularClass) klass.name else continue@loop
                        else -> declaration.name
                    }
                    functionIndex.getOrPut(name) { mutableListOf() } += declaration.symbol
                }
            }
        }
    }

    override fun processFunctionsByName(name: Name, processor: (FirFunctionSymbol<*>) -> ProcessorAction): ProcessorAction {
        val symbols = functionIndex[name] ?: emptyList()
        for (symbol in symbols) {
            if (!processor(symbol)) {
                return STOP
            }
        }
        return NEXT
    }

    override fun processPropertiesByName(name: Name, processor: (FirCallableSymbol<*>) -> ProcessorAction): ProcessorAction {
        val symbols = propertyIndex[name] ?: emptyList()
        for (symbol in symbols) {
            if (!processor(symbol)) {
                return STOP
            }
        }
        return NEXT
    }

    override fun processClassifiersByName(
        name: Name,
        processor: (FirClassifierSymbol<*>) -> ProcessorAction
    ): ProcessorAction = nestedClassifierScope.processClassifiersByName(name, processor)
}
