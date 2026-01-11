package app.model;

import java.nio.file.Path;
import java.util.Set;

public record Task(Set<String> fields, Path file) {
}
