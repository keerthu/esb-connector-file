/*
* Copyright (c) 2016, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
*
* WSO2 Inc. licenses this file to you under the Apache License,
* Version 2.0 (the "License"); you may not use this file except
* in compliance with the License.
* You may obtain a copy of the License at
*
*http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing,
* software distributed under the License is distributed on an
* "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
* KIND, either express or implied. See the License for the
* specific language governing permissions and limitations
* under the License.
*/

package org.wso2.carbon.connector.util;

import org.apache.axiom.om.OMAbstractFactory;
import org.apache.axiom.om.OMElement;
import org.apache.axiom.om.OMFactory;
import org.apache.axiom.om.OMNamespace;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.commons.vfs2.*;
import org.apache.commons.vfs2.impl.StandardFileSystemManager;
import org.apache.synapse.MessageContext;

import java.io.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class FileUnzipUtil {
    private static final Log log = LogFactory.getLog(FileUnzipUtil.class);
    private static StandardFileSystemManager manager = null;

    /**
     * @param source        Location of the zip file
     * @param destDirectory Location of the destination folder
     */
    public boolean unzip(String source, String destDirectory, MessageContext messageContext) {
        OMFactory factory = OMAbstractFactory.getOMFactory();
        OMNamespace ns = factory.createOMNamespace(FileConstants.FILECON, FileConstants.NAMESPACE);
        OMElement result = factory.createOMElement(FileConstants.RESULT, ns);
        boolean resultStatus = false;

        try {
            manager = FileConnectorUtils.getManager();
            FileSystemOptions sourceFso = FileConnectorUtils.getFso(messageContext, source, manager);
            FileSystemOptions destinationFso = FileConnectorUtils.getFso(messageContext, destDirectory, manager);

            // Create remote object
            FileObject remoteFile = manager.resolveFile(source, sourceFso);
            FileObject remoteDesFile = manager.resolveFile(destDirectory, destinationFso);
            // File destDir = new File(destDirectory);
            if (remoteFile.exists()) {
                if (!remoteDesFile.exists()) {
                    //create a folder
                    remoteDesFile.createFolder();
                }
                //open the zip file
                ZipInputStream zipIn = new ZipInputStream(remoteFile.getContent().getInputStream());
                ZipEntry entry = zipIn.getNextEntry();
                try {
                    // iterates over entries in the zip file
                    while (entry != null) {
                        // boolean testResult;
                        String[] urlParts = destDirectory.split("\\?");
                        String filePath;
                        if (urlParts.length > 1) {
                            String urlWithoutParam = urlParts[0];
                            String param = urlParts[1];
                            filePath = urlWithoutParam + File.separator + entry.getName() +
                                    FileConstants.QUERY_PARAM_SEPARATOR + param;
                        } else {
                            filePath = destDirectory + File.separator + entry.getName();
                        }
                        // Create remote object
                        FileObject remoteFilePath = manager.resolveFile(filePath, destinationFso);
                        if (log.isDebugEnabled()) {
                            log.debug("The created path is " + remoteFilePath.toString());
                        }
                        try {
                            if (!entry.isDirectory()) {
                                // if the entry is a file, extracts it
                                extractFile(zipIn, filePath, destinationFso);
                                OMElement messageElement = factory.createOMElement(FileConstants.FILE
                                        , ns);
                                messageElement.setText(entry.getName() + " | status:" + "true");
                                result.addChild(messageElement);
                            } else {
                                // if the entry is a directory, make the directory
                                remoteFilePath.createFolder();
                            }
                        } catch (IOException e) {
                            log.error("Unable to process the zip file. ", e);
                        } finally {
                            zipIn.closeEntry();
                            entry = zipIn.getNextEntry();
                        }
                    }
                    messageContext.getEnvelope().getBody().addChild(result);
                    resultStatus = true;
                } finally {
                    //we must always close the zip file
                    zipIn.close();
                }
            } else {
                log.error("File does not exist.");
            }
        } catch (IOException e) {
            log.error("Unable to process the zip file." + e.getMessage(), e);
        } finally {
            manager.close();
        }
        return resultStatus;
    }

    /**
     * @param zipIn    :Input zip stream
     * @param filePath :Location of each entry of the file.
     */
    private void extractFile(ZipInputStream zipIn, String filePath, FileSystemOptions opts) {
        BufferedOutputStream bos = null;
        try {
            // Create remote object
            FileObject remoteFilePath = manager.resolveFile(filePath, opts);
            //open the zip file
            OutputStream fOut = remoteFilePath.getContent().getOutputStream();
            bos = new BufferedOutputStream(fOut);
            byte[] bytesIn = new byte[FileConstants.BUFFER_SIZE];
            int read;
            while ((read = zipIn.read(bytesIn)) != -1) {
                bos.write(bytesIn, 0, read);
            }
        } catch (IOException e) {
            log.error("Unable to read an entry: " + e.getMessage(), e);
        } finally {
            //we must always close the zip file
            if (bos != null) {
                try {
                    bos.close();
                } catch (IOException e) {
                    log.error("Error while closing the BufferedOutputStream: " + e.getMessage(), e);
                }
            }
        }
    }
}