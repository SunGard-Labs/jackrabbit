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
package org.apache.jackrabbit.core.query.lucene;

import java.io.IOException;

import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortComparatorSource;
import org.apache.lucene.index.IndexReader;
import org.apache.jackrabbit.core.query.lucene.join.Join;
import org.apache.jackrabbit.core.HierarchyManager;
import org.apache.jackrabbit.spi.commons.query.qom.JoinConditionImpl;

/**
 * <code>JoinQuery</code> implements a query that performs a join.
 */
public class JoinQuery implements MultiColumnQuery {

    /**
     * The left side of the join.
     */
    private final MultiColumnQuery left;

    /**
     * The right side of the join.
     */
    private final MultiColumnQuery right;

    /**
     * The join type.
     */
    private final int joinType;

    /**
     * The QOM join condition.
     */
    private final JoinConditionImpl joinCondition;

    /**
     * The sort comparator source of the index.
     */
    private final SortComparatorSource scs;

    /**
     * The hierarchy manager of the workspace.
     */
    private final HierarchyManager hmgr;

    /**
     * Creates a new join query.
     *
     * @param left          the left side of the query.
     * @param right         the right side of the query.
     * @param joinType      the join type.
     * @param joinCondition the join condition.
     * @param scs           the sort comparator source of the index.
     * @param hmgr          the hierarchy manager of the workspace.
     */
    public JoinQuery(MultiColumnQuery left,
                     MultiColumnQuery right,
                     int joinType,
                     JoinConditionImpl joinCondition,
                     SortComparatorSource scs,
                     HierarchyManager hmgr) {
        this.left = left;
        this.right = right;
        this.joinType = joinType;
        this.joinCondition = joinCondition;
        this.scs = scs;
        this.hmgr = hmgr;
    }

    /**
     * {@inheritDoc}
     */
    public MultiColumnQueryHits execute(JackrabbitIndexSearcher searcher,
                                        Sort sort,
                                        long resultFetchHint)
            throws IOException {
        IndexReader reader = searcher.getIndexReader();
        HierarchyResolver resolver = (HierarchyResolver) reader;
        return Join.create(left.execute(searcher, sort, resultFetchHint),
                right.execute(searcher, sort, resultFetchHint),
                joinType, joinCondition, reader, resolver, scs, hmgr);
    }
}
