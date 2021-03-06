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

import org.apache.jackrabbit.api.security.user.Authorizable;
import org.apache.jackrabbit.api.security.user.Impersonation;
import org.apache.jackrabbit.api.security.user.User;
import org.apache.jackrabbit.core.NodeImpl;
import org.apache.jackrabbit.core.security.authentication.CryptedSimpleCredentials;
import org.apache.jackrabbit.core.security.principal.AdminPrincipal;
import org.apache.jackrabbit.core.security.principal.ItemBasedPrincipal;
import org.apache.jackrabbit.util.Text;

import javax.jcr.Credentials;
import javax.jcr.RepositoryException;
import javax.jcr.Value;
import java.io.UnsupportedEncodingException;
import java.security.NoSuchAlgorithmException;
import java.security.Principal;

/**
 * UserImpl
 */
class UserImpl extends AuthorizableImpl implements User {

    private final String id;

    private Principal principal = null;
    private Impersonation impersonation = null;

    private UserImpl(NodeImpl node, UserManagerImpl userManager) throws RepositoryException {
        super(node, userManager);

        id = node.getProperty(P_USERID).getString();
    }

    //--------------------------------------------------------------------------
    /**
     * @param node
     * @param userManager
     * @return
     * @throws RepositoryException
     */
    static User create(NodeImpl node, UserManagerImpl userManager) throws RepositoryException {
        if (node == null || !node.isNodeType(NT_REP_USER)) {
            throw new IllegalArgumentException();
        }
        if(!Text.isDescendant(USERS_PATH, node.getPath())) {
            throw new IllegalArgumentException("User has to be within the User Path");
        }
        return new UserImpl(node, userManager);
    }

    /**
     * 
     * @param password
     * @return
     * @throws RepositoryException
     */
    static String buildPasswordValue(String password) throws RepositoryException {
        try {
            CryptedSimpleCredentials creds = new CryptedSimpleCredentials("_", password);
            return creds.getPassword();
        } catch (NoSuchAlgorithmException e) {
            throw new RepositoryException(e);
        } catch (UnsupportedEncodingException e) {
            throw new RepositoryException(e);
        }
    }

    //-------------------------------------------------------< Authorizable >---
    /**
     * @see Authorizable#getID()
     */
    public String getID() throws RepositoryException {
        return id;
    }

    //------------------------------------------------< User >------------------
    /**
     * @see User#isAdmin()
     */
    public boolean isAdmin() {
        return userManager.isAdminId(id);
    }

    /**
     * @see User#getCredentials()
     */
    public Credentials getCredentials() throws RepositoryException {
        try {
            String password = getNode().getProperty(P_PASSWORD).getString();
            Credentials creds = new CryptedSimpleCredentials(getID(), password);
            return creds;
        } catch (NoSuchAlgorithmException e) {
            throw new RepositoryException(e);
        } catch (UnsupportedEncodingException e) {
            throw new RepositoryException(e);
        }
    }
    /**
     * @see User#isGroup()
     */
    public boolean isGroup() {
        return false;
    }

    /**
     * @see User#getPrincipal()
     */
    public Principal getPrincipal() throws RepositoryException {
        if (principal == null) {
            if (isAdmin()) {
                principal = new NodeBasedAdminPrincipal(getPrincipalName());
            } else {
                principal = new NodeBasedPrincipal(getPrincipalName());
            }
        }
        return principal;
    }

    /**
     * @see User#getImpersonation()
     */
    public Impersonation getImpersonation() throws RepositoryException {
        if (impersonation == null) {
            impersonation = new ImpersonationImpl(this, userManager);
        }
        return impersonation;
    }

    /**
     * @see User#changePassword(String)
     */
    public void changePassword(String password) throws RepositoryException {
        if (password == null) {
            throw new IllegalArgumentException("The password may never be null.");
        }
        Value v = getSession().getValueFactory().createValue(buildPasswordValue(password));
        userManager.setProtectedProperty(getNode(), P_PASSWORD, v);
    }

    //--------------------------------------------------------------------------
    /**
     *
     */
    private class NodeBasedAdminPrincipal extends AdminPrincipal implements ItemBasedPrincipal {

        public NodeBasedAdminPrincipal(String adminId) {
            super(adminId);
        }

        public String getPath() throws RepositoryException {
            return getNode().getPath();
        }
    }
}
