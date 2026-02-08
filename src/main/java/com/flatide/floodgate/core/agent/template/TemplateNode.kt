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

package com.flatide.floodgate.core.agent.template

sealed class TemplateNode {

    data class Text(val content: String) : TemplateNode()

    object LineBreak : TemplateNode()

    sealed class ContainerNode : TemplateNode() {
        abstract val attributes: Map<String, List<String>>
        abstract val children: List<TemplateNode>

        fun getAttribute(key: String, index: Int = 0): String {
            val values = attributes[key] ?: return ""
            return if (index < values.size) values[index] else ""
        }

        fun getAttributes(key: String): List<String>? {
            return attributes[key]
        }
    }

    data class Root(
        override val attributes: Map<String, List<String>> = emptyMap(),
        override val children: List<TemplateNode> = emptyList()
    ) : ContainerNode()

    data class Header(
        override val attributes: Map<String, List<String>> = emptyMap(),
        override val children: List<TemplateNode> = emptyList()
    ) : ContainerNode()

    data class Body(
        override val attributes: Map<String, List<String>> = emptyMap(),
        override val children: List<TemplateNode> = emptyList()
    ) : ContainerNode()

    data class Footer(
        override val attributes: Map<String, List<String>> = emptyMap(),
        override val children: List<TemplateNode> = emptyList()
    ) : ContainerNode()

    data class Row(
        override val attributes: Map<String, List<String>> = emptyMap(),
        override val children: List<TemplateNode> = emptyList()
    ) : ContainerNode()

    data class Column(
        override val attributes: Map<String, List<String>> = emptyMap(),
        override val children: List<TemplateNode> = emptyList()
    ) : ContainerNode()

    data class If(
        override val attributes: Map<String, List<String>> = emptyMap(),
        override val children: List<TemplateNode> = emptyList()
    ) : ContainerNode()

    data class Else(
        override val attributes: Map<String, List<String>> = emptyMap(),
        override val children: List<TemplateNode> = emptyList()
    ) : ContainerNode()
}
