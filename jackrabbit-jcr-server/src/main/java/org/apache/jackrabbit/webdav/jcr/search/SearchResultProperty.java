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
package org.apache.jackrabbit.webdav.jcr.search;

import org.apache.jackrabbit.value.ValueHelper;
import org.apache.jackrabbit.webdav.jcr.ItemResourceConstants;
import org.apache.jackrabbit.webdav.property.AbstractDavProperty;
import org.apache.jackrabbit.webdav.property.DavProperty;
import org.apache.jackrabbit.webdav.property.DavPropertyName;
import org.apache.jackrabbit.webdav.xml.DomUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.jcr.PropertyType;
import javax.jcr.RepositoryException;
import javax.jcr.Value;
import javax.jcr.ValueFormatException;
import javax.jcr.ValueFactory;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * <code>SearchResultProperty</code>...
 */
// todo: find proper solution for transporting search results...
public class SearchResultProperty extends AbstractDavProperty implements ItemResourceConstants {

    private static Logger log = LoggerFactory.getLogger(SearchResultProperty.class);

    public static final DavPropertyName SEARCH_RESULT_PROPERTY = DavPropertyName.create("search-result-property", ItemResourceConstants.NAMESPACE);
    private static final String COLUMN = "column";

    private final String[] columnNames;
    private final Value[] values;

    /**
     * Creates a new <code>SearchResultProperty</code>.
     *
     * @param columnNames the column names of the search row represented by this
     * dav property.
     * @param values the values present in the columns
     */
    public SearchResultProperty(String[] columnNames, Value[] values) {
        super(SEARCH_RESULT_PROPERTY, true);
        this.columnNames = columnNames;
        this.values = values;
    }

    /**
     * Wrap the specified <code>DavProperty</code> in a new <code>SearchResultProperty</code>.
     *
     * @param property
     * @param valueFactory factory used to deserialize the xml value to a JCR value.
     * @throws RepositoryException if an error occurs while build the property value
     * @throws IllegalArgumentException if the specified property does have the
     * required form.
     * @see #getValues()
     */
    public SearchResultProperty(DavProperty property, ValueFactory valueFactory) throws RepositoryException {
        super(property.getName(), true);
        if (!SEARCH_RESULT_PROPERTY.equals(getName())) {
	    throw new IllegalArgumentException("SearchResultProperty may only be created from a property named " + SEARCH_RESULT_PROPERTY.toString());
	}

        List colList = new ArrayList();
        List valList = new ArrayList();
        Object propValue = property.getValue();
        if (propValue instanceof List) {
            Iterator elemIt = ((List)propValue).iterator();
            while (elemIt.hasNext()) {
                Object el = elemIt.next();
                if (el instanceof Element) {
                    parseColumnElement((Element)el, colList, valList, valueFactory);
                }
            }
        } else if (propValue instanceof Element) {
            parseColumnElement((Element)property.getValue(), colList, valList, valueFactory);
        } else {
            throw new IllegalArgumentException("SearchResultProperty requires a list of 'dcr:column' xml elements.");
        }

        columnNames = (String[]) colList.toArray(new String[colList.size()]);
        values = (Value[]) valList.toArray(new Value[valList.size()]);
    }

    private void parseColumnElement(Element columnElement, List columnNames,
                                    List values, ValueFactory valueFactory)
        throws ValueFormatException, RepositoryException {
        if (!DomUtil.matches(columnElement, COLUMN, ItemResourceConstants.NAMESPACE)) {
            log.debug("dcr:column element expected within search result.");
            return;
        }
        columnNames.add(DomUtil.getChildText(columnElement, JCR_NAME.getName(), JCR_NAME.getNamespace()));

        Value jcrValue;
        Element valueElement = DomUtil.getChildElement(columnElement, JCR_VALUE.getName(), JCR_VALUE.getNamespace());
        if (valueElement != null) {
            String typeStr = DomUtil.getAttribute(valueElement, ATTR_VALUE_TYPE, ItemResourceConstants.NAMESPACE);
            jcrValue = ValueHelper.deserialize(DomUtil.getText(valueElement),
                    PropertyType.valueFromName(typeStr), true, valueFactory);
        } else {
            jcrValue = null;
        }
        values.add(jcrValue);
    }

    /**
     * Return the column names representing the names of the properties present
     * in the {@link #getValues() values}.
     *
     * @return columnNames
     */
    public String[] getColumnNames() {
        return columnNames;
    }

    /**
     * Return the values representing the values of that row in the search
     * result table.
     *
     * @return values
     * @see javax.jcr.query.Row#getValues()
     */
    public Value[] getValues() {
        return values;
    }


    /**
     * Same as {@link #getValues()}
     *
     * @return Array of JCR Value object
     */
    public Object getValue() {
        return values;
    }

    /**
     * Return the xml representation of this webdav property. For every value in
     * the query result row a dcr:name, dcr:value and dcr:type element is created.
     * Example:
     * <pre>
     * -----------------------------------------------------------
     *   col-name  |   bla   |   bli   |  jcr:path  |  jcr:score
     * -----------------------------------------------------------
     *   value     |   xxx   |   111   |  /aNode    |    1
     *   type      |    1    |    3    |     8      |    3
     * -----------------------------------------------------------
     * </pre>
     * results in:
     * <pre>
     * &lt;dcr:search-result-property xmlns:dcr="http://www.day.com/jcr/webdav/1.0"&gt;
     *    &lt;dcr:column&gt;
     *       &lt;dcr:name&gt;bla&lt;dcr:name/&gt;
     *       &lt;dcr:value dcr:type="String"&gt;xxx&lt;dcr:value/&gt;
     *    &lt;/dcr:column&gt;
     *    &lt;dcr:column&gt;
     *       &lt;dcr:name&gt;bli&lt;dcr:name/&gt;
     *       &lt;dcr:value dcr:type="Long"&gt;111&lt;dcr:value/&gt;
     *    &lt;/dcr:column&gt;
     *    &lt;dcr:column&gt;
     *       &lt;dcr:name&gt;jcr:path&lt;dcr:name/&gt;
     *       &lt;dcr:value dcr:type="Path"&gt;/aNode&lt;dcr:value/&gt;
     *    &lt;/dcr:column&gt;
     *    &lt;dcr:column&gt;
     *       &lt;dcr:name&gt;jcr:score&lt;dcr:name/&gt;
     *       &lt;dcr:value dcr:type="Long"&gt;1&lt;dcr:value/&gt;
     *    &lt;/dcr:column&gt;
     * &lt;/dcr:search-result-property&gt;
     * </pre>
     *
     * @see org.apache.jackrabbit.webdav.xml.XmlSerializable#toXml(org.w3c.dom.Document)
     */
    public Element toXml(Document document) {
        Element elem = getName().toXml(document);
        for (int i = 0; i < columnNames.length; i++) {
            String propertyName = columnNames[i];
            Value propertyValue = values[i];

            Element columnEl = DomUtil.addChildElement(elem, COLUMN, ItemResourceConstants.NAMESPACE);
            DomUtil.addChildElement(columnEl, JCR_NAME.getName(), JCR_NAME.getNamespace(), propertyName);
            if (propertyValue != null) {
                try {
                    String serializedValue = ValueHelper.serialize(propertyValue, true);
                    Element xmlValue = DomUtil.addChildElement(columnEl, XML_VALUE, ItemResourceConstants.NAMESPACE, serializedValue);
                    String type = PropertyType.nameFromValue(propertyValue.getType());
                    DomUtil.setAttribute(xmlValue, ATTR_VALUE_TYPE, ItemResourceConstants.NAMESPACE, type);
                } catch (RepositoryException e) {
                    log.error(e.toString());
                }
            }
        }
        return elem;
    }
}
