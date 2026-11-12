package com.codeguard.analysis;

import java.util.Set;

public record ChangedFile(String path, String status, String patch, Set<Integer> addedLines, boolean truncated) {
    public ChangedFile { addedLines = Set.copyOf(addedLines); }
}
