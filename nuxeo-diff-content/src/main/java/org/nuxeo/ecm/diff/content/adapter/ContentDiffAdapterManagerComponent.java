/*
 * (C) Copyright 2006-2018 Nuxeo (http://nuxeo.com/) and others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package org.nuxeo.ecm.diff.content.adapter;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.blobholder.BlobHolder;
import org.nuxeo.ecm.diff.content.ContentDiffAdapter;
import org.nuxeo.ecm.diff.content.ContentDiffException;
import org.nuxeo.ecm.diff.content.MimeTypeDescriptor;
import org.nuxeo.ecm.diff.content.MimeTypesDescriptor;
import org.nuxeo.ecm.diff.content.adapter.factories.BlobHolderContentDiffAdapterFactory;
import org.nuxeo.ecm.diff.content.adapter.factories.FileBasedContentDiffAdapterFactory;
import org.nuxeo.runtime.model.ComponentInstance;
import org.nuxeo.runtime.model.DefaultComponent;

/**
 * Runtime component that handles the extension points and the service interface for content diff Adapter management.
 *
 * @author Antoine Taillefer
 */
public class ContentDiffAdapterManagerComponent extends DefaultComponent implements ContentDiffAdapterManager {

    public static final String ADAPTER_FACTORY_EP = "adapterFactory";

    public static final String MIME_TYPE_CONTENT_DIFFER_EP = "mimeTypeContentDiffer";

    /**
     * @since 10.10
     */
    public static final String HTML_CONVERSION_BLACKLISTED_MIME_TYPES_EP = "htmlConversionBlacklistedMimeTypes";

    private static final Log log = LogFactory.getLog(ContentDiffAdapterManagerComponent.class);

    protected Map<String, ContentDiffAdapterFactory> factoryRegistry = new HashMap<>();

    protected Map<String, MimeTypeContentDiffer> contentDifferFactory = new HashMap<>();

    protected Map<String, MimeTypeContentDiffer> contentDifferFactoryByName = new HashMap<>();

    protected Set<String> htmlConversionBlacklistedMimeTypes = new HashSet<>();

    // Component and EP management

    @Override
    public void registerContribution(Object contribution, String extensionPoint, ComponentInstance contributor) {

        if (ADAPTER_FACTORY_EP.equals(extensionPoint)) {
            ContentDiffAdapterFactoryDescriptor desc = (ContentDiffAdapterFactoryDescriptor) contribution;
            if (desc.isEnabled()) {
                ContentDiffAdapterFactory factory = desc.getNewInstance();
                if (factory != null) {
                    factoryRegistry.put(desc.getTypeName(), factory);
                }
            } else {
                factoryRegistry.remove(desc.getTypeName());
            }
        } else if (MIME_TYPE_CONTENT_DIFFER_EP.equals(extensionPoint)) {
            MimeTypeContentDifferDescriptor desc = (MimeTypeContentDifferDescriptor) contribution;
            try {
                MimeTypeContentDiffer contentDiffer = desc.getKlass().newInstance();
                contentDifferFactory.put(desc.getPattern(), contentDiffer);

                // Also (since 7.4) add a name in the contribution
                String name = desc.getName();
                if (StringUtils.isNotBlank(name)) {
                    contentDifferFactoryByName.put(name, contentDiffer);
                }
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException(e);
            }
        } else if (HTML_CONVERSION_BLACKLISTED_MIME_TYPES_EP.equals(extensionPoint)) {
            MimeTypesDescriptor desc = (MimeTypesDescriptor) contribution;
            if (desc.isOverride()) {
                // override the whole list
                htmlConversionBlacklistedMimeTypes = desc.getMimeTypes()
                                                         .stream()
                                                         .filter(MimeTypeDescriptor::isEnabled)
                                                         .map(MimeTypeDescriptor::getName)
                                                         .collect(Collectors.toSet());
            } else {
                desc.getMimeTypes().forEach(mimeType -> {
                    Consumer<String> consumer = mimeType.isEnabled() ? htmlConversionBlacklistedMimeTypes::add
                            : htmlConversionBlacklistedMimeTypes::remove;
                    consumer.accept(mimeType.getName());
                });
            }
        }
    }

    @Override
    public void unregisterContribution(Object contribution, String extensionPoint, ComponentInstance contributor) {
    }

    // Service interface impl

    @Override
    public boolean hasAdapter(DocumentModel doc) {
        if (doc == null) {
            return false;
        }

        String docType = doc.getType();
        if (factoryRegistry.containsKey(docType)) {
            return true;
        }

        return doc.hasSchema("file") || doc.hasSchema("files");
    }

    @Override
    public ContentDiffAdapter getAdapter(DocumentModel doc) {
        if (doc == null) {
            return null;
        }

        String docType = doc.getType();

        log.debug("Looking for ContentDiffAdapter for type " + docType);

        if (factoryRegistry.containsKey(docType)) {
            log.debug("Dedicated ContentDiffAdapter factory found");
            return factoryRegistry.get(docType).getAdapter(doc);
        }

        if (doc.isFolder()) {
            return null;
        }

        BlobHolder bh = doc.getAdapter(BlobHolder.class);
        if (bh != null) {
            log.debug("Using Blob Holder based ContentDiffAdapter factory");
            ContentDiffAdapterFactory factory = new BlobHolderContentDiffAdapterFactory();
            return factory.getAdapter(doc);

        }

        if (doc.hasSchema("file") || doc.hasSchema("files")) {
            log.debug("Using default file based ContentDiffAdapter factory");
            ContentDiffAdapterFactory factory = new FileBasedContentDiffAdapterFactory();
            return factory.getAdapter(doc);
        } else {
            return null;
        }
    }

    @Override
    public MimeTypeContentDiffer getContentDiffer(String mimeType) {
        for (Map.Entry<String, MimeTypeContentDiffer> entry : contentDifferFactory.entrySet()) {
            if (mimeType.matches(entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }

    @Override
    public MimeTypeContentDiffer getContentDifferForName(String name) {
        for (Map.Entry<String, MimeTypeContentDiffer> entry : contentDifferFactoryByName.entrySet()) {
            if (name.equals(entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }

    @Override
    public HtmlContentDiffer getHtmlContentDiffer() throws ContentDiffException {
        MimeTypeContentDiffer htmlContentDiffer = contentDifferFactory.get("text/html");
        if (htmlContentDiffer == null || !(htmlContentDiffer instanceof HtmlContentDiffer)) {
            throw new ContentDiffException(
                    "No content differ of type HtmlContentDiffer found for the 'text/html' mime-type. Please check the 'mimeTypeContentDiffer' contributions.");
        }
        return (HtmlContentDiffer) htmlContentDiffer;
    }

    @Override
    public Set<String> getHtmlConversionBlacklistedMimeTypes() {
        return htmlConversionBlacklistedMimeTypes;
    }

}
