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
package org.apache.jackrabbit.jcr2spi.nodetype;

import EDU.oswego.cs.dl.util.concurrent.ConcurrentReaderHashMap;
import org.apache.commons.collections.map.ReferenceMap;
import org.apache.jackrabbit.jcr2spi.util.Dumpable;
import org.apache.jackrabbit.spi.Name;
import org.apache.jackrabbit.spi.QNodeDefinition;
import org.apache.jackrabbit.spi.QNodeTypeDefinition;
import org.apache.jackrabbit.spi.QPropertyDefinition;
import org.apache.jackrabbit.spi.QValue;
import org.apache.jackrabbit.spi.QItemDefinition;
import org.apache.jackrabbit.spi.commons.nodetype.InvalidNodeTypeDefException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.NamespaceRegistry;
import javax.jcr.PropertyType;
import javax.jcr.RepositoryException;
import javax.jcr.nodetype.NoSuchNodeTypeException;
import javax.jcr.nodetype.ConstraintViolationException;
import javax.jcr.version.OnParentVersionAction;
import java.io.PrintStream;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.List;
import java.util.ArrayList;
import java.util.TreeSet;
import java.util.HashMap;

/**
 * A <code>NodeTypeRegistry</code> ...
 */
public class NodeTypeRegistryImpl implements Dumpable, NodeTypeRegistry, EffectiveNodeTypeProvider {

    private static Logger log = LoggerFactory.getLogger(NodeTypeRegistryImpl.class);

    // cache of pre-built aggregations of node types
    private final EffectiveNodeTypeCache entCache;

    // map of node type names and node type definitions
    //private final ConcurrentReaderHashMap registeredNTDefs;
    private final NodeTypeDefinitionMap registeredNTDefs;

    // set of property definitions
    private final Set propDefs;
    // set of node definitions
    private final Set nodeDefs;

    /**
     * Object used to persist new nodetypes and modified nodetype definitions.
     */
    private final NodeTypeStorage storage;

    /**
     * Class used to validate NodeType definitions
     */
    private final DefinitionValidator validator;

    /**
     * Listeners (soft references)
     */
    private final Map listeners = Collections.synchronizedMap(new ReferenceMap(ReferenceMap.WEAK, ReferenceMap.WEAK));

    /**
     * Create a new <code>NodeTypeRegistry</codes>
     *
     * @param storage
     * @param nsRegistry
     * @return <code>NodeTypeRegistry</codes> object
     */
    public static NodeTypeRegistryImpl create(NodeTypeStorage storage, NamespaceRegistry nsRegistry) {
        NodeTypeRegistryImpl ntRegistry = new NodeTypeRegistryImpl(storage, nsRegistry);
        return ntRegistry;
    }

    /**
     * Clears all caches.
     */
    public synchronized void dispose() {
        entCache.clear();
        registeredNTDefs.clear();
        propDefs.clear();
        nodeDefs.clear();
        listeners.clear();
    }

    /**
     * Private constructor
     *
     * @param storage
     * @param nsRegistry
     */
    private NodeTypeRegistryImpl(NodeTypeStorage storage, NamespaceRegistry nsRegistry) {
        this.storage = storage;
        this.validator = new DefinitionValidator(this, nsRegistry);

        entCache = new BitsetENTCacheImpl();
        //registeredNTDefs = new ConcurrentReaderHashMap();
        registeredNTDefs = new NodeTypeDefinitionMap();

        propDefs = new HashSet();
        nodeDefs = new HashSet();
    }

    //---------------------------------------------------< NodeTypeRegistry >---
    /**
     * @see NodeTypeRegistry#addListener(NodeTypeRegistryListener)
     */
    public void addListener(NodeTypeRegistryListener listener) {
        if (!listeners.containsKey(listener)) {
            listeners.put(listener, listener);
        }
    }

    /**
     * @see NodeTypeRegistry#removeListener(NodeTypeRegistryListener)
     */
    public void removeListener(NodeTypeRegistryListener listener) {
        listeners.remove(listener);
    }

    /**
     * @see NodeTypeRegistry#getRegisteredNodeTypes()
     */
    public Name[] getRegisteredNodeTypes() throws RepositoryException {
        Set qNames = registeredNTDefs.keySet();
        return (Name[]) qNames.toArray(new Name[registeredNTDefs.size()]);
    }


    /**
     * @see NodeTypeRegistry#isRegistered(Name)
     */
    public boolean isRegistered(Name nodeTypeName) {
        return registeredNTDefs.containsKey(nodeTypeName);
    }

   /**
     * @see NodeTypeRegistry#registerNodeType(QNodeTypeDefinition)
     */
    public synchronized EffectiveNodeType registerNodeType(QNodeTypeDefinition ntDef)
            throws InvalidNodeTypeDefException, RepositoryException {
        // validate the new nodetype definition
        EffectiveNodeType ent = validator.validateNodeTypeDef(ntDef, registeredNTDefs);

        // persist new node type definition
        storage.registerNodeTypes(new QNodeTypeDefinition[] {ntDef});

        // update internal caches
        internalRegister(ntDef, ent);

        // notify listeners
        notifyRegistered(ntDef.getName());
        return ent;
    }

    /**
     * @see NodeTypeRegistry#registerNodeTypes(Collection)
     */
    public synchronized void registerNodeTypes(Collection ntDefs)
            throws InvalidNodeTypeDefException, RepositoryException {

        // validate new nodetype definitions
        Map defMap = validator.validateNodeTypeDefs(ntDefs, registeredNTDefs);
        storage.registerNodeTypes((QNodeTypeDefinition[])ntDefs.toArray(new QNodeTypeDefinition[ntDefs.size()]));

        // update internal cache
        internalRegister(defMap);

        // notify listeners
        for (Iterator iter = ntDefs.iterator(); iter.hasNext();) {
            Name ntName = ((QNodeTypeDefinition)iter.next()).getName();
            notifyRegistered(ntName);
        }
    }

    /**
     * @see NodeTypeRegistry#unregisterNodeType(Name)
     */
    public void unregisterNodeType(Name nodeTypeName) throws NoSuchNodeTypeException, RepositoryException {
        HashSet ntNames = new HashSet();
        ntNames.add(nodeTypeName);
        unregisterNodeTypes(ntNames);
    }

    /**
     * @see NodeTypeRegistry#unregisterNodeTypes(Collection)
     */
    public synchronized void unregisterNodeTypes(Collection nodeTypeNames)
            throws NoSuchNodeTypeException, RepositoryException {
        // do some preliminary checks
        for (Iterator iter = nodeTypeNames.iterator(); iter.hasNext();) {
            Name ntName = (Name) iter.next();

            // Best effort check for node types other than those to be
            // unregistered that depend on the given node types
            Set dependents = registeredNTDefs.getDependentNodeTypes(ntName);
            dependents.removeAll(nodeTypeNames);
            if (dependents.size() > 0) {
                StringBuffer msg = new StringBuffer();
                msg.append(ntName).append(" can not be removed because the following node types depend on it: ");
                for (Iterator depIter = dependents.iterator(); depIter.hasNext();) {
                    msg.append(depIter.next());
                    msg.append(" ");
                }
                throw new RepositoryException(msg.toString());
            }
        }

        // persist removal of node type definitions
        // NOTE: conflict with existing content not asserted on client
        storage.unregisterNodeTypes((Name[]) nodeTypeNames.toArray(new Name[nodeTypeNames.size()]));


        // all preconditions are met, node types can now safely be unregistered
        internalUnregister(nodeTypeNames);

        // notify listeners
        for (Iterator iter = nodeTypeNames.iterator(); iter.hasNext();) {
            Name ntName = (Name) iter.next();
            notifyUnregistered(ntName);
        }
    }

    /**
     * @see NodeTypeRegistry#reregisterNodeType(QNodeTypeDefinition)
     */
    public synchronized EffectiveNodeType reregisterNodeType(QNodeTypeDefinition ntd)
            throws NoSuchNodeTypeException, InvalidNodeTypeDefException,
            RepositoryException {
        Name name = ntd.getName();
        if (!registeredNTDefs.containsKey(name)) {
            throw new NoSuchNodeTypeException(name.toString());
        }
        /* validate new node type definition */
        EffectiveNodeType ent = validator.validateNodeTypeDef(ntd, registeredNTDefs);

        // first call reregistering on storage
        storage.reregisterNodeTypes(new QNodeTypeDefinition[]{ntd});

        // unregister old node type definition
        internalUnregister(name);
        // register new definition
        internalRegister(ntd, ent);

        // notify listeners
        notifyReRegistered(name);
        return ent;
    }

    /**
     * @see NodeTypeRegistry#getNodeTypeDefinition(Name)
     */
    public QNodeTypeDefinition getNodeTypeDefinition(Name nodeTypeName)
        throws NoSuchNodeTypeException {
        QNodeTypeDefinition def = (QNodeTypeDefinition) registeredNTDefs.get(nodeTypeName);
        if (def == null) {
            throw new NoSuchNodeTypeException("Nodetype " + nodeTypeName + " doesn't exist");
        }
        return def;
    }
    //------------------------------------------< EffectiveNodeTypeProvider >---
    /**
     * @see EffectiveNodeTypeProvider#getEffectiveNodeType(Name)
     */
    public synchronized EffectiveNodeType getEffectiveNodeType(Name ntName)
            throws NoSuchNodeTypeException {
        return getEffectiveNodeType(ntName, entCache, registeredNTDefs);
    }

    /**
     * @see EffectiveNodeTypeProvider#getEffectiveNodeType(Name[])
     */
    public synchronized EffectiveNodeType getEffectiveNodeType(Name[] ntNames)
            throws ConstraintViolationException, NoSuchNodeTypeException {
        return getEffectiveNodeType(ntNames, entCache, registeredNTDefs);
    }

    /**
     * @see EffectiveNodeTypeProvider#getEffectiveNodeType(Name[], Map)
     */
    public EffectiveNodeType getEffectiveNodeType(Name[] ntNames, Map ntdMap)
        throws ConstraintViolationException, NoSuchNodeTypeException {
        return getEffectiveNodeType(ntNames, entCache, ntdMap);
    }

    /**
     * @see EffectiveNodeTypeProvider#getEffectiveNodeType(QNodeTypeDefinition, Map)
     */
    public EffectiveNodeType getEffectiveNodeType(QNodeTypeDefinition ntd, Map ntdMap)
            throws ConstraintViolationException, NoSuchNodeTypeException {
        TreeSet mergedNodeTypes = new TreeSet();
        TreeSet inheritedNodeTypes = new TreeSet();
        TreeSet allNodeTypes = new TreeSet();
        Map namedItemDefs = new HashMap();
        List unnamedItemDefs = new ArrayList();
        Set supportedMixins = null;

        Name ntName = ntd.getName();
        // prepare new instance
        mergedNodeTypes.add(ntName);
        allNodeTypes.add(ntName);

        Name[] smixins = ntd.getSupportedMixinTypes();

        if (smixins != null) {
            supportedMixins = new HashSet();
            for (int i = 0; i < smixins.length; i++) {
                supportedMixins.add(smixins[i]);
            }
        }

        // map of all item definitions (maps id to definition)
        // used to effectively detect ambiguous child definitions where
        // ambiguity is defined in terms of definition identity
        Set itemDefIds = new HashSet();

        QNodeDefinition[] cnda = ntd.getChildNodeDefs();
        for (int i = 0; i < cnda.length; i++) {
            // check if child node definition would be ambiguous within
            // this node type definition
            if (itemDefIds.contains(cnda[i])) {
                // conflict
                String msg;
                if (cnda[i].definesResidual()) {
                    msg = ntName + " contains ambiguous residual child node definitions";
                } else {
                    msg = ntName + " contains ambiguous definitions for child node named "
                            + cnda[i].getName();
                }
                log.debug(msg);
                throw new ConstraintViolationException(msg);
            } else {
                itemDefIds.add(cnda[i]);
            }
            if (cnda[i].definesResidual()) {
                // residual node definition
                unnamedItemDefs.add(cnda[i]);
            } else {
                // named node definition
                Name name = cnda[i].getName();
                List defs = (List) namedItemDefs.get(name);
                if (defs == null) {
                    defs = new ArrayList();
                    namedItemDefs.put(name, defs);
                }
                if (defs.size() > 0) {
                    /**
                     * there already exists at least one definition with that
                     * name; make sure none of them is auto-create
                     */
                    for (int j = 0; j < defs.size(); j++) {
                        QItemDefinition qDef = (QItemDefinition) defs.get(j);
                        if (cnda[i].isAutoCreated() || qDef.isAutoCreated()) {
                            // conflict
                            String msg = "There are more than one 'auto-create' item definitions for '"
                                    + name + "' in node type '" + ntName + "'";
                            log.debug(msg);
                            throw new ConstraintViolationException(msg);
                        }
                    }
                }
                defs.add(cnda[i]);
            }
        }
        QPropertyDefinition[] pda = ntd.getPropertyDefs();
        for (int i = 0; i < pda.length; i++) {
            // check if property definition would be ambiguous within
            // this node type definition
            if (itemDefIds.contains(pda[i])) {
                // conflict
                String msg;
                if (pda[i].definesResidual()) {
                    msg = ntName + " contains ambiguous residual property definitions";
                } else {
                    msg = ntName + " contains ambiguous definitions for property named "
                            + pda[i].getName();
                }
                log.debug(msg);
                throw new ConstraintViolationException(msg);
            } else {
                itemDefIds.add(pda[i]);
            }
            if (pda[i].definesResidual()) {
                // residual property definition
                unnamedItemDefs.add(pda[i]);
            } else {
                // named property definition
                Name name = pda[i].getName();
                List defs = (List) namedItemDefs.get(name);
                if (defs == null) {
                    defs = new ArrayList();
                    namedItemDefs.put(name, defs);
                }
                if (defs.size() > 0) {
                    /**
                     * there already exists at least one definition with that
                     * name; make sure none of them is auto-create
                     */
                    for (int j = 0; j < defs.size(); j++) {
                        QItemDefinition qDef = (QItemDefinition) defs.get(j);
                        if (pda[i].isAutoCreated() || qDef.isAutoCreated()) {
                            // conflict
                            String msg = "There are more than one 'auto-create' item definitions for '"
                                    + name + "' in node type '" + ntName + "'";
                            log.debug(msg);
                            throw new ConstraintViolationException(msg);
                        }
                    }
                }
                defs.add(pda[i]);
            }
        }

        // create empty effective node type instance
        EffectiveNodeTypeImpl ent = new EffectiveNodeTypeImpl(mergedNodeTypes,
                inheritedNodeTypes, allNodeTypes, namedItemDefs,
                unnamedItemDefs, supportedMixins);

        // resolve supertypes recursively
        Name[] supertypes = ntd.getSupertypes();
        if (supertypes.length > 0) {
            EffectiveNodeTypeImpl effSuperType = (EffectiveNodeTypeImpl) getEffectiveNodeType(supertypes, ntdMap);
            ent.internalMerge(effSuperType, true);
        }
        return ent;
    }

    /**
     *
     * @param ntName
     * @param entCache
     * @param ntdCache
     * @return
     * @throws NoSuchNodeTypeException
     */
    private EffectiveNodeType getEffectiveNodeType(Name ntName,
                                                   EffectiveNodeTypeCache entCache,
                                                   Map ntdCache)
        throws NoSuchNodeTypeException {
        // 1. check if effective node type has already been built
        EffectiveNodeTypeCache.Key key = entCache.getKey(new Name[]{ntName});
        EffectiveNodeType ent = entCache.get(key);
        if (ent != null) {
            return ent;
        }

        // 2. make sure we've got the definition of the specified node type
        QNodeTypeDefinition ntd = (QNodeTypeDefinition) ntdCache.get(ntName);
        if (ntd == null) {
            throw new NoSuchNodeTypeException(ntName.toString());
        }

        // 3. build effective node type
        synchronized (entCache) {
            try {
                ent = getEffectiveNodeType(ntd, ntdCache);
                // store new effective node type
                entCache.put(ent);
                return ent;
            } catch (ConstraintViolationException e) {
                // should never get here as all known node types should be valid!
                String msg = "Internal error: encountered invalid registered node type " + ntName;
                log.debug(msg);
                throw new NoSuchNodeTypeException(msg, e);
            }
        }
    }

    /**
     * @param ntNames
     * @param entCache
     * @param ntdCache
     * @return
     * @throws ConstraintViolationException
     * @throws NoSuchNodeTypeException
     */
    private EffectiveNodeType getEffectiveNodeType(Name[] ntNames,
                                                   EffectiveNodeTypeCache entCache,
                                                   Map ntdCache)
        throws ConstraintViolationException, NoSuchNodeTypeException {

        EffectiveNodeTypeCache.Key key = entCache.getKey(ntNames);
        // 1. check if aggregate has already been built
        if (entCache.contains(key)) {
            return entCache.get(key);
        }

        // 2. make sure we've got the definitions of the specified node types
        for (int i = 0; i < ntNames.length; i++) {
            if (!ntdCache.containsKey(ntNames[i])) {
                throw new NoSuchNodeTypeException(ntNames[i].toString());
            }
        }

        // 3. build aggregate
        EffectiveNodeTypeCache.Key requested = key;
        EffectiveNodeTypeImpl result = null;
        synchronized (entCache) {
            // build list of 'best' existing sub-aggregates
            while (key.getNames().length > 0) {
                // find the (sub) key that matches the current key the best
                EffectiveNodeTypeCache.Key subKey = entCache.findBest(key);
                if (subKey != null) {
                    EffectiveNodeTypeImpl ent = (EffectiveNodeTypeImpl) entCache.get(subKey);
                    if (result == null) {
                        result = ent;
                    } else {
                        result = result.merge(ent);
                        // store intermediate result
                        entCache.put(result);
                    }
                    // subtract the result from the temporary key
                    key = key.subtract(subKey);
                } else {
                    /**
                     * no matching sub-aggregates found:
                     * build aggregate of remaining node types through iteration
                     */
                    Name[] remainder = key.getNames();
                    for (int i = 0; i < remainder.length; i++) {
                        QNodeTypeDefinition ntd = (QNodeTypeDefinition) ntdCache.get(remainder[i]);
                        EffectiveNodeType ent = getEffectiveNodeType(ntd, ntdCache);
                        // store new effective node type
                        entCache.put(ent);
                        if (result == null) {
                            result = (EffectiveNodeTypeImpl) ent;
                        } else {
                            result = result.merge((EffectiveNodeTypeImpl) ent);
                            // store intermediate result (sub-aggregate)
                            entCache.put(result);
                        }
                    }
                    break;
                }
            }
        }
        // also put the requested key, since the merge could have removed some
        // the redundant nodetypes
        if (!entCache.contains(requested)) {
            entCache.put(requested, result);
        }
        // we're done
        return result;
    }

    //------------------------------------------------------------< private >---
    /**
     * Notify the listeners that a node type <code>ntName</code> has been registered.
     */
    private void notifyRegistered(Name ntName) {
        // copy listeners to array to avoid ConcurrentModificationException
        NodeTypeRegistryListener[] la =
                new NodeTypeRegistryListener[listeners.size()];
        Iterator iter = listeners.values().iterator();
        int cnt = 0;
        while (iter.hasNext()) {
            la[cnt++] = (NodeTypeRegistryListener) iter.next();
        }
        for (int i = 0; i < la.length; i++) {
            if (la[i] != null) {
                la[i].nodeTypeRegistered(ntName);
            }
        }
    }

    /**
     * Notify the listeners that a node type <code>ntName</code> has been re-registered.
     */
    private void notifyReRegistered(Name ntName) {
        // copy listeners to array to avoid ConcurrentModificationException
        NodeTypeRegistryListener[] la = new NodeTypeRegistryListener[listeners.size()];
        Iterator iter = listeners.values().iterator();
        int cnt = 0;
        while (iter.hasNext()) {
            la[cnt++] = (NodeTypeRegistryListener) iter.next();
        }
        for (int i = 0; i < la.length; i++) {
            if (la[i] != null) {
                la[i].nodeTypeReRegistered(ntName);
            }
        }
    }

    /**
     * Notify the listeners that a node type <code>ntName</code> has been unregistered.
     */
    private void notifyUnregistered(Name ntName) {
        // copy listeners to array to avoid ConcurrentModificationException
        NodeTypeRegistryListener[] la = new NodeTypeRegistryListener[listeners.size()];
        Iterator iter = listeners.values().iterator();
        int cnt = 0;
        while (iter.hasNext()) {
            la[cnt++] = (NodeTypeRegistryListener) iter.next();
        }
        for (int i = 0; i < la.length; i++) {
            if (la[i] != null) {
                la[i].nodeTypeUnregistered(ntName);
            }
        }
    }

    private void internalRegister(Map defMap) {
        for (Iterator it = defMap.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry entry = (Map.Entry)it.next();
            QNodeTypeDefinition ntd = (QNodeTypeDefinition)entry.getKey();
            internalRegister(ntd, (EffectiveNodeTypeImpl)entry.getValue());
        }
    }

    private void internalRegister(QNodeTypeDefinition ntd, EffectiveNodeType ent) {
        // store new effective node type instance if present. otherwise it
        // will be created on demand.
        if (ent != null) {
            entCache.put(ent);
        } else {
            log.debug("Effective node type for " + ntd + " not yet built.");
        }
        // register nt-definition
        registeredNTDefs.put(ntd.getName(), ntd);

        // store property & child node definitions of new node type by id
        QPropertyDefinition[] pda = ntd.getPropertyDefs();
        synchronized (propDefs) {
            for (int i = 0; i < pda.length; i++) {
                propDefs.add(pda[i]);
            }
        }
        QNodeDefinition[] nda = ntd.getChildNodeDefs();
        synchronized (nodeDefs) {
            for (int i = 0; i < nda.length; i++) {
                nodeDefs.add(nda[i]);
            }
        }
    }

    private void internalUnregister(Name name) {
        QNodeTypeDefinition ntd = (QNodeTypeDefinition) registeredNTDefs.remove(name);
        entCache.invalidate(name);

        if (ntd != null) {
            // remove property & child node definitions
            QPropertyDefinition[] pda = ntd.getPropertyDefs();
            synchronized (propDefs) {
                for (int i = 0; i < pda.length; i++) {
                    propDefs.remove(pda[i]);
                }
            }
            synchronized (nodeDefs) {
                QNodeDefinition[] nda = ntd.getChildNodeDefs();
                for (int i = 0; i < nda.length; i++) {
                    nodeDefs.remove(nda[i]);
                }
            }
        }
    }

    private void internalUnregister(Collection ntNames) {
        for (Iterator iter = ntNames.iterator(); iter.hasNext();) {
            Name name = (Name) iter.next();
            internalUnregister(name);
        }
    }

    //-----------------------------------------------------------< Dumpable >---
    /**
     * {@inheritDoc}
     */
    public void dump(PrintStream ps) {
        ps.println("NodeTypeRegistry (" + this + ")");
        ps.println();
        ps.println("Known NodeTypes:");
        ps.println();
        registeredNTDefs.dump(ps);
        ps.println();

        entCache.dump(ps);
    }

    //--------------------------------------------------------< inner class >---
    /**
     * Inner class representing the map of <code>QNodeTypeDefinition</code>s
     * that have been loaded yet.
     */
    private class NodeTypeDefinitionMap implements Map, Dumpable {

        // map of node type names and node type definitions
        private final ConcurrentReaderHashMap nodetypeDefinitions = new ConcurrentReaderHashMap();

        /**
         * Returns the names of those registered node types that have
         * dependencies on the given node type.<p/>
         * Note, that the returned Set may not be complete with respect
         * to all node types registered within the repository. Instead it
         * will only contain those node type definitions that are known so far.
         *
         * @param nodeTypeName node type name
         * @return a set of node type <code>Name</code>s
         * @throws NoSuchNodeTypeException
         */
        private Set getDependentNodeTypes(Name nodeTypeName) throws NoSuchNodeTypeException {
            if (!nodetypeDefinitions.containsKey(nodeTypeName)) {
                throw new NoSuchNodeTypeException(nodeTypeName.toString());
            }
            // get names of those node types that have dependencies on the
            // node type with the given nodeTypeName.
            HashSet names = new HashSet();
            Iterator iter = nodetypeDefinitions.values().iterator();
            while (iter.hasNext()) {
                QNodeTypeDefinition ntd = (QNodeTypeDefinition) iter.next();
                if (ntd.getDependencies().contains(nodeTypeName)) {
                    names.add(ntd.getName());
                }
            }
            return names;
        }

        private void updateInternalMap(Iterator definitions) {
            // since definition were retrieved from the storage, valiation
            // can be omitted -> register without building effective-nodetype.
            // TODO: check if correct
            while (definitions.hasNext()) {
                internalRegister((QNodeTypeDefinition) definitions.next(), null);
            }
        }

        //------------------------------------------------------------< Map >---
        public int size() {
            return nodetypeDefinitions.size();
        }

        public void clear() {
            nodetypeDefinitions.clear();
        }

        public boolean isEmpty() {
            return nodetypeDefinitions.isEmpty();
        }

        public boolean containsKey(Object key) {
            if (!(key instanceof Name)) {
                return false;
            }
            return get(key) != null;
        }

        public boolean containsValue(Object value) {
            if (!(value instanceof QNodeTypeDefinition)) {
                return false;
            }
            return get(((QNodeTypeDefinition)value).getName()) != null;
        }

        public Set keySet() {
            // to be aware of all (recently) registered nodetypes retrieve
            // complete set from the storage again and add missing / replace
            // existing definitions.
            try {
                Iterator it = storage.getAllDefinitions();
                updateInternalMap(it);
            } catch (RepositoryException e) {
                log.error(e.getMessage());
            }
            return nodetypeDefinitions.keySet();
        }

        public Collection values() {
            // make sure all node type definitions have been loaded.
            keySet();
            // and retrieve the collection containing all definitions.
            return nodetypeDefinitions.values();
        }

        public Object put(Object key, Object value) {
            return nodetypeDefinitions.put(key, value);
        }

        public void putAll(Map t) {
            throw new UnsupportedOperationException("Implementation missing");
        }

        public Set entrySet() {
            // make sure all node type definitions have been loaded.
            keySet();
            return nodetypeDefinitions.entrySet();
        }

        public Object get(Object key) {
            if (!(key instanceof Name)) {
                throw new IllegalArgumentException();
            }
            QNodeTypeDefinition def = (QNodeTypeDefinition) nodetypeDefinitions.get(key);
            if (def == null) {
                try {
                    // node type does either not exist or hasn't been loaded yet
                    Iterator it = storage.getDefinitions(new Name[] {(Name) key});
                    updateInternalMap(it);
                } catch (RepositoryException e) {
                    log.debug(e.getMessage());
                }
            }
            def = (QNodeTypeDefinition) nodetypeDefinitions.get(key);
            return def;
        }

        public Object remove(Object key) {
            return (QNodeTypeDefinition) nodetypeDefinitions.remove(key);
        }

        //-------------------------------------------------------< Dumpable >---
        public void dump(PrintStream ps) {
            Iterator iter = nodetypeDefinitions.values().iterator();
            while (iter.hasNext()) {
                QNodeTypeDefinition ntd = (QNodeTypeDefinition) iter.next();
                ps.println(ntd.getName());
                Name[] supertypes = ntd.getSupertypes();
                ps.println("\tSupertypes");
                for (int i = 0; i < supertypes.length; i++) {
                    ps.println("\t\t" + supertypes[i]);
                }
                ps.println("\tMixin\t" + ntd.isMixin());
                ps.println("\tOrderableChildNodes\t" + ntd.hasOrderableChildNodes());
                ps.println("\tPrimaryItemName\t" + (ntd.getPrimaryItemName() == null ? "<null>" : ntd.getPrimaryItemName().toString()));
                QPropertyDefinition[] pd = ntd.getPropertyDefs();
                for (int i = 0; i < pd.length; i++) {
                    ps.print("\tPropertyDefinition");
                    ps.println(" (declared in " + pd[i].getDeclaringNodeType() + ") ");
                    ps.println("\t\tName\t\t" + (pd[i].definesResidual() ? "*" : pd[i].getName().toString()));
                    String type = pd[i].getRequiredType() == 0 ? "null" : PropertyType.nameFromValue(pd[i].getRequiredType());
                    ps.println("\t\tRequiredType\t" + type);
                    String[] vca = pd[i].getValueConstraints();
                    StringBuffer constraints = new StringBuffer();
                    if (vca == null) {
                        constraints.append("<null>");
                    } else {
                        for (int n = 0; n < vca.length; n++) {
                            if (constraints.length() > 0) {
                                constraints.append(", ");
                            }
                            constraints.append(vca[n]);
                        }
                    }
                    ps.println("\t\tValueConstraints\t" + constraints.toString());
                    QValue[] defVals = pd[i].getDefaultValues();
                    StringBuffer defaultValues = new StringBuffer();
                    if (defVals == null) {
                        defaultValues.append("<null>");
                    } else {
                        for (int n = 0; n < defVals.length; n++) {
                            if (defaultValues.length() > 0) {
                                defaultValues.append(", ");
                            }
                            try {
                                defaultValues.append(defVals[n].getString());
                            } catch (RepositoryException e) {
                                defaultValues.append(defVals[n].toString());
                            }
                        }
                    }
                    ps.println("\t\tDefaultValue\t" + defaultValues.toString());
                    ps.println("\t\tAutoCreated\t" + pd[i].isAutoCreated());
                    ps.println("\t\tMandatory\t" + pd[i].isMandatory());
                    ps.println("\t\tOnVersion\t" + OnParentVersionAction.nameFromValue(pd[i].getOnParentVersion()));
                    ps.println("\t\tProtected\t" + pd[i].isProtected());
                    ps.println("\t\tMultiple\t" + pd[i].isMultiple());
                }
                QNodeDefinition[] nd = ntd.getChildNodeDefs();
                for (int i = 0; i < nd.length; i++) {
                    ps.print("\tNodeDefinition");
                    ps.println(" (declared in " + nd[i].getDeclaringNodeType() + ") ");
                    ps.println("\t\tName\t\t" + (nd[i].definesResidual() ? "*" : nd[i].getName().toString()));
                    Name[] reqPrimaryTypes = nd[i].getRequiredPrimaryTypes();
                    if (reqPrimaryTypes != null && reqPrimaryTypes.length > 0) {
                        for (int n = 0; n < reqPrimaryTypes.length; n++) {
                            ps.print("\t\tRequiredPrimaryType\t" + reqPrimaryTypes[n]);
                        }
                    }
                    Name defPrimaryType = nd[i].getDefaultPrimaryType();
                    if (defPrimaryType != null) {
                        ps.print("\n\t\tDefaultPrimaryType\t" + defPrimaryType);
                    }
                    ps.println("\n\t\tAutoCreated\t" + nd[i].isAutoCreated());
                    ps.println("\t\tMandatory\t" + nd[i].isMandatory());
                    ps.println("\t\tOnVersion\t" + OnParentVersionAction.nameFromValue(nd[i].getOnParentVersion()));
                    ps.println("\t\tProtected\t" + nd[i].isProtected());
                    ps.println("\t\tAllowsSameNameSiblings\t" + nd[i].allowsSameNameSiblings());
                }
            }
        }
    }
}
