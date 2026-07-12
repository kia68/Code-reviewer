package de.ude.codereviewer.analysis.ast;

import java.util.List;

public record AstParseReport(List<ParsedFileInfo> files) {

    public long successCount() {
        return files.stream().filter(ParsedFileInfo::success).count();
    }

    public long failureCount() {
        return files.size() - successCount();
    }
}
