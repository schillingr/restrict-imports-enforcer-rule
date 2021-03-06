package de.skuzzle.enforcer.restrictimports.rule;

import static com.google.common.base.Preconditions.checkArgument;

import java.nio.charset.Charset;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.maven.enforcer.rule.api.EnforcerRule;
import org.apache.maven.enforcer.rule.api.EnforcerRuleException;
import org.apache.maven.enforcer.rule.api.EnforcerRuleHelper;
import org.apache.maven.project.MavenProject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.skuzzle.enforcer.restrictimports.analyze.AnalyzeResult;
import de.skuzzle.enforcer.restrictimports.analyze.AnalyzerSettings;
import de.skuzzle.enforcer.restrictimports.analyze.BannedImportDefinitionException;
import de.skuzzle.enforcer.restrictimports.analyze.BannedImportGroup;
import de.skuzzle.enforcer.restrictimports.analyze.BannedImportGroups;
import de.skuzzle.enforcer.restrictimports.analyze.CommentBufferOverflowException;
import de.skuzzle.enforcer.restrictimports.analyze.RuntimeIOException;
import de.skuzzle.enforcer.restrictimports.analyze.SourceTreeAnalyzer;
import de.skuzzle.enforcer.restrictimports.formatting.MatchFormatter;

/**
 * Enforcer rule which restricts the usage of certain packages or classes within a Java
 * code base.
 */
public class RestrictImports extends BannedImportGroupDefinition implements EnforcerRule {

    private static final Logger LOGGER = LoggerFactory.getLogger(RestrictImports.class);

    private List<BannedImportGroupDefinition> groups = new ArrayList<>();

    private boolean includeTestCode;
    private int commentLineBufferSize = 128;
    private Charset sourceFileCharset;

    @Override
    public void execute(EnforcerRuleHelper helper) throws EnforcerRuleException {
        try {
            final MavenProject project = (MavenProject) helper.evaluate("${project}");

            LOGGER.debug("Checking for banned imports");

            final BannedImportGroups groups = assembleGroups();
            LOGGER.debug("Banned import groups:\n{}", groups);

            final AnalyzerSettings analyzerSettings = createAnalyzerSettingsFromPluginConfiguration(project);
            LOGGER.debug("Analyzer settings:\n{}", analyzerSettings);

            final AnalyzeResult analyzeResult = SourceTreeAnalyzer.getInstance().analyze(analyzerSettings, groups);
            LOGGER.debug("Analyzer result:\n{}", analyzeResult);

            if (analyzeResult.bannedImportsFound()) {
                final String errorMessage = MatchFormatter.getInstance()
                        .formatMatches(analyzerSettings.getRootDirectories(), analyzeResult);
                throw new EnforcerRuleException(errorMessage);
            } else {
                LOGGER.debug("No banned imports found");
            }
        } catch (final RuntimeIOException e) {
            throw new EnforcerRuleException(e.getMessage(), e);
        } catch (final CommentBufferOverflowException e) {
            // thrown by the TransientCommentReader in case the comment buffer is too
            // small
            throw new EnforcerRuleException(String.format(
                    "Error while reading java source file. The comment line buffer is too small. "
                            + "Please set <commentLineBufferSize> to a value greater than %d. %s",
                    commentLineBufferSize, e.getMessage()));
        } catch (final BannedImportDefinitionException e) {
            throw new EnforcerRuleException("RestrictImports rule configuration error: " + e.getMessage(), e);
        } catch (final EnforcerRuleException e) {
            throw e;
        } catch (final Exception e) {
            throw new EnforcerRuleException("Encountered unexpected exception: " + e.getLocalizedMessage(), e);
        }
    }

    private BannedImportGroups assembleGroups() {
        if (!this.groups.isEmpty()) {
            final List<BannedImportGroup> bannedImportGroups = this.groups.stream()
                    .map(BannedImportGroupDefinition::createGroupFromPluginConfiguration)
                    .collect(Collectors.toList());
            return BannedImportGroups.builder()
                    .withGroups(bannedImportGroups)
                    .build();
        }
        final BannedImportGroup singleGroup = createGroupFromPluginConfiguration();
        return BannedImportGroups.builder()
                .withGroup(singleGroup)
                .build();
    }

    private AnalyzerSettings createAnalyzerSettingsFromPluginConfiguration(
            MavenProject mavenProject) {
        final Collection<Path> sourceRoots = listSourceRoots(mavenProject)
                .collect(Collectors.toList());
        final Charset sourceFileCharset = determineSourceFileCharset(mavenProject);

        return AnalyzerSettings.builder()
                .withRootDirectories(sourceRoots)
                .withCommentLineBufferSize(commentLineBufferSize)
                .withSourceFileCharset(sourceFileCharset)
                .build();
    }

    private Charset determineSourceFileCharset(MavenProject mavenProject) {
        if (this.sourceFileCharset != null) {
            return this.sourceFileCharset;
        }
        final String mavenCharsetName = (String) mavenProject.getProperties().get("project.build.sourceEncoding");
        if (mavenCharsetName != null) {
            return Charset.forName(mavenCharsetName);
        }
        return Charset.defaultCharset();
    }

    @SuppressWarnings("unchecked")
    private Stream<Path> listSourceRoots(MavenProject project) {
        final Stream<String> compileStream = project.getCompileSourceRoots().stream();

        final Stream<String> rootFolders;
        if (this.includeTestCode) {
            final Stream<String> testStream = project.getTestCompileSourceRoots().stream();
            rootFolders = Stream.concat(compileStream, testStream);
        } else {
            rootFolders = compileStream;
        }

        return rootFolders
                .peek(root -> LOGGER.debug("Including source dir: {}", root))
                .map(Paths::get);
    }

    private void checkGroups(boolean condition) {
        if (!condition) {
            throw new IllegalArgumentException("Configuration error: you can either define a list of banned import "
                    + "definitions using <groups> OR define a single banned import definition on top level without "
                    + "<groups> but not both");
        }
    }

    @Override
    public void setBasePackage(String basePackage) {
        checkGroups(this.groups.isEmpty());
        super.setBasePackage(basePackage);
    }

    @Override
    public void setBasePackages(List<String> basePackages) {
        checkGroups(this.groups.isEmpty());
        super.setBasePackages(basePackages);
    }

    @Override
    public void setBannedImport(String bannedImport) {
        checkGroups(this.groups.isEmpty());
        super.setBannedImport(bannedImport);
    }

    @Override
    public void setBannedImports(List<String> bannedPackages) {
        checkGroups(this.groups.isEmpty());
        super.setBannedImports(bannedPackages);
    }

    @Override
    public void setAllowedImport(String allowedImport) {
        checkGroups(this.groups.isEmpty());
        super.setAllowedImport(allowedImport);
    }

    @Override
    public void setAllowedImports(List<String> allowedImports) {
        checkGroups(this.groups.isEmpty());
        super.setAllowedImports(allowedImports);
    }

    @Override
    public void setExclusion(String exclusion) {
        checkGroups(this.groups.isEmpty());
        super.setExclusion(exclusion);
    }

    @Override
    public void setExclusions(List<String> exclusions) {
        checkGroups(this.groups.isEmpty());
        super.setExclusions(exclusions);
    }

    @Override
    public void setReason(String reason) {
        checkGroups(this.groups.isEmpty());
        super.setReason(reason);
    }

    public void setGroups(List<BannedImportGroupDefinition> groups) {
        checkGroups(!this.hasInput());
        checkArgument(groups != null && !groups.isEmpty(), "Groups may not be empty");
        this.groups = groups;
    }

    public final void setIncludeTestCode(boolean includeTestCode) {
        this.includeTestCode = includeTestCode;
    }

    public final void setCommentLineBufferSize(int commentLineBufferSize) {
        checkArgument(commentLineBufferSize > 0,
                "Configuration error: commentLineBufferSize must be > 0");
        this.commentLineBufferSize = commentLineBufferSize;
    }

    public final void setSourceFileCharset(String sourceFileCharset) {
        this.sourceFileCharset = Charset.forName(sourceFileCharset);
    }

    @Override
    public String getCacheId() {
        return "";
    }

    @Override
    public boolean isCacheable() {
        return false;
    }

    @Override
    public boolean isResultValid(EnforcerRule rule) {
        return false;
    }

}