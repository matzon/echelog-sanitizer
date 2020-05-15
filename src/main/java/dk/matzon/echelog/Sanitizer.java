package dk.matzon.echelog;

import com.google.common.base.Stopwatch;

import java.io.BufferedWriter;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static java.nio.file.StandardOpenOption.*;

public class Sanitizer {
    private Path inputPath = null;
    private Path outputPath = null;
    private FileSystem outputFileSystem = null;
    private int fileCount = 0;

    public Sanitizer(String[] args) throws IOException {
        inputPath = Paths.get(args[0]);
        outputPath = Paths.get(args[1]);
    }

    private void sanitize() throws IOException {

        // prepare
        if (Files.notExists(outputPath)) {
            Files.createDirectories(outputPath);
        }

        URI destinationFile = URI.create("jar:file:" + outputPath.toUri().getPath() + "sanitized.zip");
        System.out.println("Created destination file: " + destinationFile);
        outputFileSystem = FileSystems.newFileSystem(destinationFile, Collections.singletonMap("create", "true"));

        Stopwatch sw = Stopwatch.createStarted();
        if (Files.isDirectory(inputPath)) {
            Files.walk(inputPath).forEach(this::processFile);
        } else {
            processFile(inputPath);
        }
        outputFileSystem.close();
        System.out.println("Finished sanitation of " + fileCount + " files in " + sw.stop());
    }

    private void processFile(Path path) {
        if (Files.isDirectory(path)) {
            System.out.println("Entering directory " + path);
            return;
        }

        fileCount++;
        Stopwatch sw = Stopwatch.createStarted();
//        System.out.print("Processing file " + path.getFileName() + "... ");
        try {
            List<String> sanitizedStrings = Files.readAllLines(path, StandardCharsets.ISO_8859_1)
                    .parallelStream()
                    .map(this::sanitizeLine)
                    .collect(Collectors.toList());
//            System.out.println("kept " + sanitizedStrings.size() + " lines. " + sw.stop());
            writeSanitizedFile(sanitizedStrings, path);
        } catch (IOException _e) {
            System.out.println("Exception occurred while processing file: " + _e.getMessage());
        }
    }

    private void writeSanitizedFile(List<String> sanitizedStrings, Path path) throws IOException {
        Path outputFileSystemPath = outputFileSystem.getPath(path.getParent().getFileName().toString());
        Files.createDirectories(outputFileSystemPath);

        Path outputFilePath = outputFileSystemPath.resolve(path.getFileName().toString());
//        System.out.println("Saving sanitized file to " + outputFilePath);
        BufferedWriter bufferedWriter = Files.newBufferedWriter(outputFilePath, StandardCharsets.UTF_8, WRITE, CREATE, TRUNCATE_EXISTING);
        for (String sanitizedString : sanitizedStrings) {
            bufferedWriter.write(sanitizedString);
            bufferedWriter.newLine();
        }
        bufferedWriter.close();
    }

    private String sanitizeLine(String _line) {
        if (_line.contains("]  *** ")) {
            if (_line.contains("has quit") || _line.contains("has joined") || _line.contains("has left") || _line.contains("invited")) {
                int startToken = _line.indexOf("<");
                if (startToken != -1) {
                    int endToken = _line.indexOf(">", startToken) + 2;
                    return _line.substring(0, _line.indexOf("<")) + _line.substring(endToken);
                }
            } else if (_line.contains("is now known as") || _line.contains("changes topic") || _line.contains("was kicked")) {
                // harmless
            } else if (_line.contains("sets mode")) {
                if (_line.contains("*!")) {
                    int startToken = _line.indexOf("mode: ");
                    int endToken = _line.indexOf("*!", startToken + 6);
                    return _line.substring(0, startToken + 6) + _line.substring(startToken + 6, endToken).trim();
                }
            } else {
                System.out.println("Hmm: " + _line);
            }
        }
        return _line;
    }

    public static void main(String[] args) throws IOException {
        new Sanitizer(args).sanitize();
    }
}
