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

package com.flatide.floodgate.agent.flow.rule

import java.text.SimpleDateFormat
import java.util.ArrayList
import java.util.regex.Pattern

class MappingRuleItem(target: String, source: String) {
    enum class RuleType {
        ANY,
        STRING,
        NUMBER,
        DATE
    }

    enum class RuleAction {
        system,
        reference,
        literal,
        function,
        order,
    }

    var sourceName: String = ""
    var sourceType: RuleType = RuleType.ANY
    var sourceTypeSub: String = ""

    var targetName: String = ""
    var targetType: RuleType = RuleType.ANY
    var targetTypeSub: String = ""

    var action: RuleAction = RuleAction.reference

    init {
        this.sourceName = source.trim()
        this.targetName = target.trim()

        if (source.contains(":")) {
            val s = source.split(":")
            this.sourceName = s[0].trim()
            this.sourceType = RuleType.valueOf(s[1].trim())
            if (s.size > 2) {
                this.sourceTypeSub = s[2].trim()
            }
        }

        val first = this.sourceName[0]

        when (first) {
            '{' -> {
                //{CONTEXT.REQUEST_PARAMS.totalcount}
                this.action = RuleAction.system
            }
            '>' -> {
                this.action = RuleAction.function
                this.sourceName = this.sourceName.substring(1)
            }
            '+' -> {
                this.action = RuleAction.literal
                this.sourceName = this.sourceName.substring(1)
            }
            '#' -> {
                this.action = RuleAction.order
                this.sourceName = this.sourceName.substring(1)
            }
            else -> {
            }
        }

        if (target.contains(":")) {
            val t = target.split(":")
            this.targetName = t[0].trim()
            this.targetType = RuleType.valueOf(t[1].trim())
            if (t.size > 2) {
                this.targetTypeSub = t[2].trim()
            }
        }

        if (this.sourceName == "=") {
            this.sourceName = this.targetName
        }

        if (this.targetName == "=") {
            this.targetName = this.sourceName
        }
    }

    /*public <T> String process(T itemList) throws Exception {
        Object value = null;
        if (itemList != null) {
            switch (getAction()) {
                case reference:
                    value = ((Map)itemList).get(getSourceName());
                    break;
                case literal:
//                                다음과 같은 literal + reference의 복합형태를 처리한다
//
//                                "COL_VARCHAR" : "+nvl($CODE$, $CODE$, $SQ$)"
                    String source = getSourceName();

                    Pattern pattern = Pattern.compile("\\$.+?\\$");
                    Matcher matcher = pattern.matcher(source);

                    List<String> key = new ArrayList<>();
                    while (matcher.find()) {
                        key.add(source.substring(matcher.start() + 1, matcher.end() - 1));
                    }

                    for (String k : key) {
                        Object data;
                        data = ((Map) itemList).get(k);

                        if (data == null) {
                            data = "";
                        }
                        source = source.replace("$" + k + "$", String.valueOf(data));
                    }

                    value = source;
                    break;
                case function:
                    // template을 수정해야 하는 함수들을 먼저 처리
                    //v = ""; // v를 String type으로 강제함
                    value = preprocessFunc(this);
                    break;
                case order:
                    value = ((List)itemList).get(Integer.valueOf(getSourceName()));
                    break;
            }

            switch(getSourceType()) {
                case DATE:
                    String dateFormat = getSourceTypeSub();
                    SimpleDateFormat format = new SimpleDateFormat(dateFormat);
                    java.util.Date date = format.parse((String) value);
                    value = new java.sql.Date(date.getTime());
                    break;
                default:
                    break;
            }


            if (getTargetType() == MappingRuleItem.RuleType.DATE) {
                String dateFormat = getTargetTypeSub();
                SimpleDateFormat format = new SimpleDateFormat(dateFormat);

                value = format.format(value);
            }
        } else {    // Header나 Footer에서 Column 생성인 경우( JDBC PreparedStatement 생성)
            switch (getAction()) {
                case reference:
                    value = "?";
                    param.add(getSourceName());
                    break;
                case literal:
//                                    다음과 같은 literal + reference의 복합형태를 처리한다
//
//                                    "COL_VARCHAR" : "+nvl($CODE$, $CODE$, $SQ$)"

                    String source = getSourceName();

                    Pattern pattern = Pattern.compile("\\$.+?\\$");
                    Matcher matcher = pattern.matcher(source);

                    boolean find = false;
                    while (matcher.find()) {
                        find = true;
                        String key = source.substring(matcher.start() + 1, matcher.end() - 1);
                        param.add(key);
                    }

                    if( find ) {
                        source = source.replaceAll("\\$.+?\\$", "\\?");
                        value = source;
                    } else {
                        value = getSourceName();
                    }
                    break;
                case function:
                    // template을 수정해야 하는 함수들을 먼저 처리
                    Object v = processEmbedFunction(this);
                    if( v == null ) {
                        value = "";
                    } else {
                        value = v;
                    }
                    break;
                default:
                    break;
            }
        }
        return String.valueOf(value);
    }*/
}
