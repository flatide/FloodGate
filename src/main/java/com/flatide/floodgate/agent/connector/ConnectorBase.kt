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

package com.flatide.floodgate.agent.connector

import com.flatide.floodgate.agent.flow.rule.FunctionProcessor
import com.flatide.floodgate.agent.template.DocumentTemplate

abstract class ConnectorBase : Connector {
    protected var name: String? = null

    override var documentTemplate: DocumentTemplate? = null

    override fun check() {}

    override fun count() {}

    /*
    @Override
    //public final long createForStream(Payload payload, MappingRule mappingRule) throws Exception {
    public final long create(Payload payload, MappingRule mappingRule) throws Exception {
        //beforeCreate(mappingRule);

        long sent = 0;
        List<Map<String, Object>> itemList = new LinkedList<>();

        try {
            while (payload.next() != -1) {
                Object dataList = payload.getData();
                long length = payload.getReadLength();

                if (dataList instanceof List) {
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> temp = (List<Map<String, Object>>) dataList;
                    itemList.addAll(temp);

                    // TODO 스트리밍이 아닌 경우에 대한 배치 처리를 위한 Method를 만들 것
                    if (this.batchSize > 0 && itemList.size() >= this.batchSize) {   // batch size
                        List<Map<String, Object>> sub = itemList.subList(0, this.batchSize);
                        creating(sub, mappingRule, sent, this.batchSize);
                        sent += this.batchSize;
                        sub.clear();
                    }
                } else {
                    creatingBinary((byte[]) dataList, length, sent);
                    sent += length;
                }
            }

            if (itemList.size() > 0) {
                creating(itemList, mappingRule, sent, this.batchSize);
                sent += itemList.size();
                itemList.clear();
            }
            String after = this.context.getStringDefault("SEQUENCE.AFTER", "COMMIT");
            if ("COMMIT".equals(after)) {
                commit();
            } else if ("ROLLBACK".equals(after)) {
                rollback();
            }
        } catch (Exception e) {
            rollback();
        }

        afterCreate(mappingRule);

        return sent;
    }

    public void beforeCreate(MappingRule mappingRule) throws Exception {}
    */

    override fun getFunctionProcessor(type: String): FunctionProcessor? {
        return null
    }
}
