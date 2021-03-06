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
package org.apache.jackrabbit.core.security.user;

import org.apache.jackrabbit.core.SessionImpl;
import org.apache.jackrabbit.test.NotExecutableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.RepositoryException;
import javax.jcr.Session;

/** <code>TraversingNodeResolverTest</code>... */
public class TraversingNodeResolverTest extends NodeResolverTest {

    private static Logger log = LoggerFactory.getLogger(TraversingNodeResolverTest.class);

    protected NodeResolver createNodeResolver(Session session) throws RepositoryException, NotExecutableException {
        if (!(session instanceof SessionImpl)) {
            throw new NotExecutableException();
        }
        return new TraversingNodeResolver(session, (SessionImpl) session);
    }
}