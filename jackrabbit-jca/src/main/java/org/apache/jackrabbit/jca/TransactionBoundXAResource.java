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
package org.apache.jackrabbit.jca;

import javax.transaction.xa.XAException;
import javax.transaction.xa.XAResource;
import javax.transaction.xa.Xid;

public class TransactionBoundXAResource implements XAResource {

    private XAResource xaResource;

    private JCAManagedConnection connection;

    private boolean ending;

    public TransactionBoundXAResource(JCAManagedConnection connection,
            XAResource xaResource) {
        super();
        this.xaResource = xaResource;
        this.connection = connection;
    }

    /**
     * There is a one-to-one Relation between this TransactionBoundXAResource
     * and the JCAManagedConnection so the used XAResource must be in sync when it is changed in the
     * JCAManagedConnection.
     * @param res
     */
    protected void rebind(XAResource res) {
        this.xaResource = res;
    }

    public void commit(Xid xid, boolean flag) throws XAException {
        xaResource.commit(xid, flag);
    }

    public void end(Xid xid, int i) throws XAException {
        if (!ending) {
            this.ending = true;

            try {
                xaResource.end(xid, i);
            }
            finally {
                if( i != XAResource.TMSUSPEND) {
                    this.connection.closeHandles();
                }
            }
            // reuse the XAResource
            this.ending = false;
        }
    }

    public void forget(Xid xid) throws XAException {
        xaResource.forget(xid);
    }

    public int getTransactionTimeout() throws XAException {
        return xaResource.getTransactionTimeout();
    }

    public boolean isSameRM(XAResource xar) throws XAException {
        return xaResource.isSameRM(xar);
    }

    public int prepare(Xid xid) throws XAException {
        return xaResource.prepare(xid);
    }

    public Xid[] recover(int i) throws XAException {
        return xaResource.recover(i);
    }

    public void rollback(Xid xid) throws XAException {
        xaResource.rollback(xid);
    }

    public boolean setTransactionTimeout(int timeout) throws XAException {
        return xaResource.setTransactionTimeout(timeout);
    }

    public void start(Xid xid, int i) throws XAException {
        xaResource.start(xid, i);
    }
}
