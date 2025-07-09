/**
 * 
 */
package de.jcup.asciidoctor.converter.pdf.latex.preprocessor;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Before an adoc is converted to pdf, go through each adoc and its included
 * contents and replace all latex with svgs
 */
public class LatexPDFPreprocessor {
    private static final String INCLUDE_PATTERN = "include::([^\\[]+?)\\[(.*?)\\]";
    private static final String MATH_PATTERN = "stem:\\[([^\\]]+)\\]";
    private static final String BLOCK_MATH_PATTERN = "\\[stem]\\s*\\+{4}\\s*([\\s\\S]*?)\\s*\\+{4}";

    private static final Pattern COMBINED = Pattern.compile(INCLUDE_PATTERN + 
            "|" + MATH_PATTERN + 
            "|" + BLOCK_MATH_PATTERN, Pattern.MULTILINE);

    private static final String TEMP_IMAGE_DIR = "tempImageDir";

    private static Path imageDir;
    private Path docDir;
    private String asciiDocFullFilePath; // adoc file to be processed
    private File nodeEXE;

    public LatexPDFPreprocessor(String file, Path docDir) {
        this.asciiDocFullFilePath = file;
        this.docDir = docDir;
        this.nodeEXE = NodeBinaryExtractor.getNodeExecutable();
    }

    public Path run() throws IOException, InterruptedException {
        Path resultFile = null;
        try {
            Path sourceFile = Paths.get(asciiDocFullFilePath);
            Path tempDir = Files.createTempDirectory(sourceFile.getParent(), "mathsvg_");

            String rootFile = processFile(sourceFile, tempDir, new HashSet<>(), new HashMap<>(), true);
            resultFile = Paths.get(rootFile);
        } 
        catch (Exception e) {
            throw new RuntimeException(e);
        }

        return resultFile;
    }

    /**
     * Recursively process each adoc file, generate SVG for each latex, and replace
     * include and stem statements with calls to their processed files
     * 
     * @param sourceFile
     * @param tempDir
     * @param visited
     * @param inheritedAttrs
     * @param isRoot
     * @return
     * @throws IOException
     * @throws InterruptedException
     */
    private String processFile(Path sourceFile, Path tempDir, Set<Path> visited, Map<String, String> inheritedAttrs,
            boolean isRoot) throws IOException, InterruptedException {
        Path outputFile = tempDir.resolve(sourceFile.getFileName().toString().replace(".adoc", "_processed.adoc"));

        if (visited.contains(outputFile))
            return outputFile.toString();
        visited.add(outputFile);

        String content = Files.readString(sourceFile);

        // This method is recursive and child files need the info from the root file
        Map<String, String> localAttrs = new HashMap<>(inheritedAttrs);

        if (isRoot) { // Set up image directory
            Path currDir = sourceFile.getParent().toAbsolutePath(); // Actual path to current file being processed

            imageDir = currDir.resolve("Images/Latex");

            File imageDirFile = imageDir.toFile();
            if (!imageDirFile.exists()) {
                imageDirFile.mkdirs();
            }

            String docDirString = docDir.toString().replace("\\", "/");
            localAttrs.putIfAbsent("docdir", docDirString); // built-in {docdir}
        }

        Map<String, String> attributes = parseHeaderPaths(content, localAttrs);
        localAttrs.putAll(attributes);

        String output = matchAndReplaceContent(content, tempDir, localAttrs, visited, isRoot);
        Files.writeString(outputFile, output.toString());

        return outputFile.toString();
    }

    /**
     * Use Regex to parse and replace file contents with references to converted
     * latex and include files
     * 
     * @param content
     * @param tempDir
     * @param attributes
     * @param visited
     * @param isRoot
     * @return
     * @throws IOException
     * @throws InterruptedException
     */
    private String matchAndReplaceContent(String content, Path tempDir, Map<String, String> attributes,
            Set<Path> visited, boolean isRoot) throws IOException, InterruptedException {
        StringBuffer output = new StringBuffer();

        if (isRoot) {
            String relativeImageDir = imageDir.toString();
            relativeImageDir = relativeImageDir.replace("\\", "/");
            String docDir = attributes.get("docdir");

            if (!docDir.isBlank()) {
                relativeImageDir = relativeImageDir.replace(docDir, "{docdir}");
            }
            output.append(":" + TEMP_IMAGE_DIR + ": " + relativeImageDir + System.lineSeparator());
        }

        java.util.regex.Matcher matcher = COMBINED.matcher(content);
        int lastEnd = 0;

        while (matcher.find()) {
            output.append(content, lastEnd, matcher.start());

            if (matcher.group(1) != null) { // Match includes
                // Ex. include::file.adoc[options]
                String includePathRaw = matcher.group(1).trim();
                String includeOptions = matcher.group(2).trim();
                String resolvedPath = getAbsoluteIncludePaths(includePathRaw, attributes);

                if (!resolvedPath.equals(includePathRaw) && Files.exists(Paths.get(resolvedPath))) {
                    Path included = Paths.get(resolvedPath).normalize();
                    String newFile = processFile(included, tempDir, visited, attributes, false);

                    if (newFile != null && !newFile.isBlank()) {
                        newFile = newFile.toString().replace("\\", "/");

                        output.append("include::").append(newFile).append("[").append(includeOptions).append("]");
                    } else {
                        String pathDisplay = included.toString().replace("\\", "/");
                        output.append("include::").append(pathDisplay).append("[").append(includeOptions).append("]");
                    }
                } else {
                    output.append("include::").append(includePathRaw).append("[").append(includeOptions).append("]");
                }
            } else if (matcher.group(3) != null) { // Match inline latex
                // stem:[latex] text
                String latex = matcher.group(3).trim();

                String svgName = latex.hashCode() + ".svg";
                Path svgFile = imageDir.resolve(svgName);

                if (!Files.exists(svgFile)) {
                    generateSvgFromLatex(latex, svgFile);
                }

                String pathOutput = "{" + TEMP_IMAGE_DIR + "}/" + svgName;

                output.append("image:").append(pathOutput).append("[fit=line]");
            } else if (matcher.group(4) != null) { // Match block latex
                // [stem]++++...++++
                String blockLatex = matcher.group(4).trim();

                String svgName = blockLatex.hashCode() + ".svg";
                Path svgFile = imageDir.resolve(svgName);

                if (!Files.exists(svgFile)) {
                    generateSvgFromLatex(blockLatex, svgFile);
                }

                String pathOutput = "{" + TEMP_IMAGE_DIR + "}/" + svgName;

                output.append("image::").append(pathOutput).append("[pdfwidth=25%, align=center]");
            }

            lastEnd = matcher.end();
        }

        output.append(content.substring(lastEnd));

        return output.toString();
    }

    /**
     * Calls MathJax to convert given latex to an svg file
     * 
     * @param latex
     * @param outputSvg
     * @throws IOException
     * @throws InterruptedException
     */
    private void generateSvgFromLatex(String latex, Path outputSvg) throws IOException, InterruptedException {
        try {
            if (nodeEXE == null)
                throw new RuntimeException("Latex to SVG: Failed to find required resources");

            ProcessBuilder pb = new ProcessBuilder(NodeBinaryExtractor.EXECUTABLE_NAME, NodeBinaryExtractor.SCRIPT_NAME,
                    latex);
            pb.directory(new File(nodeEXE.getParent().toString()));

            pb.redirectOutput(outputSvg.toFile());

            Process process = pb.start();

            String line;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {

                while ((line = reader.readLine()) != null) {
                    System.err.println("Output: " + line);
                }
            }

            try (BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {

                while ((line = errorReader.readLine()) != null) {
                    System.err.println("ERROR: " + line);
                }
            }

            process.waitFor();

            if (process.exitValue() != 0) {
                throw new RuntimeException("Latex conversion failed for: " + latex);
            }

            stripMjxFromSVG(outputSvg);
        } 
        catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Remove HTML nodes generated by MathJax
     * 
     * @param filePath
     * @throws IOException
     */
    private static void stripMjxFromSVG(Path filePath) throws IOException {
        String content = Files.readString(filePath);

        Pattern pattern = Pattern.compile("<mjx-container[^>]*>\\s*(<svg[\\s\\S]*?</svg>)\\s*</mjx-container>");
        java.util.regex.Matcher matcher = pattern.matcher(content);

        if (matcher.find()) {
            String svgOnly = matcher.group(1);
            Files.writeString(filePath, svgOnly);
        }
    }

    /**
     * Get all path attributes, convert them into absolute paths, and add them to
     * attribute dictionary
     * 
     * @param content            file contents
     * @param existingAttributes dictionary of attributes often inherited from
     *                           another file that uses includes
     * @return dictionary of all existing and new attributes
     */
    private static Map<String, String> parseHeaderPaths(String content, Map<String, String> existingAttributes) {
        Map<String, String> attributes = new HashMap<>(existingAttributes);
        Pattern attrPattern = Pattern.compile("^:([^:]+):\\s*(.+)$", Pattern.MULTILINE);
        java.util.regex.Matcher matcher = attrPattern.matcher(content);

        String output;
        while (matcher.find()) {
            String key = matcher.group(1).trim();
            String value = matcher.group(2).trim();
            output = value.replace("\\", "/");

            attributes.put(key, output);
        }

        // Resolve attribute values
        boolean changed;
        do {
            changed = false;
            for (Map.Entry<String, String> entry : attributes.entrySet()) {
                String resolved = getAbsoluteIncludePaths(entry.getValue(), attributes);
                if (!resolved.equals(entry.getValue())) {
                    entry.setValue(resolved);
                    changed = true;
                }
            }
        } while (changed);

        return attributes;
    }

    /**
     * Takes a relative include path and uses the attributes to create an absolute
     * path
     * 
     * @param includeText the include path to be processed
     * @param attributes  dictionary of directory attributes from the header
     * @return absolute path
     */
    private static String getAbsoluteIncludePaths(String includeText, Map<String, String> attributes) {
        Pattern pattern = Pattern.compile("\\{([^{}]+)}");
        java.util.regex.Matcher matcher = pattern.matcher(includeText);
        StringBuffer sb = new StringBuffer();

        while (matcher.find()) {
            String key = matcher.group(1);
            String replacement = attributes.getOrDefault(key, matcher.group(0)); // keep original if unknown
            matcher.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);

        String result = sb.toString();
        result = result.replace("\\", "/");

        return result;
    }

    /**
     * Delete temp folder created to hold generated adocs
     * 
     * @param tempFilePath
     */
    public static void cleanUp(Path tempFilePath) {
        if (tempFilePath != null) {
            Path tempDir = tempFilePath.getParent();
            File directory = tempDir.toFile();

            if (directory.exists()) {
                deleteDir(directory); // Comment this out to prevent deleting of the temp folder (for debugging)
            }
        }
    }

    /**
     * Recursively delete all files and contents in a given directory then delete
     * the directory itself
     * 
     * @param file
     */
    private static void deleteDir(File file) {
        File[] contents = file.listFiles();
        if (contents != null) {
            for (File f : contents) {
                if (!Files.isSymbolicLink(f.toPath())) {
                    deleteDir(f);
                }
            }
        }
        file.delete();
    }
}