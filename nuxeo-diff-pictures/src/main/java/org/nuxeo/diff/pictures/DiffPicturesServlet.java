/*
 * (C) Copyright 2015 Nuxeo SA (http://nuxeo.com/) and contributors.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Lesser General Public License
 * (LGPL) version 2.1 which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/lgpl-2.1.html
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * Contributors:
 *     thibaud
 */
package org.nuxeo.diff.pictures;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.util.HashMap;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.CoreInstance;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.IdRef;
import org.nuxeo.ecm.core.api.repository.RepositoryManager;
import org.nuxeo.ecm.platform.commandline.executor.api.CommandNotAvailable;
import org.nuxeo.ecm.platform.web.common.ServletHelper;
import org.nuxeo.runtime.api.Framework;

/**
 * /nuxeo/diffPictures?&action=diff&repo=therepo&leftDocId=123456&rightDocId= 456789012
 * &xpath=file:content&commandLine=thecommandline&fuzz=1000&highlightColor= Red&lowlightColor=White Depending on the
 * value of "action", other parameters are used or not
 * <ul>
 * <li>diff
 * <ul>
 * <li>This is the default value if <code>action</code> is not passed</li>
 * <li><code>leftDocId</code> and <code>rightDocId</code> are required</li>
 * <li>All other parameters are optional (commandLine, xpath, fuzz, ...). Default values are defined in
 * <code>DiffPictures</code></li>
 * </ul>
 * </li>
 * <li>cleanup
 * <ul>
 * <li>Tells the code to delete the temporary files. It is a good habit to ask for the cleanup<br/>
 * Returns a text message letting the caller knows everything went well.</li>
 * <li><code>leftDocId</code> and <code>rightDocId</code> are required (used to build unique file names)</li>
 * <li>Other parameters are not used</li>
 * </ul>
 * </li>
 * </ul>
 * commandine, xpath, fuzz, highlightColor, lowlightColor and repo are optional Utility to force cleanup of temporary
 * files created on the server: /nuxeo/diffPictures?cleanup=true&leftDocId=123456&rightDocId=456789& WARNING: Why can't
 * I use the public static void downloadBlob() from DownloadServlet?
 *
 * @since 7.3
 */
public class DiffPicturesServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    private static final Log log = LogFactory.getLog(DiffPicturesServlet.class);

    protected static final int BUFFER_SIZE = 1024 * 512;

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {

        DocumentModel leftDoc, rightDoc;

        String action = req.getParameter("action");
        String leftDocId = req.getParameter("leftDocId");
        String rightDocId = req.getParameter("rightDocId");

        if (StringUtils.isBlank(leftDocId)) {
            sendTextResponse(resp, "you must specify a left document as origin");
            return;
        }
        if (StringUtils.isBlank(rightDocId)) {
            sendTextResponse(resp, "you must specify 'right' used for comparison against the left document.");
            return;
        }

        if (StringUtils.isBlank(action)) {
            action = "diff";
        }
        action = action.toLowerCase();

        switch (action) {
        case "cleanup":
            TempFilesHandler.deleteTempFolder(leftDocId, rightDocId);
            sendTextResponse(resp, "Temporaty files deleted");
            return;
        }

        // Ok, not a special action, let's diff.

        String repo = req.getParameter("repo");
        String xpath = req.getParameter("xpath");
        String commandLine = req.getParameter("commandLine");
        String fuzz = req.getParameter("fuzz");
        String highlightColor = req.getParameter("highlightColor");
        String lowlightColor = req.getParameter("lowlightColor");

        if (StringUtils.isBlank(repo)) {
            repo = Framework.getLocalService(RepositoryManager.class).getDefaultRepository().getName();
        }

        // This try-with-resources does an implicit close() at the end
        try (CoreSession coreSession = CoreInstance.openCoreSession(repo)) {

            leftDoc = coreSession.getDocument(new IdRef(leftDocId));
            rightDoc = coreSession.getDocument(new IdRef(rightDocId));

            DiffPictures dp = new DiffPictures(leftDoc, rightDoc, xpath);

            HashMap<String, Serializable> params = new HashMap<String, Serializable>();
            if (StringUtils.isNotBlank(fuzz)) {
                params.put("fuzz", fuzz);
            }
            if (StringUtils.isNotBlank(highlightColor)) {
                params.put("highlightColor", highlightColor);
            }
            if (StringUtils.isNotBlank(lowlightColor)) {
                params.put("lowlightColor", lowlightColor);
            }

            Blob bResult;
            try {
                bResult = dp.compare(commandLine, params);
            } catch (CommandNotAvailable | IOException e) {
                log.error("Unable to compare the pictures", e);
                sendTextResponse(resp, "Unable to compare the pictures");
                return;
            }

            resp.setHeader("Cache-Control", "no-cache");
            resp.setHeader("Pragma", "no-cache");
            try {
                sendBlobResult(req, resp, bResult);
            } catch (IOException e) {
                log.error("Unable to handleCompareResult", e);
                sendTextResponse(resp, "Unable to return the result");
                return;
            }
        }
    }

    protected void sendTextResponse(HttpServletResponse resp, String response) throws IOException {

        resp.setContentType("text/plain");
        resp.setContentLength(response.getBytes().length);
        OutputStream out = resp.getOutputStream();
        out.write(response.getBytes());
        out.close();

    }

    protected void sendBlobResult(HttpServletRequest req, HttpServletResponse resp, Blob blob) throws IOException {

        log.warn("Should use DownloadServlet.downloadBlob with 7.3 and higher");

        InputStream in = blob.getStream();
        OutputStream out = resp.getOutputStream();
        String fileName = blob.getFilename();

        resp.setHeader("Content-Disposition", ServletHelper.getRFC2231ContentDisposition(req, fileName));
        resp.setContentType(blob.getMimeType());
        long fileSize = blob.getLength();
        resp.setContentLength((int) fileSize);

        byte[] buffer = new byte[BUFFER_SIZE];
        int bytesRead;
        while ((bytesRead = in.read(buffer)) != -1) {
            out.write(buffer, 0, bytesRead);
        }

    }
}
