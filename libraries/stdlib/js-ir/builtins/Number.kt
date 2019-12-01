/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

@file:Suppress("NON_ABSTRACT_FUNCTION_WITH_NO_BODY")

package kotlin

/**
 * Superclass for all platform classes representing numeric values.
 */
public abstract class Number {
    /**
     * Returns the value of this number as a [Double], which may involve rounding.
     */
    public abstract fun toDouble(): Double

    /**
     * Returns the value of this number as a [Float], which may involve rounding.
     */
    public abstract fun toFloat(): Float

    /**
     * Returns the value of this number as a [Long], which may involve rounding or truncation.
     */
    public abstract fun toLong(): Long

    /**
     * Returns the value of this number as an [Int], which may involve rounding or truncation.
     */
    public abstract fun toInt(): Int

    /**
     * Returns the [Char] with the numeric value equal to this number, truncated to 16 bits if appropriate.
     */
    public abstract fun toChar(): Char

    /**
     * Returns the value of this number as a [Short], which may involve rounding or truncation.
     */
    public abstract fun toShort(): Short

    /**
     * Returns the value of this number as a [Byte], which may involve rounding or truncation.
     */
    public abstract fun toByte(): Byte

    public override fun equals(other: Any?): Boolean

    public override fun hashCode(): Int

    public override fun toString(): String
}

