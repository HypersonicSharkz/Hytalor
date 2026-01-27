package com.hypersonicsharkz.util;

import com.google.gson.JsonObject;
import com.hypersonicsharkz.HytalorPlugin;
import com.hypixel.hytale.logger.backend.HytaleLogManager;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.net.URISyntaxException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class JSONUtilTest {

    @Test
    void simpleKeyValuePatch() throws URISyntaxException {
        TestFiles testFiles = new TestFiles("simpleKeyValuePatch");
        JsonObject patch = testFiles.patch;
        JsonObject source = testFiles.source;
        JsonObject expected = testFiles.expected;

        JSONUtil.deepMerge(patch, source);

        assertEquals(expected, source);
    }

    @Test
    void nestedObjectPatch() throws URISyntaxException {
        TestFiles testFiles = new TestFiles("nestedObjectPatch");
        JsonObject patch = testFiles.patch;
        JsonObject source = testFiles.source;
        JsonObject expected = testFiles.expected;

        JSONUtil.deepMerge(patch, source);

        assertEquals(expected, source);
    }

    @Test
    void arrayAdditionPatch() throws URISyntaxException {
        TestFiles testFiles = new TestFiles("addingToArrayPatch");
        JsonObject patch = testFiles.patch;
        JsonObject source = testFiles.source;
        JsonObject expected = testFiles.expected;

        JSONUtil.deepMerge(patch, source);

        assertEquals(expected, source);
    }

    @Test
    void nestedArrayPatch() throws URISyntaxException {
        TestFiles testFiles = new TestFiles("nestedArrayPatch");
        JsonObject patch = testFiles.patch;
        JsonObject source = testFiles.source;
        JsonObject expected = testFiles.expected;

        JSONUtil.deepMerge(patch, source);

        assertEquals(expected, source);

        TestFiles testFiles2 = new TestFiles("nestedArrayPatchPart2");
        JsonObject patch2 = testFiles2.patch;
        JsonObject expected2 = testFiles2.expected;

        JSONUtil.deepMerge(patch2, source);

        assertEquals(expected2, source);
    }

    @Test
    void queryValuePatch() throws URISyntaxException {
        TestFiles testFiles = new TestFiles("queryValuePatch");
        JsonObject patch = testFiles.patch;
        JsonObject source = testFiles.source;
        JsonObject expected = testFiles.expected;

        JSONUtil.deepMerge(patch, source);

        assertEquals(expected, source);
    }

    @Test
    void upsertArrayPatch() throws URISyntaxException {
        TestFiles testFiles = new TestFiles("upsertArrayPatch");
        JsonObject patch = testFiles.patch;
        JsonObject source = testFiles.source;
        JsonObject expected = testFiles.expected;

        JSONUtil.deepMerge(patch, source);

        assertEquals(expected, source);
    }

    private static class TestFiles {
        JsonObject patch;
        JsonObject source;
        JsonObject expected;

        public TestFiles(String testName) throws URISyntaxException {
            patch = JSONUtil.readJSON(Path.of(getClass().getResource("/" + testName + "/patch.json").toURI()));
            source = JSONUtil.readJSON(Path.of(getClass().getResource("/" + testName + "/source.json").toURI()));
            expected = JSONUtil.readJSON(Path.of(getClass().getResource("/" + testName + "/expected.json").toURI()));
        }
    }
}