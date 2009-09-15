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
package org.apache.jackrabbit.core.config;

/**
 * Cluster configuration. This includes the journal configuration
 * {@link JournalConfig}.
 */
public class ClusterConfig {

    /**
     * Identifier.
     */
    private final String id;

    /**
     * Sync delay.
     */
    private final long syncDelay;

    /**
     * Journal configuration.
     */
    private final JournalConfig jc;

    /**
     * Creates a new cluster configuration.
     *
     * @param id custom cluster node id
     * @param syncDelay syncDelay, in milliseconds
     * @param jc journal configuration
     */
    public ClusterConfig(String id, long syncDelay, JournalConfig jc) {
        this.id = id;
        this.syncDelay = syncDelay;
        this.jc = jc;
    }

    /**
     * Return the id configuration attribute value.
     *
     * @return id attribute value
     */
    public String getId() {
        return id;
    }

    /**
     * Return the syncDelay configuration attribute value.
     *
     * @return syncDelay
     */
    public long getSyncDelay() {
        return syncDelay;
    }

    /**
     * Returns the journal configuration.
     *
     * @return journal configuration
     */
    public JournalConfig getJournalConfig() {
        return jc;
    }
}