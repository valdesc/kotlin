/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.statistics.metrics

interface IMetricContainer<T> {
    fun addValue(t: T)

    fun toStringRepresentation(): String
}

interface IMetricContainerFactory<T> {
    fun newMetricContainer(): IMetricContainer<T>
}

class OverrideMetricContainer<T>() : IMetricContainer<T> {
    var myValue: T? = null

    override fun addValue(t: T) {
        myValue = t
    }

    override fun toStringRepresentation(): String {
        return myValue?.toString() ?: "null"
    }
}

class ConcatMetricContainer() : IMetricContainer<String> {
    val myValues = HashSet<String>()

    override fun addValue(t: String) {
        myValues.add(t)
    }

    override fun toStringRepresentation(): String {
        return myValues.joinToString(";")
    }
}

