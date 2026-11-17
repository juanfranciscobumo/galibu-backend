package com.galibu.backend.monitor;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.xml.parsers.DocumentBuilderFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * Lee los resultados de los tests de backend (JUnit XML) generados en el build
 * y los expone como un resumen para el monitor. La ruta es configurable para
 * poder apuntar al directorio de resultados en desarrollo o en el contenedor.
 */
@Service
public class TestReportService {

    private final String resultsDir;

    public TestReportService(
        @Value("${app.monitor.test-results-dir:/app/test-results}") String resultsDir
    ) {
        this.resultsDir = resultsDir;
    }

    public Map<String, Object> summary() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("resultsDir", resultsDir);
        Path dir = Paths.get(resultsDir);
        if (!Files.isDirectory(dir)) {
            out.put("available", false);
            out.put("message", "No se encontró el directorio de resultados de tests (" + resultsDir + ").");
            out.put("tests", 0);
            out.put("failures", 0);
            out.put("errors", 0);
            out.put("skipped", 0);
            out.put("classes", List.of());
            return out;
        }

        int totalTests = 0;
        int totalFailures = 0;
        int totalErrors = 0;
        int totalSkipped = 0;
        double totalTime = 0.0;
        List<Map<String, Object>> classes = new ArrayList<>();

        List<File> xmlFiles = new ArrayList<>();
        try {
            Files.walk(dir)
                .filter(Files::isRegularFile)
                .filter(p -> p.getFileName().toString().endsWith(".xml"))
                .forEach(p -> xmlFiles.add(p.toFile()));
        } catch (Exception ignored) {
        }
        for (File file : xmlFiles) {
            try {
                Document doc = DocumentBuilderFactory.newInstance()
                    .newDocumentBuilder().parse(file);
                Element root = doc.getDocumentElement();
                // JUnit puede tener <testsuite> como raíz o <testsuites>.
                List<Element> suites = new ArrayList<>();
                if ("testsuite".equals(root.getTagName())) {
                    suites.add(root);
                } else {
                    NodeList nodes = root.getElementsByTagName("testsuite");
                    for (int i = 0; i < nodes.getLength(); i++) {
                        suites.add((Element) nodes.item(i));
                    }
                }
                for (Element suite : suites) {
                    int tests = intAttr(suite, "tests");
                    int failures = intAttr(suite, "failures");
                    int errors = intAttr(suite, "errors");
                    int skipped = intAttr(suite, "skipped");
                    double time = doubleAttr(suite, "time");
                    String name = suite.getAttribute("name");
                    if (name == null || name.isBlank()) {
                        name = file.getName();
                    }
                    totalTests += tests;
                    totalFailures += failures;
                    totalErrors += errors;
                    totalSkipped += skipped;
                    totalTime += time;

                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("name", name);
                    item.put("file", file.getName());
                    item.put("tests", tests);
                    item.put("failures", failures);
                    item.put("errors", errors);
                    item.put("skipped", skipped);
                    item.put("time", Math.round(time * 1000.0) / 1000.0);
                    item.put("status", (failures + errors) > 0 ? "FAILED" : "PASSED");
                    classes.add(item);
                }
            } catch (Exception ignored) {
                // Archivo no parseable: se omite.
            }
        }

        classes.sort(Comparator.comparing(m -> String.valueOf(m.get("name"))));

        out.put("available", true);
        out.put("files", xmlFiles.size());
        out.put("tests", totalTests);
        out.put("failures", totalFailures);
        out.put("errors", totalErrors);
        out.put("skipped", totalSkipped);
        out.put("passed", Math.max(0, totalTests - totalFailures - totalErrors - totalSkipped));
        out.put("time", Math.round(totalTime * 1000.0) / 1000.0);
        out.put("status", (totalFailures + totalErrors) > 0 ? "FAILED" : "PASSED");
        out.put("classes", classes);
        return out;
    }

    private static int intAttr(Element element, String name) {
        try {
            return Integer.parseInt(element.getAttribute(name));
        } catch (Exception ex) {
            return 0;
        }
    }

    private static double doubleAttr(Element element, String name) {
        try {
            return Double.parseDouble(element.getAttribute(name));
        } catch (Exception ex) {
            return 0.0;
        }
    }
}
