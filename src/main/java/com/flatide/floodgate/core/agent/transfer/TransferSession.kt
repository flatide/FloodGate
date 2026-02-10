/*
 * MIT License
 *
 * Copyright (c) 2022 FLATIDE LC.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.flatide.floodgate.core.agent.transfer

import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

enum class TransferStatus {
    READY,
    TRANSFERRING,
    COMPLETED,
    FAILED,
    CANCELLED
}

class TransferSession(
    val transferId: String,
    val datasourceId: String,
    val filename: String,
    val totalBytes: Long,
    capacity: Int
) {
    companion object {
        val END_SENTINEL = ByteArray(0)
    }

    val queue = ArrayBlockingQueue<ByteArray>(capacity)
    val bytesTransferred = AtomicLong(0)
    val status = AtomicReference(TransferStatus.READY)
    val startTime = System.currentTimeMillis()
    var errorMessage: String? = null

    fun addBytes(count: Long) {
        bytesTransferred.addAndGet(count)
    }

    fun getElapsedMillis(): Long {
        return System.currentTimeMillis() - startTime
    }

    fun getBytesPerSecond(): Double {
        val elapsed = getElapsedMillis()
        if (elapsed == 0L) return 0.0
        return bytesTransferred.get().toDouble() / (elapsed / 1000.0)
    }

    fun getPercentage(): Double {
        if (totalBytes <= 0) return 0.0
        return (bytesTransferred.get().toDouble() / totalBytes) * 100.0
    }
}
