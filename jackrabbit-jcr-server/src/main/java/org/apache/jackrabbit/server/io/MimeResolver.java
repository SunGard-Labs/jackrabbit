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
package org.apache.jackrabbit.server.io;

import org.apache.jackrabbit.util.Text;

import java.io.IOException;
import java.util.Properties;

/**
 * This Class implements a very simple mime type resolver.
 */
public class MimeResolver {

    /**
     * the loaded mimetypes
     */
    private Properties mimeTypes = new Properties();

    /**
     * the default mimetype
     */
    private String defaultMimeType = "application/octet-stream";

    /**
     * Creates a new mimetype resolver containing the default mappings and having
     * "application/octet-stream" set as default mimetype.
     */
    public MimeResolver() {
        try {
            // init the mime types
            mimeTypes.load(getClass().getResourceAsStream("mimetypes.properties"));
        } catch (IOException e) {
            throw new InternalError("Unable to load mimetypes: " + e.toString());
        }
    }

    /**
     * Creates a new mime type resolver extending the default mapping by the
     * entries of the given Properties. The default mimetype is set to the
     * given <code>defaultMimeType</code>.
     *
     * @param additionalProperties MimeType mappings to be added to the default
     * properties.
     * @param defaultMimeType The default mimetype. A non-null String with a
     * length greater than 0.
     */
    public MimeResolver(Properties additionalProperties, String defaultMimeType) {
        // init default mimetypes.
        this();
        // extend or adjust mapping.
        if (additionalProperties != null && !additionalProperties.isEmpty()) {
            mimeTypes.putAll(additionalProperties);
        }
        // set the default type.
        if (defaultMimeType != null && defaultMimeType.length() > 0) {
            this.defaultMimeType = defaultMimeType;
        }
    }

    /**
     * Returns the default mime type
     * @return
     */
    public String getDefaultMimeType() {
        return defaultMimeType;
    }

    /**
     * Sets the default mime type
     * @param defaultMimeType
     */
    public void setDefaultMimeType(String defaultMimeType) {
        this.defaultMimeType = defaultMimeType;
    }

    /**
     * Retrusn the mime type for the given name.
     * @param filename
     * @return
     */
    public String getMimeType(String filename) {
        String ext = Text.getName(filename, '.');
        if (ext.equals("")) {
            ext = filename;
        }
        return mimeTypes.getProperty(ext.toLowerCase(), defaultMimeType);
    }
}
