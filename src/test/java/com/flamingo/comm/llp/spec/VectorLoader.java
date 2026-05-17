package com.flamingo.comm.llp.spec;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public final class VectorLoader {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final boolean INCLUDE_SLOW =
            Boolean.parseBoolean(System.getProperty("llp.test.includeSlow", "false"));

    private VectorLoader() {
    }

    public static List<TestVector> loadAll(String resourcePath) throws IOException {
        return loadAll(resourcePath, false);
    }

    public static List<TestVector> loadAll(String resourcePath, boolean includeSlow) throws IOException {
        List<TestVector> result = new ArrayList<>();
        URL url = Thread.currentThread().getContextClassLoader().getResource(resourcePath);
        if (url == null) {
            throw new IOException("Resource not found on classpath: " + resourcePath);
        }
        Path root;
        try {
            root = Path.of(url.toURI());
        } catch (URISyntaxException e) {
            throw new IOException("Invalid resource URI: " + url, e);
        }
        if (!Files.isDirectory(root)) {
            throw new IOException("Not a directory: " + root);
        }
        try (Stream<Path> walk = Files.walk(root)) {
            List<Path> jsonFiles = walk
                    .filter(p -> p.toString().endsWith(".json"))
                    .toList();
            for (Path file : jsonFiles) {
                loadGroupedFile(file, result, includeSlow);
            }
        }
        return result;
    }

    public static List<TestVector> loadByType(String resourcePath, String type) throws IOException {
        return loadByType(resourcePath, type, false);
    }

    public static List<TestVector> loadByType(String resourcePath, String type, boolean includeSlow) throws IOException {
        return loadAll(resourcePath, includeSlow).stream()
                .filter(v -> v.type().equals(type))
                .toList();
    }

    public static List<TestVector> loadByNames(String resourcePath, String... filenames) throws IOException {
        return loadByNames(resourcePath, false, filenames);
    }

    public static List<TestVector> loadByNames(String resourcePath, boolean includeSlow, String... filenames) throws IOException {
        List<TestVector> result = new ArrayList<>();
        String prefix = resourcePath.endsWith("/") ? resourcePath : resourcePath + "/";
        for (String fn : filenames) {
            URL url = Thread.currentThread().getContextClassLoader().getResource(prefix + fn);
            if (url == null) continue;
            try {
                Path path = Path.of(url.toURI());
                loadGroupedFile(path, result, includeSlow);
            } catch (URISyntaxException ignored) {
            }
        }
        return result;
    }

    public static int countSlowVectors(String resourcePath) throws IOException {
        List<TestVector> all = loadAll(resourcePath, true);
        int count = 0;
        for (TestVector v : all) {
            if (v.flags().contains("Slow")) count++;
        }
        return count;
    }

    private static void loadGroupedFile(Path file, List<TestVector> out, boolean includeSlow) throws IOException {
        boolean effectiveIncludeSlow = includeSlow || INCLUDE_SLOW;
        JsonNode rootNode = MAPPER.readTree(file.toFile());
        String specVersion = rootNode.get("spec_version").asString();
        JsonNode vectors = rootNode.get("vectors");
        if (vectors == null || !vectors.isArray()) return;
        for (JsonNode vec : vectors) {
            if (vec.get("type") == null) continue;

            JsonNode flagsNode = vec.get("flags");
            boolean hasSlow = false;
            List<String> flags = new ArrayList<>();
            if (flagsNode != null && flagsNode.isArray()) {
                for (JsonNode flag : flagsNode) {
                    String flagVal = flag.asString();
                    flags.add(flagVal);
                    if ("Slow".equals(flagVal)) hasSlow = true;
                }
            }

            if (!effectiveIncludeSlow && hasSlow) continue;

            JsonNode expected = vec.get("expected");
            out.add(new TestVector(
                    specVersion,
                    vec.get("type").asString(),
                    vec.get("name").asString(),
                    vec.get("description").asString(),
                    vec.get("input"),
                    expected,
                    flags
            ));
        }
    }
}
