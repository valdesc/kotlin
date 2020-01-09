/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package kotlin.collections

/**
 * The minimum capacity that we'll use for a newly created deque.
 * Must be a power of 2.
 */
private const val MIN_INITIAL_CAPACITY = 8

/**
 * Resizable-array implementation of the deque data structure.
 *
 * The name deque is short for "double ended queue" and is usually pronounced "deck".
 *
 * The collection provide methods for convenient access to the both ends.
 * It also implements [MutableList] interface and supports efficient get/set operations by index.
 */
@ExperimentalStdlibApi
public class ArrayDeque<E>(capacity: Int) : AbstractMutableList<E>() {
    private var head: Int = 0
    private var tail: Int = 0
    private var elements: Array<Any?>

    init {
        if (capacity < 0)
            throw IllegalArgumentException("Capacity can't be negative")

        var initialCapacity = MIN_INITIAL_CAPACITY
        // Find the best power of two to hold elements.
        // Tests "<=" because arrays aren't kept full.
        if (capacity >= initialCapacity) {
            initialCapacity = capacity.takeHighestOneBit() shl 1

            if (initialCapacity < 0)   // Too many elements, must back off
                initialCapacity = initialCapacity ushr 1 // Good luck allocating 2 ^ 30 elements
        }
        elements = arrayOfNulls(initialCapacity)
    }

    /** Constructs and empty deque. */
    constructor() : this(0)

    /** Constructs a deque that contains the same elements as the specified [elements] collection in the same order. */
    constructor(elements: Collection<E>) : this(elements.size) {
        this.addAll(elements)
    }

    /**
     * Doubles the capacity of this deque.  Call only when full, i.e.,
     * when head and tail have wrapped around to become equal.
     */
    private fun doubleCapacity() {
        check(head == tail)

        val newCapacity = elements.size shl 1
        if (newCapacity < 0)
            throw IllegalStateException("Sorry, deque too big")

        copyElements(newCapacity, elements.size)
    }

    /**
     * Ensures that the capacity of this deque is at least equal to the specified [minimumCapacity].
     *
     * If the current capacity is less than the [minimumCapacity], a new backing storage is allocated with greater capacity.
     * Otherwise, this method takes no action and simply returns.
     *
     * Do not call this method if this deque is full.
     */
    private fun ensureCapacity(minimumCapacity: Int) {
        if (minimumCapacity < elements.size) return

        val newCapacity = minimumCapacity.takeHighestOneBit() shl 1
        if (newCapacity < 0)
            throw IllegalStateException("Sorry, deque too big")

        copyElements(newCapacity, size)
    }

    /**
     * Creates a new array with the specified [newCapacity] size and copies elements in the [elements] array to it.
     */
    private fun copyElements(newCapacity: Int, newSize: Int) {
        val newElements = arrayOfNulls<Any?>(newCapacity)
        elements.copyInto(newElements, 0, head, elements.size)
        elements.copyInto(newElements, elements.size - head, 0, head)
        head = 0
        tail = newSize
        elements = newElements
    }

    private inline val mask: Int
        get() = elements.size - 1

    @kotlin.internal.InlineOnly
    private inline fun internalGet(internalIndex: Int): E {
        @Suppress("UNCHECKED_CAST")
        return elements[internalIndex] as E
    }

    @kotlin.internal.InlineOnly
    private inline fun internalIndex(index: Int): Int = (head + index) and mask

    @kotlin.internal.InlineOnly
    private inline fun incremented(index: Int): Int = (index + 1) and mask

    @kotlin.internal.InlineOnly
    private inline fun decremented(index: Int): Int = (index - 1) and mask

    override val size: Int
        get() = (tail - head) and mask

    override fun isEmpty(): Boolean = head == tail

    /**
     * Returns first element or throws [NoSuchElementException] if this deque is empty.
     */
    fun first(): E = if (isEmpty()) throw NoSuchElementException() else internalGet(head)

    /**
     * Returns first element or `null` if this deque is empty.
     */
    fun firstOrNull(): E? = if (isEmpty()) null else internalGet(head)

    /**
     * Returns last element or throws [NoSuchElementException] if this deque is empty.
     */
    fun last(): E = if (isEmpty()) throw NoSuchElementException() else internalGet(decremented(tail))

    /**
     * Returns last element or `null` if this deque is empty.
     */
    fun lastOrNull(): E? = if (isEmpty()) null else internalGet(decremented(tail))

    /**
     * Prepends the specified [element] to this deque.
     */
    fun addFirst(element: E) {
        head = decremented(head)
        elements[head] = element

        if (head == tail) {
            doubleCapacity()
        }
    }

    /**
     * Appends the specified [element] to this deque.
     */
    fun addLast(element: E) {
        elements[tail] = element
        tail = incremented(tail)

        if (head == tail) {
            doubleCapacity()
        }
    }

    /**
     * Removes the first element from this deque and returns that removed element, or throws [NoSuchElementException] if this deque is empty.
     */
    fun removeFirst(): E {
        if (isEmpty())
            throw NoSuchElementException()

        val element = internalGet(head)
        elements[head] = null
        head = incremented(head)
        return element
    }

    /**
     * Removes the first element from this deque and returns that removed element, or returns `null` if this deque is empty.
     */
    fun removeFirstOrNull(): E? = if (isEmpty()) null else removeFirst()

    /**
     * Removes the last element from this deque and returns that removed element, or throws [NoSuchElementException] if this deque is empty.
     */
    fun removeLast(): E {
        if (isEmpty())
            throw NoSuchElementException()

        tail = decremented(tail)
        val element = internalGet(tail)
        elements[tail] = null
        return element
    }

    /**
     * Removes the last element from this deque and returns that removed element, or returns `null` if this deque is empty.
     */
    fun removeLastOrNull(): E? = if (isEmpty()) null else removeLast()

    // MutableList, MutableCollection
    override fun add(element: E): Boolean {
        addLast(element)
        return true
    }

    override fun add(index: Int, element: E) {
        AbstractList.checkPositionIndex(index, size)

        if (index == size) {
            addLast(element)
            return
        } else if (index == 0) {
            addFirst(element)
            return
        }

        if (index < (size + 1) shr 1) {
            // closer to the first element -> move first elements
            val internalIndex = internalIndex(index)

            if (internalIndex < head) { // head > tail, head can't be zero
                elements.copyInto(elements, head - 1, head, elements.size)

                if (internalIndex != 0) {
                    elements[elements.size - 1] = elements[0]
                    elements.copyInto(elements, 0, 1, internalIndex)
                }
            } else { // internalIndex > head, head may be zero
                elements[decremented(head)] = elements[head]
                elements.copyInto(elements, head, head + 1, internalIndex)
            }

            elements[decremented(internalIndex)] = element
            head = decremented(head)
        } else {
            // closer to the last element -> move last elements
            val internalIndex = internalIndex(index)

            if (internalIndex > tail) { // head > tail, internalIndex may be `elements.size - 1`
                elements.copyInto(elements, 1, 0, tail)
                elements[0] = elements[elements.size - 1]
                elements.copyInto(elements, internalIndex + 1, internalIndex, elements.size - 1)
            } else { // internalIndex < tail
                elements.copyInto(elements, internalIndex + 1, internalIndex, tail)
            }

            elements[internalIndex] = element
            tail = incremented(tail)
        }

        if (head == tail) {
            doubleCapacity()
        }
    }

    override fun addAll(elements: Collection<E>): Boolean {
        if (elements.isEmpty())
            return false

        ensureCapacity(this.size + elements.size)

        elements.forEach { element -> addLast(element) }

        return true
    }

    override fun addAll(index: Int, elements: Collection<E>): Boolean {
        AbstractList.checkPositionIndex(index, size)

        if (elements.isEmpty()) {
            return false
        } else if (index == size) {
            return addAll(elements)
        }

        ensureCapacity(this.size + elements.size)

        if (index < (size + 1) shr 1) {
            // closer to the first element -> move first elements
            val internalIndex = internalIndex(index)

            var copyOffset: Int

            if (internalIndex < head) { // head > tail, head can't be zero
                copyOffset = head - elements.size
                check(copyOffset > tail)

                this.elements.copyInto(this.elements, copyOffset, head, this.elements.size)
                copyOffset += this.elements.size - head

                val moveCount = minOf(elements.size, internalIndex)
                this.elements.copyInto(this.elements, copyOffset, 0, moveCount)
                this.elements.copyInto(this.elements, 0, moveCount, internalIndex)
                copyOffset = (copyOffset + internalIndex) and mask

            } else { // internalIndex > head, head may be zero
                copyOffset = (head - elements.size) and mask
                check(copyOffset < head || copyOffset > tail)

                val maxMove = internalIndex - head
                val moveCount = maxOf(0, elements.size - head).coerceAtMost(maxMove)
                this.elements.copyInto(this.elements, copyOffset, head, head + moveCount)
                copyOffset = (copyOffset + moveCount) and mask

                this.elements.copyInto(this.elements, copyOffset, head + moveCount, internalIndex)
                copyOffset = (copyOffset + internalIndex - head - moveCount) and mask
            }

            elements.forEach { element ->
                this.elements[copyOffset] = element
                copyOffset = incremented(copyOffset)
            }
            check(copyOffset == internalIndex)
            head = (head - elements.size) and mask
        } else {
            // closer to the last element -> move last elements
            val internalIndex = internalIndex(index)

            var copyOffset: Int

            if (internalIndex > tail) { // head > tail
                check(tail + elements.size < head)

                this.elements.copyInto(this.elements, elements.size, 0, tail)

                val moveCount = minOf(elements.size, this.elements.size - internalIndex)
                copyOffset = elements.size - moveCount
                this.elements.copyInto(this.elements, copyOffset, this.elements.size - moveCount, this.elements.size)
                copyOffset = (copyOffset - (this.elements.size - moveCount - internalIndex)) and mask
                this.elements.copyInto(this.elements, copyOffset, internalIndex, this.elements.size - moveCount)
            } else { // internalIndex < tail
                copyOffset = (tail + elements.size) and mask
                check(copyOffset < head || copyOffset > tail)

                val maxMove = tail - internalIndex
                val moveCount = maxOf(0, tail + elements.size - this.elements.size).coerceAtMost(maxMove)
                check(copyOffset >= moveCount)
                copyOffset -= moveCount
                this.elements.copyInto(this.elements, copyOffset, tail - moveCount, tail)

                copyOffset = (copyOffset - (tail - moveCount - internalIndex)) and mask
                this.elements.copyInto(this.elements, copyOffset, internalIndex, tail - moveCount)
            }

            copyOffset = (copyOffset - elements.size) and mask

            check(copyOffset == internalIndex)
            elements.forEach { element ->
                this.elements[copyOffset] = element
                copyOffset = incremented(copyOffset)
            }
            tail = (tail + elements.size) and mask
        }

        return true
    }

    override fun get(index: Int): E {
        AbstractList.checkElementIndex(index, size)

        return internalGet(internalIndex(index))
    }

    override fun set(index: Int, element: E): E {
        AbstractList.checkElementIndex(index, size)

        val internalIndex = internalIndex(index)
        val oldElement = internalGet(internalIndex(index))
        elements[internalIndex] = element

        return oldElement
    }

    override fun contains(element: E): Boolean = indexOf(element) != -1

    override fun indexOf(element: E): Int {
        if (head < tail) {
            for (index in head until tail) {
                if (element == elements[index]) return index - head
            }
        } else if (head > tail) {
            for (index in head until elements.size) {
                if (element == elements[index]) return index - head
            }
            for (index in 0 until tail) {
                if (element == elements[index]) return index + elements.size - head
            }
        }

        return -1
    }

    override fun lastIndexOf(element: E): Int {
        if (head < tail) {
            for (index in tail - 1 downTo head) {
                if (element == elements[index]) return index - head
            }
        } else if (head > tail) {
            for (index in tail - 1 downTo 0) {
                if (element == elements[index]) return index + elements.size - head
            }
            for (index in elements.size - 1 downTo head) {
                if (element == elements[index]) return index - head
            }
        }

        return -1
    }

    override fun remove(element: E): Boolean {
        val index = indexOf(element)
        if (index == -1) return false
        removeAt(index)
        return true
    }

    override fun removeAt(index: Int): E {
        AbstractList.checkElementIndex(index, size)

        if (index == lastIndex) {
            return removeLast()
        } else if (index == 0) {
            return removeFirst()
        }

        val element: E
        if (index < size shr 1) {
            // closer to the first element -> move first elements
            val internalIndex = internalIndex(index)
            element = internalGet(internalIndex)

            if (internalIndex < head) {
                elements.copyInto(elements, 1, 0, internalIndex)
                elements[0] = elements[elements.size - 1]
                elements.copyInto(elements, head + 1, head, elements.size - 1)
            } else {
                elements.copyInto(elements, head + 1, head, internalIndex)
            }

            elements[head] = null
            head = incremented(head)
        } else {
            // closer to the last element -> move last elements
            val internalIndex = internalIndex(index)
            element = internalGet(internalIndex)

            tail = decremented(tail)

            if (internalIndex > tail) {
                elements.copyInto(elements, internalIndex, internalIndex + 1, elements.size)
                elements[elements.size - 1] = elements[0]
                elements.copyInto(elements, 0, 1, tail + 1)
            } else {
                elements.copyInto(elements, internalIndex, internalIndex + 1, tail + 1)
            }

            elements[tail] = null
        }

        return element
    }

    override fun removeAll(elements: Collection<E>): Boolean = filterInPlace { !elements.contains(it) }

    override fun retainAll(elements: Collection<E>): Boolean = filterInPlace { elements.contains(it) }

    private inline fun filterInPlace(predicate: (E) -> Boolean): Boolean {
        if (this.isEmpty() || elements.isEmpty())
            return false

        var newTail = head

        if (head < tail) {
            for (index in head until tail) {
                val element = elements[index]

                @Suppress("UNCHECKED_CAST")
                if (predicate(element as E))
                    elements[newTail++] = element
            }

            elements.fill(null, newTail, tail)

        } else {
            for (index in head until elements.size) {
                val element = elements[index]
                elements[index] = null

                @Suppress("UNCHECKED_CAST")
                if (predicate(element as E))
                    elements[newTail++] = element
            }

            for (index in 0 until tail) {
                val element = elements[index]
                elements[index] = null

                @Suppress("UNCHECKED_CAST")
                if (predicate(element as E))
                    elements[newTail++ and mask] = element
            }
        }

        val changed = newTail != tail
        tail = newTail
        return changed
    }

    override fun clear() {
        if (head < tail) {
            elements.fill(null, head, tail)
        } else if (head > tail) {
            elements.fill(null, head, elements.size)
            elements.fill(null, 0, tail)
        }
        head = 0
        tail = 0
    }

    // For testing only
    internal fun internalStructure(structure: (head: Int, elements: Array<Any?>) -> Unit) {
        if (head <= tail) {
            @Suppress("UNCHECKED_CAST")
            structure(head, elements.sliceArray(head until tail))
        } else {
            @Suppress("UNCHECKED_CAST")
            structure(head - elements.size, elements.sliceArray(head until elements.size).plus(elements = elements.sliceArray(0 until tail)))
        }
    }
}