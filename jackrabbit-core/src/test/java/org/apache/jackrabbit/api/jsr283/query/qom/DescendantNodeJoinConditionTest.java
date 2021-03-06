/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.jackrabbit.api.jsr283.query.qom;

import java.util.List;
import java.util.ArrayList;

import javax.jcr.Node;
import javax.jcr.RepositoryException;

import org.apache.jackrabbit.spi.commons.query.jsr283.qom.JoinCondition;
import org.apache.jackrabbit.spi.commons.query.jsr283.qom.QueryObjectModel;

/**
 * <code>DescendantNodeJoinConditionTest</code> contains test cases that cover
 * <code>DescendantNodeJoinCondition</code>.
 */
public class DescendantNodeJoinConditionTest extends AbstractJoinTest {

    private Node n1;

    private Node n2;

    protected void setUp() throws Exception {
        super.setUp();
        n1 = testRootNode.addNode(nodeName1, testNodeType);
        n2 = n1.addNode(nodeName2, testNodeType);
        n2.addMixin(mixReferenceable);
        testRootNode.save();
    }

    public void testInnerJoin() throws RepositoryException {
        JoinCondition c = qomFactory.descendantNodeJoinCondition(LEFT, RIGHT);
        QueryObjectModel qom = createQuery(JOIN_TYPE_INNER, c);
        checkResult(qom.execute(), new Node[][]{{n2, n1}});
    }

    public void testRightOuterJoin() throws RepositoryException {
        JoinCondition c = qomFactory.descendantNodeJoinCondition(LEFT, RIGHT);
        QueryObjectModel qom = createQuery(JOIN_TYPE_RIGHT_OUTER, c);
        checkResult(qom.execute(), new Node[][]{{n2, n1}, {null, n2}});
    }

    public void testLeftOuterJoin() throws RepositoryException {
        JoinCondition c = qomFactory.descendantNodeJoinCondition(LEFT, RIGHT);
        QueryObjectModel qom = createQuery(JOIN_TYPE_LEFT_OUTER, c);
        List result = new ArrayList();
        result.add(new Node[]{n2, n1});
        // for each ancestor-or-self of testRootNode check
        // whether it is of type testNodeType and add
        // two matches in that case
        Node n = testRootNode;
        for (;;) {
            if (n.isNodeType(testNodeType)) {
                result.add(new Node[]{n1, n});
                result.add(new Node[]{n2, n});
            }
            if (n.getDepth() == 0) {
                break;
            } else {
                n = n.getParent();
            }
        }
        if (result.size() == 1) {
            // n1 not yet covered
            result.add(new Node[]{n1, null});
        }
        checkResult(qom.execute(), (Node[][]) result.toArray(new Node[result.size()][]));
    }
}
