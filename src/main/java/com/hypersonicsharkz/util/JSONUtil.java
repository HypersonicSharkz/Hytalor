package com.hypersonicsharkz.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import com.hypersonicsharkz.HytalorPlugin;

import java.io.BufferedReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Level;

public class JSONUtil {
    public static JsonObject readJSON(Path path) {
        try (
                BufferedReader fileReader = Files.newBufferedReader(path);
                JsonReader reader = new JsonReader(fileReader);
        ) {
            return JsonParser.parseReader(reader).getAsJsonObject();

        } catch (Exception e) {
            HytalorPlugin.get().getLogger()
                    .at(Level.SEVERE)
                    .log("Failed to read JSON file at path: " + path, e);
            return null;
        }
    }

    public static void deepMerge(JsonObject source, JsonObject target, IndexNode indexNode) {
        for (String key: source.keySet()) {
            if (key.equals("BaseAssetPath"))
                continue;

            JsonElement sourceValue = source.get(key);

            if (!target.has(key)) {
                target.add(key, sourceValue);
                continue;
            }

            JsonElement targetValue = target.get(key);

            IndexNode childNode = indexNode.getOrCreateChild(key, targetValue);

            // existing value for "key" - recursively deep merge:
            if (sourceValue.isJsonObject() && targetValue.isJsonObject()) {
                deepMerge(sourceValue.getAsJsonObject(), targetValue.getAsJsonObject(), childNode);
            } else if (sourceValue.isJsonArray() && targetValue.isJsonArray()) {
                target.add(key, mergeArray(sourceValue.getAsJsonArray(), targetValue.getAsJsonArray(), childNode));
            } else {
                target.add(key, sourceValue);
            }
        }
    }

    public static JsonArray mergeArray(JsonArray sourceArray, JsonArray targetArray, IndexNode indexNode) {
        JsonArray newArray = targetArray.deepCopy();

        for (JsonElement sourceElement : sourceArray) {
            if (!sourceElement.isJsonObject()) {
                newArray.add(sourceElement);
                continue; //Not a valid array patch element
            }

            JsonObject sourceObject = sourceElement.getAsJsonObject();

            JsonElement indexElement = sourceObject.get("_index");

            int[] indexes = getIndexes(indexElement);

            String op = sourceObject.has("_op")
                    ? sourceObject.get("_op").getAsString()
                    : "merge";

            for (int index : indexes) {
                switch (op) {
                    case "add" -> newArray = handleAddOperation(sourceObject, newArray, index, indexNode);
                    case "remove" -> handleRemoveOperation(newArray, index, indexNode);
                    case "replace" -> handleReplaceOperation(sourceObject, newArray, index, indexNode);
                    case "merge" -> handleMergeOperation(sourceObject, newArray, index, indexNode);
                    default ->
                        //Unknown operation
                        HytalorPlugin.get().getLogger()
                                .at(Level.SEVERE)
                                .log("Unknown array merge operation: " + op + "\n when merging array: \n" + sourceArray + "\n into: " + targetArray);
                }
            }
        }

        return newArray;
    }

    private static JsonArray handleAddOperation(JsonObject sourceObject, JsonArray targetArray, int _index, IndexNode indexNode) {
        JsonArray newArray = targetArray.deepCopy();

        int index = indexNode.getMappedIndex(_index, true);

        JsonElement newElement = getCleanedObject(sourceObject);

        if (index >= 0) {
            if (index < newArray.size()) {
                newArray = insertAt(newArray, index, newElement);
            } else { //Index out of bounds, add to the end
                newArray.add(newElement);
            }

            //Update index mapping
            indexNode.incrementAbove(_index);
        } else { //No index specified, add to the end
            newArray.add(newElement);
        }

        return newArray;
    }

    private static void handleRemoveOperation(JsonArray targetArray, int _index, IndexNode indexNode) {
        int index = indexNode.getMappedIndex(_index, false);

        if (index < 0 || index >= targetArray.size())
            return;

        targetArray.remove(index);

        //Update index mapping
        indexNode.decrementAbove(_index);
        indexNode.invalidateIndex(_index);

    }

    private static void handleReplaceOperation(JsonObject sourceObject, JsonArray targetArray, int _index, IndexNode indexNode) {
        int index = indexNode.getMappedIndex(_index, false);

        if (index < 0 || index >= targetArray.size())
            return;

        JsonElement newElement = getCleanedObject(sourceObject);
        targetArray.set(index, newElement);

    }

    private static void handleMergeOperation(JsonObject sourceObject, JsonArray targetArray, int _index, IndexNode indexNode) {
        int index = indexNode.getMappedIndex(_index, false);

        if (index < 0 || index >= targetArray.size())
            return;

        JsonElement targetElement = targetArray.get(index);
        if (!targetElement.isJsonObject())
            return;

        JsonElement newElement = getCleanedObject(sourceObject);
        if (!newElement.isJsonObject()) {
            handleReplaceOperation(sourceObject, targetArray, index, indexNode);
            return;
        }

        IndexNode childNode = indexNode.getOrCreateChild(String.valueOf(_index), targetElement);

        deepMerge(newElement.getAsJsonObject(), targetElement.getAsJsonObject(), childNode);

    }

    private static JsonElement getCleanedObject(JsonObject sourceObject) {
        if (sourceObject.get("_value") != null) {
            return sourceObject.get("_value");
        }

        JsonObject newElement = sourceObject.deepCopy();
        newElement.remove("_index");
        newElement.remove("_op");
        newElement.remove("_value");
        return newElement;
    }

    private static JsonArray insertAt(JsonArray array, int index, JsonElement element) {
        JsonArray newArray = new JsonArray();
        for (int i = 0; i < array.size(); i++) {
            if (i == index) {
                newArray.add(element);
            }
            newArray.add(array.get(i));
        }
        if (index >= array.size()) {
            newArray.add(element);
        }

        return newArray;
    }

    private static int[] getIndexes(JsonElement indexElement) {
        int[] indexes = new int[] { -1 }; //Default to -1 (no index specified)

        if (indexElement != null) {
            if (indexElement.isJsonArray()) {
                indexes = new int[indexElement.getAsJsonArray().size()];
                for (int i = 0; i < indexElement.getAsJsonArray().size(); i++) {
                    indexes[i] = indexElement.getAsJsonArray().get(i).getAsInt();
                }
            } else {
                indexes = new int[] { indexElement.getAsInt() };
            }
        }
        return indexes;
    }
}
