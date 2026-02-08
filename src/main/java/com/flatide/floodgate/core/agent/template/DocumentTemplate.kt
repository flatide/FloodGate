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

import com.flatide.floodgate.core.ConfigurationManager
import com.flatide.floodgate.core.FloodgateConstants
import com.flatide.floodgate.core.agent.Context
import com.flatide.floodgate.core.agent.flow.rule.MappingRule
import com.flatide.floodgate.core.agent.flow.rule.MappingRuleItem
import com.flatide.floodgate.core.agent.meta.MetaManager

import org.apache.logging.log4j.LogManager

import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern

class DocumentTemplate private constructor() {
    private var root: TemplateNode.Root? = null

    private fun findSection(type: Class<out TemplateNode>): TemplateNode.ContainerNode? {
        val children = root?.children ?: return null
        for (child in children) {
            if (type.isInstance(child)) {
                return child as TemplateNode.ContainerNode
            }
        }
        return null
    }

    @Suppress("UNCHECKED_CAST")
    fun <T> makeHeader(context: Context, rules: MappingRule, itemList: List<T>?): String {
        val part = findSection(TemplateNode.Header::class.java) ?: return ""
        // header와 footer에서 첫번째 컬럼의 tag name등을 처리하기 위해
        var columnData: T? = null
        if (itemList != null) {
            columnData = itemList[0]
        }
        return processNode(part, context, rules, itemList, columnData, 0)
    }

    @Suppress("UNCHECKED_CAST")
    fun <T> makeFooter(context: Context, rules: MappingRule, itemList: List<T>?): String {
        val part = findSection(TemplateNode.Footer::class.java) ?: return ""
        // header와 footer에서 첫번째 컬럼의 tag name등을 처리하기 위해
        var columnData: T? = null
        if (itemList != null) {
            columnData = itemList[0]
        }
        return processNode(part, context, rules, itemList, columnData, 0)
    }

    fun <T> makeBody(context: Context, rules: MappingRule, itemList: List<T>?, index: Long): String {
        val part = findSection(TemplateNode.Body::class.java) ?: return ""
        return processNode(part, context, rules, itemList, null, index)
    }

    private fun <T> processNode(node: TemplateNode.ContainerNode, context: Context, rules: MappingRule, itemList: List<T>?, columnData: T?, index: Long): String {
        val builder = StringBuilder()
        var isTrue = false  // if else 구문을 위한 flag
        for (child in node.children) {
            when (child) {
                is TemplateNode.Text -> {
                    val content = context.evaluate(child.content)
                    builder.append(content)
                }
                is TemplateNode.Row -> {
                    val row = processRow(child, context, rules, itemList, index)
                    builder.append(row)
                }
                is TemplateNode.LineBreak -> {
                    builder.append("\r\n")
                }
                is TemplateNode.Column -> {
                    val column = processColumn(child, context, columnData, rules)
                    builder.append(column)
                }
                is TemplateNode.If -> {
                    var condition: String = child.getAttribute("condition")
                    if (condition.isEmpty()) {
                        condition = "false"
                    }

                    if (context.evaluate(condition).equals("true", ignoreCase = true)) {
                        isTrue = true
                        val result = processNode(child, context, rules, itemList, columnData, index)
                        builder.append(result)
                    } else {
                        isTrue = false
                    }
                }
                is TemplateNode.Else -> {
                    if (!isTrue) {
                        val result = processNode(child, context, rules, itemList, columnData, index)
                        builder.append(result)
                    }
                }
                is TemplateNode.Root, is TemplateNode.Header,
                is TemplateNode.Body, is TemplateNode.Footer -> {
                    // skip — these are section markers, not renderable inline
                }
            }
        }

        return builder.toString()
    }

    private fun <T> processRow(row: TemplateNode.Row, context: Context, rules: MappingRule, itemList: List<T>?, index: Long): String {
        var rowDelimiter: String = row.getAttribute("delimiter")
        rowDelimiter = context.evaluate(rowDelimiter)
        rowDelimiter = rowDelimiter.replace("\\r", "\\s")
        rowDelimiter = rowDelimiter.replace("\\n", "\r\n")

        if (itemList != null) {
            val builder = StringBuilder()
            var idx = index

            for (item in itemList) {
                if (idx > 0) {
                    builder.append(rowDelimiter)
                }
                val applied = processNode(row, context, rules, itemList, item, idx)
                builder.append(applied)
                idx++
            }

            return builder.toString()
        } else {
            return processNode(row, context, rules, null, null, 0)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> processColumn(column: TemplateNode.Column, context: Context, columnData: T?, rules: MappingRule): String {
        val builder = StringBuilder()

        var delimiter = column.getAttribute("delimiter")
        delimiter = context.evaluate(delimiter)
        delimiter = delimiter.replace("\\r", "\\s")
        delimiter = delimiter.replace("\\n", "\r\n")

        for (child in column.children) {
            if (child is TemplateNode.Text) {
                val col = child.content
                var i = 0
                for (item in rules.getRules()) {
                    var result = col
                    if (i > 0) {
                        builder.append(delimiter)
                    }

                    if (col.contains("\$SOURCE\$")) {
                        var source: String? = rules.apply(item, columnData)
                        if (source != null) {
                            source = processConditionalAttributes(column, context, source)

                            if ("true" != column.getAttribute("ignoreType")) {
                                if (item.targetType != MappingRuleItem.RuleType.NUMBER) {
                                    source = "\"" + source + "\""
                                }
                            }

                            if (source != null) {
                                result = result.replace("\$SOURCE\$", source)
                            }
                        }

                        if (source == null) {
                            //logger.info(item.getSourceName() + " is not exist.");
                            //TODO Source가 없을 경우 empty로 계속 진행할 것인지 Exception을 발생시키고 중단할 것인지 결정할 것. 2021.03.15
                            result = ""
                            //throw new Exception(item.getSourceName() + " is not exist.");
                        }
                    }

                    result = result.replace("?SOURCE?", item.sourceName)
                    result = result.replace("?TARGET?", item.targetName)

                    builder.append(result)
                    i++
                }
            }   // 중첩 허용 안함
        }

        return builder.toString()
    }

    private fun processConditionalAttributes(column: TemplateNode.Column, context: Context, value: String?): String? {
        if (value == null) {
            return null
        }

        var currentValue: String = value
        var j = -1
        while (true) {
            val findGroup = HashSet<String>()

            j++
            var condition = column.getAttribute("if", j)
            if (condition.isEmpty()) {
                break
            }

            condition = context.evaluate(condition)
            if (condition.startsWith("regx:")) {
                val regxCondition = condition.substring(5)

                val pattern = Pattern.compile(regxCondition)
                val matcher = pattern.matcher(currentValue)
                while (matcher.find()) {
                    findGroup.add(currentValue.substring(matcher.start(), matcher.end()))
                }
                if (findGroup.size == 0) {
                    continue
                }
            } else if (!condition.equals("true", ignoreCase = true)) {
                continue
            }

            var then = column.getAttribute("then", j)
            if (then.isEmpty()) {
                continue
            }

            if (then.startsWith("replace:")) {
                then = then.substring(8)
                //TODO  다음과 같이 공백을 포함한 파라메터를 처리할 수 있도록 할 것 - 2021.03.08
                //      replace:$ 'pre $ post'
                val replaceParam = then.split(" ".toRegex()).toTypedArray()
                // $를 현재 조작하고 있는 문자열로 변환
                replaceParam[0] = replaceParam[0].replace("$", currentValue)
                replaceParam[1] = replaceParam[1].replace("$", currentValue)

                if (replaceParam[0] == "#") {
                    for (f in findGroup) {
                        val rep = replaceParam[1].replace("#", f)
                        currentValue = currentValue.replace(f, rep)
                    }
                } else {
                    currentValue = currentValue.replace(replaceParam[0], replaceParam[1])
                }
            } else if (then.startsWith("escape:")) {
                then = then.substring(7)
                then = then.replace("$", currentValue)    // $를 현재 조작하고 있는 문자열로 변환

                if (then == "#") {
                    for (f in findGroup) {
                        val rep: String = when (f) {
                            "\n" -> "\\n"
                            "\r" -> "\\r"
                            "\t" -> "\\t"
                            "\b" -> "\\b"
                            else -> "\\" + f
                        }
                        currentValue = currentValue.replace(f, rep)
                    }
                } else {
                    currentValue = currentValue.replace(then, "\\" + then)
                }
            } else {
                then = then.replace("$", currentValue)
                currentValue = then
            }
        }

        return currentValue
    }

    companion object {
        private val logger = LogManager.getLogger(DocumentTemplate::class.java)

        private val templateCache: MutableMap<String, DocumentTemplate> = ConcurrentHashMap()

        /*
         * builtInTemplate : Pre-defined template located in src/main/resources
         */
        @Suppress("UNCHECKED_CAST")
        fun get(name: String?, builtInTemplate: String, cache: Boolean): DocumentTemplate {
            val effectiveName = name ?: ""

            var documentTemplate: DocumentTemplate? = null
            if (cache) {
                documentTemplate = templateCache[effectiveName]
            }

            if (documentTemplate == null || !cache) {
                documentTemplate = DocumentTemplate()

                var lines: List<String> = ArrayList()

                if (effectiveName.isNotEmpty()) {
                    val table = ConfigurationManager.getString(FloodgateConstants.META_SOURCE_TABLE_FOR_TEMPLATE)!!
                    val templateInfo = MetaManager.read(table, effectiveName)
                    val templateData = templateInfo?.get("DATA") as? Map<String, Any>
                    if (templateData != null) {
                        val read = templateData["TEMPLATE"] as String
                        lines = read.split("\\R".toRegex())
                    }
                } else {
                    val inputStream = Thread.currentThread().contextClassLoader
                        .getResourceAsStream("$builtInTemplate.template")
                        ?: throw IllegalArgumentException("Built-in template '$builtInTemplate.template' not found")
                    lines = inputStream.bufferedReader(Charsets.UTF_8).use { it.readLines() }
                }

                documentTemplate.root = TemplateParser.parse(lines)

                templateCache[effectiveName] = documentTemplate
            }

            return documentTemplate
        }
    }
}
