package com.hypersonicsharkz.util;

import com.google.gson.JsonElement;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

public class IndexNode {
    String name;
    IndexNode parent;
    Map<String, IndexNode> children;

    int[] indexMapping;

    public IndexNode(String name, IndexNode parent, JsonElement targetElement) {
        this.name = name;
        this.parent = parent;
        this.children = new HashMap<>();

        if (targetElement != null && targetElement.isJsonArray()) {
            indexMapping = IntStream.range(0, targetElement.getAsJsonArray().size()).toArray();
        }
    }

    public IndexNode getOrCreateChild(String childName, JsonElement targetElement) {
        return children.computeIfAbsent(childName, k -> new IndexNode(childName, this, targetElement));
    }

    public int getMappedIndex(int originalIndex, boolean lookahead) {
        if (indexMapping == null || originalIndex < 0 || originalIndex >= indexMapping.length) {
            return -1; // Invalid index
        }

        int mappedIndex = indexMapping[originalIndex];
        if (lookahead && mappedIndex == -1) {
            // Look ahead for the next valid index
            for (int i = originalIndex + 1; i < indexMapping.length; i++) {
                if (indexMapping[i] != -1) {
                    return indexMapping[i];
                }
            }
            // look back for the previous valid index
            for (int i = originalIndex - 1; i >= 0; i--) {
                if (indexMapping[i] != -1) {
                    return indexMapping[i] + 1;
                }
            }
            return -1; // No valid index found
        }

        return indexMapping[originalIndex];
    }

    public void incrementAbove(int index) {
        for (int i = 0; i < indexMapping.length; i++) {
            if (i >= index && indexMapping[i] >= 0) {
                indexMapping[i]++;
            }
        }
    }

    public void decrementAbove(int index) {
        for (int i = 0; i < indexMapping.length; i++) {
            if (i >= index && indexMapping[i] > 0) {
                indexMapping[i]--;
            }
        }
    }

    public void invalidateIndex(int index) {
        if (index < 0 || index >= indexMapping.length) {
            return; // Invalid index
        }
        indexMapping[index] = -1; // Mark as invalid
    }
}

