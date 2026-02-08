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

package com.flatide.floodgate.core.agent.connector

import com.flatide.floodgate.core.agent.Context
import com.flatide.floodgate.core.agent.template.DocumentTemplate
import com.flatide.floodgate.core.agent.flow.module.Module
import com.flatide.floodgate.core.agent.flow.rule.FunctionProcessor
import com.flatide.floodgate.core.agent.flow.rule.MappingRule

interface Connector {
    var documentTemplate: DocumentTemplate?
    fun getFunctionProcessor(type: String): FunctionProcessor?

    fun connect(context: Context, module: Module)

    fun check()

    fun count()

    fun beforeRead(rule: MappingRule)

    fun read(rule: MappingRule): List<Map<Any?, Any?>>

    fun readPartially(rule: MappingRule): List<Map<Any?, Any?>>

    fun readBuffer(rule: MappingRule, buffer: MutableList<Any?>, limit: Int): Int

    fun afterRead()

    fun beforeCreate(rule: MappingRule)

    fun create(items: List<Map<Any?, Any?>>, mappingRule: MappingRule): Int

    fun createPartially(items: List<Map<Any?, Any?>>, mappingRule: MappingRule): Int

    fun afterCreate(rule: MappingRule)

    fun update(mappingRule: MappingRule, data: Any?): Int

    fun delete(): Int

    fun commit()

    fun rollback()

    fun close()

    fun getSent(): Int
    fun getErrorPosition(): Int
}
