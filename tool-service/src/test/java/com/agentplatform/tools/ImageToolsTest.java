package com.agentplatform.tools;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ImageToolsTest {

    private final ImageTools imageTools = new ImageTools();

    @Test
    @DisplayName("generateImage returns an HTML fragment with an img tag that renders in the chat UI")
    void generateImage_returnsRenderableHtml() {
        String html = imageTools.generateImage("a rocket launching");

        assertThat(html).startsWith("<div class=\"tool-image-result\">");
        assertThat(html).contains("<img");
        assertThat(html).contains("loading=\"lazy\"");
        assertThat(html).contains("Generated image — \"a rocket launching\"");
    }

    @Test
    @DisplayName("same prompt produces a deterministic URL while the fragment is prompt-escaped")
    void generateImage_urlIsDeterministicAndSafe() {
        String prompt = "castle <on> \"fire\"";
        String html = imageTools.generateImage(prompt);

        assertThat(html).doesNotContain("<on>");
        assertThat(html).doesNotContain("\"fire\"");
        assertThat(html).contains("&lt;on&gt;");
        assertThat(html).contains("&quot;fire&quot;");
        assertThat(html).startsWith("<div class=\"tool-image-result\">");

        assertThat(imageTools.generateImage(prompt))
                .isEqualTo(imageTools.generateImage(prompt));
    }

    @Test
    @DisplayName("image requests are detected for draw / generate-an-image / create-a-photo phrasing")
    void isImageRequest_positiveCases() {
        assertThat(ImageTools.isImageRequest("Draw a cat")).isTrue();
        assertThat(ImageTools.isImageRequest("please draw a rocket")).isTrue();
        assertThat(ImageTools.isImageRequest("Generate an image of a sunset")).isTrue();
        assertThat(ImageTools.isImageRequest("create a photo of a car")).isTrue();
        assertThat(ImageTools.isImageRequest("make a picture of mountains")).isTrue();
    }

    @Test
    @DisplayName("non-image prompts and edge cases are rejected")
    void isImageRequest_negativeCases() {
        assertThat(ImageTools.isImageRequest("Hello there")).isFalse();
        assertThat(ImageTools.isImageRequest("Please withdraw cash")).isFalse();
        assertThat(ImageTools.isImageRequest("")).isFalse();
        assertThat(ImageTools.isImageRequest(null)).isFalse();
    }
}