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
 *     Thibaud Arguillere
 */
package org.nuxeo.diff.pictures.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.common.utils.FileUtils;
import org.nuxeo.diff.pictures.ImageMagickContentDiffAdapter;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.impl.blob.FileBlob;
import org.nuxeo.ecm.core.test.CoreFeature;
import org.nuxeo.ecm.core.test.TransactionalFeature;
import org.nuxeo.ecm.diff.content.ContentDiffAdapter;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;

import com.google.inject.Inject;

/**
 * @since 7.4
 */
@RunWith(FeaturesRunner.class)
@Features({ TransactionalFeature.class, CoreFeature.class })
@Deploy({ "org.nuxeo.ecm.platform.convert:OSGI-INF/convert-service-contrib.xml", "org.nuxeo.diff.content",
        "org.nuxeo.diff.pictures", "org.nuxeo.ecm.platform.rendition.core", "org.nuxeo.ecm.platform.picture.core",
        "org.nuxeo.ecm.platform.commandline.executor" })
public class TestContentDiffIntegration {

    protected static final String ISLAND_PNG = "island.png";

    protected static final String ISLAND_MODIF_PNG = "island-modif.png";

    protected static final int ISLAND_W = 400;

    protected static final int ISLAND_H = 282;

    DocumentModel parentOfTestDocs;

    DocumentModel leftDoc;

    DocumentModel rightDoc;

    @Inject
    CoreSession coreSession;

    protected DocumentModel createPictureDocument(File inFile) {

        DocumentModel pictDoc = coreSession.createDocumentModel(parentOfTestDocs.getPathAsString(), inFile.getName(),
                "Picture");
        pictDoc.setPropertyValue("dc:title", inFile.getName());
        pictDoc.setPropertyValue("file:content", new FileBlob(inFile));
        return coreSession.createDocument(pictDoc);

    }

    @Before
    public void setUp() {

        parentOfTestDocs = coreSession.createDocumentModel("/", "test-diff-pictures", "Folder");
        parentOfTestDocs.setPropertyValue("dc:title", "test-diff-pictures");
        parentOfTestDocs = coreSession.createDocument(parentOfTestDocs);

        leftDoc = createPictureDocument(FileUtils.getResourceFileFromContext(ISLAND_PNG));
        rightDoc = createPictureDocument(FileUtils.getResourceFileFromContext(ISLAND_MODIF_PNG));

        coreSession.save();
    }

    @After
    public void cleanup() {

        coreSession.removeDocument(parentOfTestDocs.getRef());
        coreSession.save();
    }

    @Test
    public void testPictureDocumentsContentDiffAdapter() throws Exception {

        ContentDiffAdapter contentDiffAdapter = leftDoc.getAdapter(ContentDiffAdapter.class);
        assertNotNull(contentDiffAdapter);
        assertTrue(contentDiffAdapter instanceof ImageMagickContentDiffAdapter);

        ImageMagickContentDiffAdapter imcda = (ImageMagickContentDiffAdapter) contentDiffAdapter;
        List<Blob> contentDiffBlobs = imcda.getFileContentDiffBlobs(rightDoc, null, null);

        assertNotNull(contentDiffBlobs);
        assertEquals(1, contentDiffBlobs.size());

        Blob result = contentDiffBlobs.get(0);
        assertNotNull(result);
        // The result is a StringBlob containing the HTML to display
        // (in the template nuxeo-diff-pictures-template.html)
        // For testing, let's check the blob contains the 2 IDs of our documents
        String html = result.getString();
        assertTrue(html.indexOf(leftDoc.getId()) > -1);
        assertTrue(html.indexOf(rightDoc.getId()) > -1);

    }

}
