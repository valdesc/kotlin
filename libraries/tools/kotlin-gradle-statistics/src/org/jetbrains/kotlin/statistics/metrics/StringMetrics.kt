/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.statistics.metrics

import org.jetbrains.kotlin.statistics.metrics.StringAnonymizationPolicy.*
import org.jetbrains.kotlin.statistics.metrics.StringOverridePolicy.*


enum class StringMetrics(val type: StringOverridePolicy, val anonymization: StringAnonymizationPolicy, val perProject: Boolean = false) {

    GRADLE_VERSION(OVERRIDE, COMPONENT_VERSION),

    OS_TYPE(OVERRIDE, SAFE),

    MPP_PLATFORMS(CONCAT, SAFE),

    ANDROID_GRADLE_PLUGIN_VERSION(OVERRIDE, COMPONENT_VERSION),

    KOTLIN_COMPILER_VERSION(OVERRIDE, COMPONENT_VERSION),

    KOTLIN_STDLIB_VERSION(OVERRIDE, COMPONENT_VERSION),

    KOTLIN_REFLECT_VERSION(OVERRIDE, COMPONENT_VERSION),

    TEST_STRING_METRIC(OVERRIDE, SAFE)
}


