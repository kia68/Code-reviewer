package de.ude.codereviewer.analysis.ast;

import com.github.javaparser.ast.CompilationUnit;

public record ParsedCompilationUnit(String relativePath, CompilationUnit unit) { }
