package de.ude.codereviewer.analysis.ast;

public record ParsedFileInfo(
        String relativePath, boolean success, int typeCount, int methodCount, String errorMessage) { }
