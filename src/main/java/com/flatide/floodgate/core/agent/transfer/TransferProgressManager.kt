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

import java.util.concurrent.ConcurrentHashMap

object TransferProgressManager {
    private val sessions = ConcurrentHashMap<String, TransferSession>()

    fun register(session: TransferSession) {
        sessions[session.transferId] = session
    }

    fun get(transferId: String): TransferSession? {
        return sessions[transferId]
    }

    fun remove(transferId: String): TransferSession? {
        return sessions.remove(transferId)
    }

    fun getActiveSessions(): List<TransferSession> {
        return sessions.values.toList()
    }

    fun getActiveCount(): Int {
        return sessions.values.count {
            it.status.get() == TransferStatus.READY || it.status.get() == TransferStatus.TRANSFERRING
        }
    }
}
