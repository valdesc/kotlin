/*
 * Copyright 2010-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.targets.js.testing.mocha

import org.gradle.api.Project
import org.gradle.process.ProcessForkOptions
import org.jetbrains.kotlin.gradle.internal.testing.TCServiceMessagesClientSettings
import org.jetbrains.kotlin.gradle.internal.testing.TCServiceMessagesTestExecutionSpec
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinJsCompilation
import org.jetbrains.kotlin.gradle.targets.js.KotlinGradleNpmPackage
import org.jetbrains.kotlin.gradle.targets.js.RequiredKotlinJsDependency
import org.jetbrains.kotlin.gradle.targets.js.internal.parseNodeJsStackTraceAsJvm
import org.jetbrains.kotlin.gradle.targets.js.ir.KotlinJsIrTest
import org.jetbrains.kotlin.gradle.targets.js.ir.KotlinJsIrTestFramework
import org.jetbrains.kotlin.gradle.targets.js.jsQuoted
import org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsRootPlugin
import org.jetbrains.kotlin.gradle.targets.js.npm.LoadNpmModules
import org.jetbrains.kotlin.gradle.targets.js.npm.RequiresNpmDependencies
import org.jetbrains.kotlin.gradle.targets.js.npm.npmProject
import org.jetbrains.kotlin.gradle.targets.js.testing.KotlinJsTest
import org.jetbrains.kotlin.gradle.targets.js.testing.KotlinJsTestFramework
import org.jetbrains.kotlin.gradle.targets.js.testing.KotlinTestRunnerCliArgs
import org.jetbrains.kotlin.gradle.tasks.KotlinTest

class KotlinMocha(override val compilation: KotlinJsCompilation) :
    KotlinJsTestFramework,
    KotlinJsIrTestFramework {
    private val project: Project = compilation.target.project
    private val nodeJs = NodeJsRootPlugin.apply(project.rootProject)
    private val versions = nodeJs.versions

    override val settingsState: String
        get() = "mocha"

    override val requiredNpmDependencies: Collection<RequiredKotlinJsDependency>
        get() = listOf(
            KotlinGradleNpmPackage("test-js-runner"),
            versions.mocha,
            versions.sourceMapSupport
        )

    // https://mochajs.org/#-timeout-ms-t-ms
    var timeout: String = DEFAULT_TIMEOUT

    override fun createTestExecutionSpec(
        task: KotlinJsIrTest,
        forkOptions: ProcessForkOptions,
        nodeJsArgs: MutableList<String>,
        debug: Boolean
    ): TCServiceMessagesTestExecutionSpec =
        createTestExecutionSpecPrivate<KotlinJsIrTest>(
            task, forkOptions, nodeJsArgs, debug
        )

    override fun createTestExecutionSpec(
        task: KotlinJsTest,
        forkOptions: ProcessForkOptions,
        nodeJsArgs: MutableList<String>,
        debug: Boolean
    ): TCServiceMessagesTestExecutionSpec =
        createTestExecutionSpecPrivate(
            task = task,
            forkOptions = forkOptions,
            nodeJsArgs = nodeJsArgs,
            debug = debug
        )

    private fun <T> createTestExecutionSpecPrivate(
        task: T,
        forkOptions: ProcessForkOptions,
        nodeJsArgs: MutableList<String>,
        debug: Boolean
    ): TCServiceMessagesTestExecutionSpec
            where T : KotlinTest,
                  T : RequiresNpmDependencies,
                  T : LoadNpmModules {
        val clientSettings = TCServiceMessagesClientSettings(
            task.name,
            testNameSuffix = task.targetName,
            prependSuiteName = true,
            stackTraceParser = ::parseNodeJsStackTraceAsJvm,
            ignoreOutOfRootNodes = true
        )

        val npmProject = compilation.npmProject

        val cliArgs = KotlinTestRunnerCliArgs(
            include = task.includePatterns,
            exclude = task.excludePatterns
        )

        createAdapterJs(task)

        val mocha = npmProject.require("mocha/bin/mocha")
        val adapter = npmProject.require("./$ADAPTER_NODEJS")

        val args = mutableListOf(
            "--require",
            npmProject.require("source-map-support/register.js")

        ).apply {
            add(mocha)
            addAll(cliArg("--inspect-brk", debug))
            add(adapter)
            addAll(cliArgs.toList())
            addAll(cliArg("--reporter", "kotlin-test-js-runner/mocha-kotlin-reporter.js"))
            addAll(cliArg("--timeout", timeout))
        }

        return TCServiceMessagesTestExecutionSpec(
            forkOptions,
            args,
            false,
            clientSettings
        )
    }

    private fun cliArg(cli: String, value: Boolean): List<String> {
        return if (value) listOf(cli) else emptyList()
    }

    private fun cliArg(cli: String, value: String?): List<String> {
        return value?.let { listOf(cli, it) } ?: emptyList()
    }

    private fun createAdapterJs(task: LoadNpmModules) {
        val npmProject = compilation.npmProject
        val file = task.nodeModulesToLoad
            .map { npmProject.require(it) }
            .single()

        val adapterJs = npmProject.dir.resolve(ADAPTER_NODEJS)
        adapterJs.printWriter().use { writer ->
            val adapter = npmProject.require("kotlin-test-js-runner/kotlin-test-nodejs-runner.js")
            writer.println("require(${adapter.jsQuoted()})")

            writer.println("module.exports = require(${file.jsQuoted()})")
        }
    }

    companion object {
        const val ADAPTER_NODEJS = "adapter-nodejs.js"

        private const val DEFAULT_TIMEOUT = "2s"
    }
}