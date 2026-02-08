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

class ParseException(message: String, val lineNumber: Int) : RuntimeException("Line $lineNumber: $message")

object TemplateParser {

    fun parse(rawLines: List<String>): TemplateNode.Root {
        val lines = preprocess(rawLines).toMutableList()
        val children = parseChildren(lines, expectElse = false)
        return TemplateNode.Root(children = children)
    }

    // template의 라인 연결(\)를 처리한다 — ported from DocumentTemplate.preprocess()
    internal fun preprocess(original: List<String>): List<String> {
        val result = ArrayList<String>()
        var buffer = ""
        var conjunction = false

        for (s in original) {
            var current = s
            if (current.trim().endsWith("\\")) {
                if (!conjunction) {
                    conjunction = true
                    result.add(buffer)
                    buffer = ""
                }
                current = current.substring(0, current.lastIndexOf("\\"))
            } else {
                if (!conjunction) {
                    result.add(buffer)
                    buffer = ""
                } else {
                    conjunction = false
                }
            }
            buffer += current
        }
        if (buffer.isNotEmpty()) {
            result.add(buffer)
        }

        return result
    }

    // Tokenizer for tag attributes — ported from TemplatePart.tokenize()
    internal fun tokenize(str: String): MutableList<String> {
        var state = 0

        var i = 0
        var token = StringBuilder()
        val result = ArrayList<String>()
        while (i < str.length) {
            val ch = str[i]
            if (ch == '\\') {
                i++
                if (i < str.length) {
                    val next = str[i]
                    if (next != '\\' && next != '\"' && next != '\'') {
                        if (next == 'n' || next == 'r' || next == 't' || next == 'b') {
                            when (next) {
                                'n' -> token.append('\n')
                                'r' -> token.append('\r')
                                't' -> token.append('\t')
                                'b' -> token.append('\b')
                            }
                            i++
                            continue
                        } else {
                            token.append(ch)
                        }
                    }
                    token.append(next)
                }
                i++
                continue
            }
            when (state) {
                0 -> { // normal
                    if (ch == '"') {
                        state = 1
                    } else if (ch == '\'') {
                        state = 2
                    } else if (ch == ' ' || ch == '\t' || ch == '\n' || ch == '\r') {
                        if (token.isNotEmpty()) {
                            result.add(token.toString())
                        }
                        token = StringBuilder()
                    } else {
                        token.append(ch)
                    }
                }
                1 -> { // in Double Quotes
                    if (ch == '"') {
                        result.add(token.toString())
                        token = StringBuilder()
                        state = 0
                    } else {
                        token.append(ch)
                    }
                }
                2 -> { // in Single Quotes
                    if (ch == '\'') {
                        result.add(token.toString())
                        token = StringBuilder()
                        state = 0
                    } else {
                        token.append(ch)
                    }
                }
            }

            i++
        }
        if (token.isNotEmpty()) {
            result.add(token.toString())
        }

        return result
    }

    // Recursive descent parser — ported from TemplatePart.init
    // if~else~end 구조에서 else는 end와 else가 중첩되어 있는 것과 같으므로
    // if를 처리할 때 flag를 설정하여 else가 한번은 end로, 한번은 else로
    // 처리될 수 있도록 한다.
    // if~end~else~end의 축약형태를 지원하기 위함
    private fun parseChildren(lines: MutableList<String>, expectElse: Boolean): List<TemplateNode> {
        val children = ArrayList<TemplateNode>()
        val textBuffer = ArrayList<String>()
        var localExpectElse = expectElse

        while (lines.size > 0) {
            val cur = lines.removeAt(0)
            if (cur.trim().startsWith("#")) {
                val inner = cur.trim().substring(1)
                if (inner.trim().startsWith("#")) {
                    // # escape — literal #
                    textBuffer.add(inner)
                } else if (inner.startsWith("else") && !localExpectElse) {
                    // if 블럭을 끝내고 else를 다시 처리하기 위해
                    lines.add(0, "#else")
                    break
                } else if (inner == "end") {
                    break
                } else {
                    localExpectElse = false

                    // Flush text buffer
                    if (textBuffer.size > 0) {
                        children.add(makeTextNode(textBuffer))
                    }

                    if (inner == "br") {
                        children.add(TemplateNode.LineBreak)
                    } else {
                        val newExpectElse = inner.startsWith("if")
                        val node = parseTag(inner, lines, newExpectElse)
                        children.add(node)
                    }
                    textBuffer.clear()
                }
            } else {
                textBuffer.add(cur)
            }
        }
        if (textBuffer.size > 0) {
            children.add(makeTextNode(textBuffer))
        }

        return children
    }

    private fun makeTextNode(lines: List<String>): TemplateNode.Text {
        val builder = StringBuilder()
        for (line in lines) builder.append(line + "\n")
        var content = builder.toString()
        content = content.substring(0, content.lastIndexOf("\n"))
        return TemplateNode.Text(content)
    }

    private fun parseTag(tag: String, lines: MutableList<String>, expectElse: Boolean): TemplateNode {
        val trimmedTag = tag.trim()
        val tokens = tokenize(trimmedTag)
        val name = if (tokens.size > 0) tokens.removeAt(0) else ""

        val attributes = HashMap<String, MutableList<String>>()
        for (t in tokens) {
            val keyValue = t.split("=".toRegex())
            if (keyValue.size != 2) {
                println("Invalid Tag Parameter Format : $trimmedTag ### $t")
                continue
            }
            val values = attributes.getOrPut(keyValue[0]) { ArrayList() }
            values.add(keyValue[1])
        }

        val immutableAttrs: Map<String, List<String>> = attributes.mapValues { it.value.toList() }
        val children = parseChildren(lines, expectElse)

        return when (name) {
            "root" -> TemplateNode.Root(immutableAttrs, children)
            "header" -> TemplateNode.Header(immutableAttrs, children)
            "body" -> TemplateNode.Body(immutableAttrs, children)
            "footer" -> TemplateNode.Footer(immutableAttrs, children)
            "row" -> TemplateNode.Row(immutableAttrs, children)
            "column" -> TemplateNode.Column(immutableAttrs, children)
            "if" -> TemplateNode.If(immutableAttrs, children)
            "else" -> TemplateNode.Else(immutableAttrs, children)
            else -> TemplateNode.Root(immutableAttrs, children) // fallback
        }
    }
}
