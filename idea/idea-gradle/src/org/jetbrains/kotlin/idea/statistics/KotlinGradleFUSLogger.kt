/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.statistics

import org.jetbrains.kotlin.statistics.metrics.StringMetrics

class KotlinGradleFUSLogger(val todoGradleHomeDir: String) {
/*
    fun main() {
        AppExecutorUtil.getAppScheduledExecutorService().scheduleWithFixedDelay
    }
*/

    fun doSmth() {
        println(StringMetrics.GRADLE_VERSION)
    }
}