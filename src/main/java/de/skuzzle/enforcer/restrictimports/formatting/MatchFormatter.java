package de.skuzzle.enforcer.restrictimports.formatting;

import java.nio.file.Path;
import java.util.Collection;

import de.skuzzle.enforcer.restrictimports.analyze.AnalyzeResult;

public interface MatchFormatter {

    public static MatchFormatter getInstance() {
        return MatchFormatterImpl.INSTANCE;
    }

    String formatMatches(Collection<Path> roots, AnalyzeResult analyzeResult);
}
