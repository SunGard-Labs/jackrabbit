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
package org.apache.jackrabbit.core.data;

import org.apache.jackrabbit.core.SessionImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Iterator;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;

/**
 * Helper class that runs data store garbage collection as a background thread.
 */
public class GCThread implements Runnable, ScanEventListener {

    /** logger instance */
    private static final Logger LOG = LoggerFactory.getLogger(GCThread.class);

    private boolean stop;
    private Session session;
    private Exception exception;

    public GCThread(Session session) {
        this.session = session;
    }

    public void run() {

        try {
            GarbageCollector gc = ((SessionImpl) session).createDataStoreGarbageCollector();
            gc.setScanEventListener(this);
            while (!stop) {
                LOG.debug("Scanning...");
                gc.scan();
                int count = listIdentifiers(gc);
                LOG.debug("Stop; currently " + count + " identifiers");
                gc.stopScan();
                int numDeleted = gc.deleteUnused();
                if (numDeleted > 0) {
                    LOG.debug("Deleted " + numDeleted + " identifiers");
                }
                LOG.debug("Waiting...");
                Thread.sleep(10);
            }
        } catch (Exception ex) {
            LOG.error("Error scanning", ex);
            exception = ex;
        }
    }

    public void setStop(boolean stop) {
        this.stop = stop;
    }

    public Exception getException() {
        return exception;
    }

    private int listIdentifiers(GarbageCollector gc) throws DataStoreException {
        Iterator it = gc.getDataStore().getAllIdentifiers();
        int count = 0;
        while (it.hasNext()) {
            DataIdentifier id = (DataIdentifier) it.next();
            LOG.debug("  " + id);
            count++;
        }
        return count;
    }

    public void throwException() throws Exception {
        if (exception != null) {
            throw exception;
        }
    }

    public void afterScanning(Node n) throws RepositoryException {
    }

    public void beforeScanning(Node n) throws RepositoryException {
    }

    public void done() {
    }

}
