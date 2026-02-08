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

package com.flatide.floodgate.core.agent.flow.rule

import org.apache.logging.log4j.LogManager
import java.text.SimpleDateFormat
import java.util.ArrayList
import java.util.regex.Pattern

class MappingRule {
    companion object {
        private val logger = LogManager.getLogger(MappingRule::class.java)
    }

    enum class Function {
        TARGET_DATE,
        DATE,
        SEQ
    }

    private val rules: MutableList<MappingRuleItem> = ArrayList()
    var functionProcessor: FunctionProcessor? = null

    // for JDBC PreparedStatement
    private val param: MutableList<String> = ArrayList()

    fun getParam(): List<String> {
        return this.param
    }

    fun addRule(rules: Map<String, String>) {
        for ((key, value) in rules) {
            val item = MappingRuleItem(key, value)
            this.rules.add(item)
        }
    }

    /*public void addRule(String target, String source) {
        this.rules.add(new MappingRuleItem(target, source));
    }*/

    fun getRules(): List<MappingRuleItem> {
        return rules
    }

    @Suppress("UNCHECKED_CAST")
    fun <T> apply(item: MappingRuleItem, columnData: T): String? {
        var value: Any? = null
        when (item.action) {
            MappingRuleItem.RuleAction.system -> {
                value = "?"
                param.add(item.sourceName)
            }
            MappingRuleItem.RuleAction.reference -> {
                value = (columnData as Map<String, Any>).get(item.sourceName)
                param.add(item.sourceName)
            }
            MappingRuleItem.RuleAction.literal -> {
                /*
                    다음과 같은 literal + reference의 복합형태도 처리해야 한다

                    "COL_VARCHAR" : "+nvl($CODE$, $CODE$, $SQ$)"
                 */
                var source = item.sourceName

                val pattern = Pattern.compile("\\$.+?\\$")
                val matcher = pattern.matcher(source)

                val key: MutableList<String> = ArrayList()
                while (matcher.find()) {
                    key.add(source.substring(matcher.start() + 1, matcher.end() - 1))
                }

                for (k in key) {
                    var data: Any?
                    data = (columnData as Map<String, Any>).get(k)

                    if (data == null) {
                        data = ""
                    }
                    source = source.replace("$${k}$", data.toString())
                    param.add(k)
                }

                value = source
            }
            MappingRuleItem.RuleAction.function -> {
                // template을 수정해야 하는 함수들을 먼저 처리
                //v = ""; // v를 String type으로 강제함
                if (this.functionProcessor != null) {
                    value = this.functionProcessor!!.process(item)
                }

                if (value == "?") {
                    // jdbc template은 creating에서 다시 function을 평가해야 한다
                    param.add(">" + item.sourceName)
                }
            }
            MappingRuleItem.RuleAction.order -> {
                value = (columnData as List<Any>).get(Integer.valueOf(item.sourceName))
            }
        }

        if (value == null) {
            return null
        }

        try {
            when (item.sourceType) {
                MappingRuleItem.RuleType.DATE -> {
                    val dateFormat = item.sourceTypeSub
                    val format = SimpleDateFormat(dateFormat)
                    val date = format.parse(value as String)
                    value = java.sql.Date(date.time)
                }
                else -> {
                }
            }
        } catch (e: IllegalArgumentException) {
            logger.info(e.message)
        }

        if (item.targetType == MappingRuleItem.RuleType.DATE) {
            val dateFormat = item.targetTypeSub
            val format = SimpleDateFormat(dateFormat)

            try {
                value = format.format(value)
            } catch (e: IllegalArgumentException) {
                // Ignore format error for jdbc template, '?'
                logger.info(e.message)
            }
        }
        return value.toString()
    }

    /*
        preparedStatement에 파라메터로 들어갈 수 없는 SQL function등을 리턴한다

        processFuntionForTemplate으로 이름을 변경하면 좋을 것. 20201223
     */
    /*String preprocessFunc(MappingRuleItem item) {
        String func = item.getSourceName();
        Function function = Function.valueOf(func);

        switch (function) {
            case TARGET_DATE:
                //item.setAction(MappingRuleItem.RuleAction.literal);
                return "now()";
            default:
                return "?";
        }
    }*/
}
