/*
 * (C) Copyright 2012 Nuxeo SA (http://nuxeo.com/) and contributors.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Lesser General Public License
 * (LGPL) version 2.1 which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/lgpl.html
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * Contributors:
 *     ataillefer
 */
package org.nuxeo.ecm.platform.diff.model.impl;

import java.util.HashMap;
import java.util.Map;

import org.nuxeo.ecm.platform.diff.model.PropertyDiff;
import org.nuxeo.ecm.platform.diff.model.PropertyType;

/**
 * Implementation of PropertyDiff for a complex property.
 * 
 * @author <a href="mailto:ataillefer@nuxeo.com">Antoine Taillefer</a>
 */
public class ComplexPropertyDiff implements PropertyDiff {

    private static final long serialVersionUID = -1100714461537900354L;

    private Map<String, PropertyDiff> diffMap;

    /**
     * Instantiates a new complex property diff.
     */
    public ComplexPropertyDiff() {
        diffMap = new HashMap<String, PropertyDiff>();
    }

    public PropertyType getPropertyType() {
        return PropertyType.complex;
    }

    /**
     * Gets the diff.
     * 
     * @param item the item
     * @return the diff
     */
    public PropertyDiff getDiff(String item) {
        return diffMap.get(item);
    }

    /**
     * Put diff.
     * 
     * @param item the item
     * @param diff the diff
     * @return the property diff
     */
    public PropertyDiff putDiff(String item, PropertyDiff diff) {
        return diffMap.put(item, diff);
    }

    public Map<String, PropertyDiff> getDiffMap() {
        return diffMap;
    }

    public void setDiffMap(Map<String, PropertyDiff> diffMap) {
        this.diffMap = diffMap;
    }

    @Override
    public boolean equals(Object other) {

        if (this == other) {
            return true;
        }
        if (other == null || !(other instanceof ComplexPropertyDiff)) {
            return false;
        }
        Map<String, PropertyDiff> otherDiffMap = ((ComplexPropertyDiff) other).getDiffMap();
        return (diffMap == null && otherDiffMap == null)
                || (diffMap != null && otherDiffMap != null && diffMap.equals(otherDiffMap));

    }

    @Override
    public String toString() {

        return diffMap.toString();
    }
}
