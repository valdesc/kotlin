/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.statistics.fileloggers

import org.jetbrains.kotlin.statistics.metrics.*
import java.util.*

class MetricsContainer : IStatisticsValuesConsumer {

    private val numericalMetrics = TreeMap<String, IMetricContainer<Long>>()

    private val booleanMetrics = TreeMap<String, IMetricContainer<Boolean>>()

    private val stringMetrics = TreeMap<String, IMetricContainer<String>>()

    override fun report(metric: BooleanMetrics, value: Boolean, subprojectName: String?) {
        //TODO add processing when subproject is set
        val metricContainer = booleanMetrics[metric.name] ?: metric.type.newMetricContainer().also { booleanMetrics[metric.name] = it }
        metricContainer.addValue(value)
    }

    override fun report(metric: NumericalMetrics, value: Long, subprojectName: String?) {
        //TODO add processing when subproject is set
        val metricContainer = numericalMetrics[metric.name] ?: metric.type.newMetricContainer().also { numericalMetrics[metric.name] = it }
        metricContainer.addValue(value)
    }

    override fun report(metric: StringMetrics, value: String, subprojectName: String?) {
        //TODO add processing when subproject is set
        val metricContainer = stringMetrics[metric.name] ?: metric.type.newMetricContainer().also { stringMetrics[metric.name] = it }
        metricContainer.addValue(value)
    }

    fun flush(trackingFile: IRecordLogger?) {
        if (trackingFile == null) return
        trackingFile.writeString("Starting build session\r\n\r\n")

        for (entry in numericalMetrics.entries.union(booleanMetrics.entries).union(stringMetrics.entries)) {
            trackingFile.writeString("${entry.key}=${entry.value.toStringRepresentation()}\r\n")
        }

        trackingFile.writeString("Finish build session\r\n")
    }
}