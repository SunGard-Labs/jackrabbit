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
package org.apache.jackrabbit.core.security.authorization;

import org.apache.jackrabbit.api.jsr283.security.AccessControlException;
import org.apache.jackrabbit.api.jsr283.security.Privilege;
import org.apache.jackrabbit.value.StringValue;
import org.apache.jackrabbit.value.ValueHelper;

import javax.jcr.Value;
import javax.jcr.ValueFactory;
import java.security.Principal;
import java.util.Collections;
import java.util.Map;
import java.util.Iterator;
import java.util.HashMap;

/**
 * Simple, immutable implementation of the
 * {@link org.apache.jackrabbit.api.jsr283.security.AccessControlEntry}
 * and the {@link JackrabbitAccessControlEntry} interfaces.
 */
public abstract class AccessControlEntryImpl implements JackrabbitAccessControlEntry {

    /**
     * Privileges contained in this entry
     */
    private final Privilege[] privileges;

    /**
     * PrivilegeBits calculated from the privileges
     */
    private final int privilegeBits;

    /**
     * the Principal of this entry
     */
    private final Principal principal;

    /**
     * Jackrabbit specific extension: if the actions contained are allowed or
     * denied.
     */
    private final boolean allow;

    /**
     * Jackrabbit specific extension: the list of additional restrictions to be
     * included in the evaluation.
     */
    private final Map restrictions;

    /**
     * Value factory
     */
    private final ValueFactory valueFactory;

    /**
     * Hash code being calculated on demand.
     */
    private int hashCode = -1;

    /**
     * Construct an access control entry for the given principal and privileges.
     *
     * @param principal Principal for this access control entry.
     * @param privileges Privileges for this access control entry.
     * @throws AccessControlException if either principal or privileges are invalid.
     */
    protected AccessControlEntryImpl(Principal principal, Privilege[] privileges)
            throws AccessControlException {
        this(principal, privileges, true, null, null);
    }

    /**
     * Construct an access control entry for the given principal and privileges.
     *
     * @param principal Principal for this access control entry.
     * @param privileges Privileges for this access control entry.
     * @param isAllow <code>true</code> if this ACE grants the specified
     * privileges to the specified principal; <code>false</code> otherwise.
     * @param restrictions A map of restriction name (String) to restriction
     * (Value). See {@link org.apache.jackrabbit.core.security.authorization.JackrabbitAccessControlList#getRestrictionNames()}
     * and {@link org.apache.jackrabbit.core.security.authorization.JackrabbitAccessControlList#getRestrictionType(String)}. 
     * @param valueFactory the value factory.
     * @throws AccessControlException if either principal or privileges are invalid.
     */
    protected AccessControlEntryImpl(Principal principal, Privilege[] privileges,
                                     boolean isAllow, Map restrictions, ValueFactory valueFactory)
            throws AccessControlException {
        if (principal == null) {
            throw new IllegalArgumentException();
        }
        // make sure no abstract privileges are passed.
        for (int i = 0; i < privileges.length; i++) {
            if (privileges[i].isAbstract()) {
                throw new AccessControlException("Privilege " + privileges[i] + " is abstract.");
            }
        }
        this.principal = principal;
        this.privileges = privileges;
        this.privilegeBits = PrivilegeRegistry.getBits(privileges);
        this.allow = isAllow;
        this.valueFactory = valueFactory;
        
        if (restrictions == null) {
            this.restrictions = Collections.EMPTY_MAP;
        } else {
            this.restrictions = new HashMap(restrictions.size());
            // validate the passed restrictions and fill the map
            for (Iterator it = restrictions.keySet().iterator(); it.hasNext();) {
                Object key = it.next();
                Object v = restrictions.get(key);
                Value value;
                if (v instanceof Value) {
                    // create copy of the value
                    value = ValueHelper.copy((Value) v, valueFactory);
                } else {
                    // fallback
                    value = new StringValue(v.toString());
                }
                this.restrictions.put(key.toString(), value);
            }
        }
    }
    
    /**
     * @return the int representation of the privileges defined for this entry.
     */
    public int getPrivilegeBits() {
        return privilegeBits;
    }

    /**
     * Build the hash code.
     *
     * @return the hash code.
     */
    protected int buildHashCode() {
        int h = 17;
        h = 37 * h + principal.getName().hashCode();
        h = 37 * h + privilegeBits;
        h = 37 * h + Boolean.valueOf(allow).hashCode();
        h = 37 * h + restrictions.hashCode();
        return h;
    }

    //-------------------------------------------------< AccessControlEntry >---
    /**
     * @see org.apache.jackrabbit.api.jsr283.security.AccessControlEntry#getPrincipal()
     */
    public Principal getPrincipal() {
        return principal;
    }

    /**
     * @see org.apache.jackrabbit.api.jsr283.security.AccessControlEntry#getPrivileges()
     */
    public Privilege[] getPrivileges() {
        return privileges;
    }


    //---------------------------------------< JackrabbitAccessControlEntry >---
    /**
     * @see JackrabbitAccessControlEntry#isAllow()
     */
    public boolean isAllow() {
        return allow;
    }

    /**
     * @see JackrabbitAccessControlEntry#getRestrictionNames()
     */
    public String[] getRestrictionNames() {
        return (String[]) restrictions.keySet().toArray(new String[restrictions.size()]);
    }

    /**
     * @see JackrabbitAccessControlEntry#getRestriction(String)
     */
    public Value getRestriction(String restrictionName) {
        if (restrictions.containsKey(restrictionName)) {
            return ValueHelper.copy((Value) restrictions.get(restrictionName), valueFactory);
        } else {
            return null;
        }
    }

    //-------------------------------------------------------------< Object >---
    /**
     * @see Object#hashCode()
     */
    public int hashCode() {
        if (hashCode == -1) {
            hashCode = buildHashCode();
        }
        return hashCode;
    }

    /**
     * @see Object#equals(Object)
     */
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj instanceof AccessControlEntryImpl) {
            AccessControlEntryImpl tmpl = (AccessControlEntryImpl) obj;
            return principal.getName().equals(tmpl.principal.getName()) &&
                   privilegeBits == tmpl.privilegeBits && allow == tmpl.allow &&
                   restrictions.equals(tmpl.restrictions);
        }
        return false;
    }
}