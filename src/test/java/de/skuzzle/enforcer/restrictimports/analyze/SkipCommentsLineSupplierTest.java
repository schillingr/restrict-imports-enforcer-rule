package de.skuzzle.enforcer.restrictimports.analyze;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;

public class SkipCommentsLineSupplierTest {

    private final FileSystem fs = Jimfs.newFileSystem(Configuration.unix());
    private final LineSupplier subject = new SkipCommentsLineSupplier(
            StandardCharsets.UTF_8, 4);

    @Test
    void testNoComments() throws Exception {
        final Path file = new SourceFileBuilder(fs)
                .atPath("src/sample.txt")
                .withLines("line1", "line2", "line3");

        assertThat(subject.lines(file).collect(Collectors.toList()))
                .isEqualTo(Arrays.asList("line1", "line2", "line3"));
    }

    @Test
    void testInlineComment() throws Exception {
        final Path file = new SourceFileBuilder(fs)
                .atPath("src/sample.txt")
                .withLines("line1", "//line2", "line3");

        assertThat(subject.lines(file).collect(Collectors.toList()))
                .isEqualTo(Arrays.asList("line1", "", "line3"));
    }

    @Test
    void testSimpleBlockComment() throws Exception {
        final Path file = new SourceFileBuilder(fs)
                .atPath("src/sample.txt")
                .withLines("line1", "/*skip me*/", "line3");

        assertThat(subject.lines(file).collect(Collectors.toList()))
                .isEqualTo(Arrays.asList("line1", "", "line3"));
    }

    @Test
    void testInlineBlockComment() throws Exception {
        final Path file = new SourceFileBuilder(fs)
                .atPath("src/sample.txt")
                .withLines("line1", "line2/*skip me*/", "line3");

        assertThat(subject.lines(file).collect(Collectors.toList()))
                .isEqualTo(Arrays.asList("line1", "line2", "line3"));
    }

    @Test
    void testBlockCommentSpanningMultipleLines() throws Exception {
        final Path file = new SourceFileBuilder(fs)
                .atPath("src/sample.txt")
                .withLines("line/*1", "lien2", "line*/1");

        assertThat(subject.lines(file).collect(Collectors.toList()))
                .isEqualTo(Arrays.asList("line", "", "1"));
    }

    @Test
    void testCommentBufferOverflow() throws Exception {
        final Path file = new SourceFileBuilder(fs)
                .atPath("src/sample.txt")
                .withLines("line/*1", " ", " ", " ", " ", " ", " ", "line*/1");
        assertThatExceptionOfType(CommentBufferOverflowException.class)
                .isThrownBy(() -> subject.lines(file).count())
                .withMessageContaining("4")
                .withMessageContaining("7");
    }
}
