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
package org.apache.jackrabbit.server.util;

import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.FileItemFactory;
import org.apache.commons.fileupload.FileUploadException;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServletRequest;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * <code>HttpMultipartPost</code>...
 */
class HttpMultipartPost {

    /**
     * logger instance
     */
    private static final Logger log = LoggerFactory.getLogger(HttpMultipartPost.class);

    private final Map nameToItems = new LinkedHashMap();
    private final Set fileParamNames = new HashSet();

    private boolean initialized;

    HttpMultipartPost(HttpServletRequest request, File tmpDir) throws IOException {
        extractMultipart(request, tmpDir);
        initialized = true;
    }

    /**
     *
     * @param tmpDir
     * @return
     */
    private static FileItemFactory getFileItemFactory(File tmpDir) {
        DiskFileItemFactory fiFactory = new DiskFileItemFactory(DiskFileItemFactory.DEFAULT_SIZE_THRESHOLD, tmpDir);
        return fiFactory;
    }

    /**
     * 
     * @param request
     * @param tmpDir
     * @throws IOException
     */
    private void extractMultipart(HttpServletRequest request, File tmpDir)
            throws IOException {
        if (!ServletFileUpload.isMultipartContent(request)) {
            log.debug("Request does not contain multipart content -> ignoring.");
            return;
        }

        ServletFileUpload upload = new ServletFileUpload(getFileItemFactory(tmpDir));
        try {
            List fileItems = upload.parseRequest(request);
            for (Iterator it = fileItems.iterator(); it.hasNext();) {
                FileItem item = (FileItem) it.next();
                addItem(item);
            }
        } catch (FileUploadException e) {
            log.error("Error while processing multipart.", e);
            throw new IOException(e.toString());
        }
    }

    /**
     * Add the given file item to the list defined for its name and make the
     * list is present in the map. If the item does not represent a simple
     * form field its name is also added to the <code>fileParamNames</code> set.
     *
     * @param item The {@link FileItem} to add.
     */
    private void addItem(FileItem item) {
        String name = item.getFieldName();
        ArrayList l = (ArrayList) nameToItems.get(item.getFieldName());
        if (l == null) {
            l = new ArrayList();
            nameToItems.put(name, l);
        }
        l.add(item);

        // if file parameter, add name to the set of file params in order to
        // be able to extract the file param values later on without iterating
        // over all keys.
        if (!item.isFormField()) {
            fileParamNames.add(name);
        }
    }

    private void checkInitialized() {
        if (!initialized) {
            throw new IllegalStateException("HttpMultipartPost not initialized (or already disposed).");
        }
    }

    /**
     * Release all file items hold with the name-to-items map. specially those
     * having a tmp-file associated with.
     * 
     * @see FileItem#delete()
     */
    synchronized void dispose() {
        checkInitialized();

        for (Iterator it = nameToItems.values().iterator(); it.hasNext();) {
            List fileItems = (List) it.next();
            for (int i = 0; i < fileItems.size(); i++) {
                FileItem item = (FileItem) fileItems.get(i);
                item.delete();
            }
        }

        nameToItems.clear();
        fileParamNames.clear();
        initialized = false;
    }

    /**
     * Returns an iterator over all file item names.
     *
     * @return a set of strings.
     */
    Set getParameterNames() {
        checkInitialized();
        return nameToItems.keySet();
    }


    /**
     * Returns the content types of the paramaters with the given name. If
     * the parameter does not exist <code>null</code> is returned. If the content
     * type of any of the parameter values is not known, the corresponding entry
     * in the array returned is <code>null</code>.
     * <p>
     * The content type of a paramater is only known here if the information
     * has been sent by the client browser. This is generally only the case
     * for file upload fields of HTML forms which have been posted using the
     * HTTP <em>POST</em> with <em>multipart/form-data</em> encoding.
     * <p>
     * Example : For the form
     * <pre>
         <form name="Upload" method="POST" ENCTYPE="multipart/form-data">
            <input type="file" name="Upload"><br>
            <input type="text" name="Upload"><br>
            <input type="submit">
         </form>
     * </pre>
     * this method will return an array of two entries when called for the
     * <em>Upload</em> parameter. The first entry will contain the content
     * type (if transmitted by the client) of the file uploaded. The second
     * entry will be <code>null</code> because the content type of the text
     * input field will generally not be sent by the client.
     *
     * @param name The name of the paramater whose content type is to be
     * returned.
     * @return The content types of the file items with the specified name.
     */
    String[] getParameterTypes(String name) {
        checkInitialized();
        String[] cts = null;
        List l = (List) nameToItems.get(name);
        if (l != null && !l.isEmpty()) {
            cts = new String[l.size()];
            for (int i = 0; i < cts.length; i++) {
                cts[i] = ((FileItem) l.get(i)).getContentType();
            }
        }
        return cts;
    }
    
    /**
     * Returns the first value of the file items with the given <code>name</code>.
     * The byte to string converstion is done using either the contenttype of
     * the file items or the <code>formEncoding</code>.
     * <p/>
     * Please note that if the addressed parameter is an uploaded file rather
     * than a simple form entry, the name of the original file is returned    
     * instead of the content.
     *
     * @param name the name of the parameter
     * @return the string of the first value or <code>null</code> if the
     *         parameter does not exist
     */
    String getParameter(String name) {
        checkInitialized();
        List l = (List) nameToItems.get(name);
        if (l == null || l.isEmpty()) {
            return null;
        } else {
            FileItem item = ((FileItem) l.get(0));
            if (item.isFormField()) {
                return item.getString();
            } else {
                return item.getName();
            }
        }
    }

    /**
     * Returns an array of Strings with all values of the parameter addressed
     * by <code>name</code>. the byte to string conversion is done using either
     * the content type of the multipart body or the <code>formEncoding</code>.
     * <p/>
     * Please note that if the addressed parameter is an uploaded file rather
     * than a simple form entry, the name of the original file is returned
     * instead of the content.
     *
     * @param name the name of the parameter
     * @return a string array of values or <code>null</code> if no entry with the
     * given name exists.
     */
    String[] getParameterValues(String name) {
        checkInitialized();
        List l = (List) nameToItems.get(name);
        if (l == null || l.isEmpty()) {
            return null;
        } else {
            String[] values = new String[l.size()];
            for (int i = 0; i < values.length; i++) {
                FileItem item = ((FileItem) l.get(i));
                if (item.isFormField()) {
                    values[i] = item.getString();
                } else {
                    values[i] = item.getName();
                }
            }
            return values;
        }
    }

    /**
     * Returns a set of the file parameter names. An empty set if
     * no file parameters were present in the request.
     *
     * @return an set of file item names representing the file
     * parameters available with the request.
     */
    Set getFileParameterNames() {
        checkInitialized();
        return fileParamNames;
    }
    
    /**
     * Returns an array of input streams for uploaded file parameters.
     *
     * @param name the name of the file parameter(s)
     * @return an array of input streams or <code>null</code> if no file params
     * with the given name exist.
     * @throws IOException if an I/O error occurs
     */
    InputStream[] getFileParameterValues(String name) throws IOException {
        checkInitialized();
        InputStream[] values = null;
        if (fileParamNames.contains(name)) {
            List l = (List) nameToItems.get(name);
            if (l != null && !l.isEmpty()) {
                List ins = new ArrayList(l.size());
                for (Iterator it = l.iterator(); it.hasNext();) {
                    FileItem item = (FileItem) it.next();
                    if (!item.isFormField()) {
                        ins.add(item.getInputStream());
                    }
                }
                values = (InputStream[]) ins.toArray(new InputStream[ins.size()]);
            }
        }
        return values;
    }
}
