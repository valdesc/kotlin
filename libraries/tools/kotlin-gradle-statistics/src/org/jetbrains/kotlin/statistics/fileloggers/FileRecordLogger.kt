/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.statistics.fileloggers

import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.nio.channels.Channels
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.file.Paths
import java.nio.file.StandardOpenOption

class FileRecordLogger(file: File) : IRecordLogger {

    private val channel: FileChannel
    private val outputStream: OutputStream
    private val lock: FileLock

    init {
        channel =
            FileChannel.open(Paths.get(file.toURI()), StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.APPEND)
                ?: throw IOException("Could not open file $file")
        lock = try {
            channel.lock()
        } catch (e: IOException) {
            channel.close()
            throw e
        }
        outputStream = Channels.newOutputStream(channel)
    }

    override fun writeString(s: String) {
        outputStream.write(s.toByteArray())
    }

    override fun close() {
        channel.use {
            outputStream.use {
                lock.release()
            }
        }
    }
}
