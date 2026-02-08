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

package com.flatide.floodgate.agent.flow

import com.flatide.floodgate.agent.Context
import com.flatide.floodgate.agent.flow.stream.FGInputStream
import com.flatide.floodgate.agent.flow.stream.Payload
import com.flatide.floodgate.agent.flow.module.Module
import com.flatide.floodgate.agent.flow.rule.MappingRule

class FlowContext(id: String, flowData: Map<String, Any>) : Context() {
    var debug: Boolean = false

    var id: String = id

    //Map<String, Object> flowData;

    var entry: String = ""

    var modules: MutableMap<String, Module> = HashMap()

    var rules: MutableMap<String, MappingRule> = HashMap()

    var previousModule: Module? = null
    var currentModule: Module? = null
    var nextModule: Module? = null

    // Input Data
    var current: FGInputStream? = null

    var payload: Payload? = null

    init {
        super.add("FLOW", flowData)
        //this.flowData = flowData;
    }

    fun hasNext(): Boolean {
        return this.nextModule != null
    }

    fun setNext(id: String?): Module? {
        val module = this.modules[id]
        this.nextModule = module

        return module
    }

    fun next(): Module? {
        this.previousModule = this.currentModule
        this.currentModule = this.nextModule
        this.nextModule = null

        return this.currentModule
    }
}
