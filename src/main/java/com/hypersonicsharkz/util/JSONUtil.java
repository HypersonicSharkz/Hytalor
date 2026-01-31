package com.hypersonicsharkz.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import com.hypersonicsharkz.HytalorPlugin;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;
import com.jayway.jsonpath.PathNotFoundException;
import com.jayway.jsonpath.spi.json.GsonJsonProvider;

import java.io.BufferedReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.logging.Level;

public class JSONUtil {
    private static final GsonJsonProvider provider = new GsonJsonProvider();
    private static final Configuration conf = Configuration
            .builder()
            .options(Option.ALWAYS_RETURN_LIST, Option.AS_PATH_LIST)
            .jsonProvider(provider)
            .build();

    public static JsonObject readJSON(Path path) {
        try (
                BufferedReader fileReader = Files.newBufferedReader(path);
                JsonReader reader = new JsonReader(fileReader);
        ) {
            return JsonParser.parseReader(reader).getAsJsonObject();

        } catch (Exception e) {
            HytalorPlugin.get().getLogger()
                    .at(Level.WARNING)
                    .log("      Failed to read JSON file at path: " + path, e);
            return null;
        }
    }

    public static void deepMerge(JsonObject source, JsonObject target) {
        for (String key: source.keySet()) {
            if (key.equals("BaseAssetPath") || key.equals("_priority"))
                continue;

            JsonElement sourceValue = source.get(key);

            if (isQuery(key)) {
                resolveQuery(key, sourceValue, target);
                continue;
            }

            if (!target.has(key)) {
                if (isPatch(sourceValue)) {
                    HytalorPlugin.get().getLogger().at(Level.WARNING)
                            .log("      Target Asset does not contain key: '" + key + "', But it was expected by the Patch");
                    continue;
                }

                addNewField(target, key, sourceValue);
                continue;
            }

            JsonElement targetValue = target.get(key);

            // existing value for "key" - recursively deep merge:
            if (sourceValue.isJsonObject() && targetValue.isJsonObject()) {
                if (isRemoveOperation(sourceValue.getAsJsonObject())) {
                    target.remove(key);
                    return;
                }

                deepMerge(sourceValue.getAsJsonObject(), targetValue.getAsJsonObject());
            } else if (sourceValue.isJsonArray() && targetValue.isJsonArray()) {
                target.add(key, mergeArray(sourceValue.getAsJsonArray(), targetValue.getAsJsonArray()));
            } else {
                target.add(key, sourceValue);
            }
        }
    }

    private static boolean isRemoveOperation(JsonObject patch) {
        JsonElement op = patch.get("_op");
        if (op != null && op.isJsonPrimitive() && op.getAsJsonPrimitive().isString()) {
            String opString = op.getAsString();
            if (opString.equals("remove")) {
                return true;
            } else {
                HytalorPlugin.get().getLogger().at(Level.WARNING)
                        .log("      Operation " + opString + " is not valid for a Json Object patch");
            }
        }
        return false;
    }

    private static void addNewField(JsonObject target, String key, JsonElement value) {
        if (value.isJsonArray() && isArrayPatch(value.getAsJsonArray())) {
            JsonArray newArray = mergeArray(value.getAsJsonArray(), new JsonArray());
            target.add(key, newArray);
            return;
        }

        target.add(key, value);
    }

    public static JsonArray mergeArray(JsonArray sourceArray, JsonArray targetArray) {
        JsonArray newArray = targetArray.deepCopy();

        for (JsonElement sourceElement : sourceArray) {
            if (!sourceElement.isJsonObject()) {
                newArray.add(sourceElement);
                continue; //Not a valid array patch element
            }

            JsonObject sourceObject = sourceElement.getAsJsonObject();

            int[] indexes = resolveIndex(sourceObject, newArray);

            String op = sourceObject.has("_op")
                    ? sourceObject.get("_op").getAsString()
                    : "merge";

            for (int index : indexes) {
                switch (op) {
                    case "add" -> newArray = handleAddOperation(sourceObject, newArray, index);
                    case "addBefore" -> newArray = handleAddOperation(sourceObject, newArray, index == -1 ? 0 : index);
                    case "addAfter" -> newArray = handleAddOperation(sourceObject, newArray, index == -1 ? -1 : index + 1);
                    case "remove" -> handleRemoveOperation(newArray, index);
                    case "replace" -> handleReplaceOperation(sourceObject, newArray, index);
                    case "merge" -> handleMergeOperation(sourceObject, newArray, index);
                    case "upsert" -> newArray = handleUpsertOperation(sourceObject, newArray, index);
                    default ->
                        //Unknown operation
                        HytalorPlugin.get().getLogger()
                                .at(Level.WARNING)
                                .log("      Unknown array merge operation: " + op);
                }
            }
        }

        return newArray;
    }

    private static JsonArray handleUpsertOperation(JsonObject sourceObject, JsonArray targetArray, int index) {
        if (index >= 0 && index < targetArray.size()) { //Found index, perform merge
            JsonObject targetElement = targetArray.get(index).getAsJsonObject();
            JsonObject cleanedSource = getCleanedObject(sourceObject).getAsJsonObject();
            deepMerge(cleanedSource, targetElement);

            return targetArray;
        } else { //Index not found, perform add
            int indexToInsert = getUpsertIndex(sourceObject);
            return handleAddOperation(sourceObject, targetArray, indexToInsert);
        }
    }

    private static JsonArray handleAddOperation(JsonObject sourceObject, JsonArray targetArray, int index) {
        JsonArray newArray = targetArray.deepCopy();

        JsonElement newElement = getCleanedObject(sourceObject);

        if (index >= 0) {
            if (index < newArray.size()) {
                newArray = insertAt(newArray, index, newElement);
            } else { //Index out of bounds, add to the end
                newArray.add(newElement);
            }
        } else { //No index specified, add to the end
            newArray.add(newElement);
        }

        return newArray;
    }

    private static void handleRemoveOperation(JsonArray targetArray, int index) {
        if (index < 0 || index >= targetArray.size())
            return;

        targetArray.remove(index);
    }

    private static void handleReplaceOperation(JsonObject sourceObject, JsonArray targetArray, int index) {
        if (index < 0 || index >= targetArray.size())
            return;

        JsonElement newElement = getCleanedObject(sourceObject);

        targetArray.set(index, newElement);
    }

    private static void handleMergeOperation(JsonObject sourceObject, JsonArray targetArray, int index) {
        if (index < 0 || index >= targetArray.size())
            return;

        JsonElement targetElement = targetArray.get(index);
        if (!targetElement.isJsonObject())
            return;

        JsonElement newElement = getCleanedObject(sourceObject);
        if (!newElement.isJsonObject()) {
            handleReplaceOperation(sourceObject, targetArray, index);
            return;
        }

        deepMerge(newElement.getAsJsonObject(), targetElement.getAsJsonObject());
    }

    private static JsonElement getCleanedObject(JsonObject sourceObject) {
        if (sourceObject.get("_value") != null) {
            return sourceObject.get("_value");
        }

        JsonObject newElement = sourceObject.deepCopy();
        newElement.remove("_index");
        newElement.remove("_op");
        newElement.remove("_value");
        newElement.remove("_find");
        newElement.remove("_findAll");
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

    private static int[] resolveIndex(JsonObject sourceObject, JsonArray targetArray) {
        boolean findFirst = sourceObject.has("_find");
        boolean findAll = sourceObject.has("_findAll");

        if (findFirst && findAll) {
            HytalorPlugin.get().getLogger()
                    .at(Level.WARNING)
                    .log("      Array merge object cannot have both _find and _findAll properties:\n" + sourceObject);
            return new int[]{-1};
        }

        if (findFirst || findAll) {
            return resolveFind(sourceObject, targetArray);
        }

        if (sourceObject.has("_index")) {
            return getIndexes(sourceObject.get("_index"));
        }

        return new int[]{-1};
    }

    private static int[] resolveFind(JsonObject sourceObject, JsonArray targetArray) {
        boolean findFirst = sourceObject.has("_find");

        JsonElement findElement = sourceObject.get(findFirst ? "_find" : "_findAll");

        if (findElement.isJsonObject()) {
            JsonArray indexesArray = new JsonArray();
            for (int i = 0; i < targetArray.size(); i++) {
                JsonElement candidateElement = targetArray.get(i);
                if (matchesFindObject(findElement.getAsJsonObject(), candidateElement)) {
                    indexesArray.add(i);

                    if (findFirst)
                        break;
                }
            }
            return getIndexes(indexesArray);
        }

        if (isQuery(findElement.getAsString())) {
            return queryIndexes(findElement, targetArray, findFirst);
        }

        return new int[]{-1};
    }

    private static boolean matchesFindObject(JsonObject findObject, JsonElement candidateElement) {
        if (!candidateElement.isJsonObject())
            return false;

        JsonObject candidateObject = candidateElement.getAsJsonObject();

        for (String key : findObject.keySet()) {
            if (!candidateObject.has(key))
                return false;

            JsonElement findValue = findObject.get(key);
            JsonElement candidateValue = candidateObject.get(key);

            if (!findValue.equals(candidateValue))
                return false;
        }

        return true;
    }

    private static void resolveQuery(String query, JsonElement value, JsonObject targetObject) {
        try {
            var pathsDoc = JsonPath.using(conf).parse(targetObject);
            var objectDoc = JsonPath.using(provider).parse(targetObject);

            JsonArray matches = pathsDoc.read(query);
            if (matches.isEmpty()) {
                throw new PathNotFoundException();
            }

            for (JsonElement match : matches) {
                String jsonPath = match.getAsString();

                if (value.isJsonObject()) {
                    if (isRemoveOperation(value.getAsJsonObject())) {
                        objectDoc.delete(jsonPath);
                        return;
                    }

                    JsonObject matchObject = objectDoc.read(jsonPath);
                    deepMerge(value.getAsJsonObject(), matchObject);
                    objectDoc.set(jsonPath, matchObject);
                    return;
                }

                if (value.isJsonArray() && isArrayPatch(value.getAsJsonArray())) {
                    JsonArray matchArray = objectDoc.read(jsonPath);
                    matchArray = mergeArray(value.getAsJsonArray(), matchArray);
                    objectDoc.set(jsonPath, matchArray);
                    return;
                }

                objectDoc.set(query, value).jsonString();
            }
        } catch (PathNotFoundException e) {
            HytalorPlugin.get().getLogger().at(Level.WARNING).log(
                    "       Query did not match any elements: " + query
            );
        }
    }

    private static boolean isQuery(String key) {
        return key.startsWith("$") && !key.equals("$Comment");
    }

    private static int[] queryIndexes(JsonElement findElement, JsonArray targetArray, boolean firstOnly) {
        JsonArray indexesArray = new JsonArray();

        for (int i = 0; i < targetArray.size(); i++) {
            JsonElement candidateElement = targetArray.get(i);

            try {
                JsonArray queryResults = JsonPath.using(provider).parse(candidateElement).read(findElement.getAsString());
                if (queryResults == null || queryResults.isEmpty())
                    continue;

            } catch (PathNotFoundException e) {
                continue;
            }

            indexesArray.add(i);
            if (firstOnly) {
                return getIndexes(indexesArray);
            }
        }

        return getIndexes(indexesArray);
    }

    private static int[] getIndexes(JsonElement indexElement) {
        int[] indexes = new int[] { -1 }; //Default to -1 (no index specified)

        if (indexElement != null) {
            if (indexElement.isJsonArray() && !indexElement.getAsJsonArray().isEmpty()) {
                indexes = new int[indexElement.getAsJsonArray().size()];
                for (int i = 0; i < indexElement.getAsJsonArray().size(); i++) {
                    indexes[i] = indexElement.getAsJsonArray().get(i).getAsInt();
                }
            } else if (indexElement.isJsonPrimitive() && indexElement.getAsJsonPrimitive().isNumber()) {
                indexes = new int[] { indexElement.getAsInt() };
            }
        }
        return indexes;
    }

    private static int getUpsertIndex(JsonObject sourceElement) {
        JsonElement indexElement = sourceElement.get("_index");

        if (indexElement != null && indexElement.isJsonPrimitive()) {
            return indexElement.getAsInt();
        }

        return -1; //Default to -1 (no index specified)
    }

    private static boolean isArrayPatch(JsonArray array) {
        if (array.isEmpty())
            return false;

        JsonElement firstElement = array.get(0);
        if (!firstElement.isJsonObject())
            return false;

        return isObjectAPatch(firstElement.getAsJsonObject());
    }

    private static boolean isObjectAPatch(JsonObject jsonObject) {
        return jsonObject.getAsJsonObject().has("_op") ||
                jsonObject.getAsJsonObject().has("_index") ||
                jsonObject.getAsJsonObject().has("_find") ||
                jsonObject.getAsJsonObject().has("_findAll");
    }

    private static boolean isPatch(JsonElement jsonElement) {
        if (!jsonElement.isJsonObject())
            return false;

        JsonObject jsonObject = jsonElement.getAsJsonObject();

        if (isObjectAPatch(jsonObject))
            return true;

        for (Map.Entry<String, JsonElement> element : jsonObject.entrySet()) {
            if (element.getValue().isJsonObject())
                return isObjectAPatch(element.getValue().getAsJsonObject());

            if (element.getValue().isJsonArray())
                return isArrayPatch(element.getValue().getAsJsonArray());
        }

        return false;
    }
}
