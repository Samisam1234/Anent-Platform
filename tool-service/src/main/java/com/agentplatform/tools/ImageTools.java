package com.agentplatform.tools;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Image generation tool.
 *
 * <p>Registered as a LangChain4j {@link Tool} so an LLM that supports function
 * calling can invoke it autonomously, and as a Spring {@link Component} so the
 * orchestrator can also call it directly. The returned value is a self-contained
 * HTML fragment (a {@code &lt;div class="tool-image-result"&gt;} wrapping a
 * {@code <img>}) that the chat UI renders inline as-is.</p>
 *
 * <p>Generation is deliberately free of any external LLM/image API: the placeholder
 * image URL is derived deterministically from the prompt (same prompt → same
 * image) and can be swapped for a real generation provider later by changing
 * {@code tools.image.url-template}.</p>
 */
@Component
public class ImageTools {

    public static final String DEFAULT_URL_TEMPLATE = "https://picsum.photos/seed/%s/800/450";

    private static final Pattern DRAW_WORD = Pattern.compile("\\b(draw|drawing|sketch|illustrate)\\b");

    /** Phrases that clearly request a new image, even without the "draw" verb. */
    private static final List<String> IMAGE_PHRASES = List.of(
            "generate an image",
            "generate a image",
            "create a photo",
            "create an image",
            "make an image",
            "make a picture",
            "a picture of",
            "an image of",
            "a photo of");

    private String urlTemplate = DEFAULT_URL_TEMPLATE;

    public ImageTools() {
        // Spring injects urlTemplate via the setter below.
    }

    ImageTools(String urlTemplate) {
        this.urlTemplate = urlTemplate;
    }

    @Value("${tools.image.url-template:" + DEFAULT_URL_TEMPLATE + "}")
    public void setUrlTemplate(String urlTemplate) {
        this.urlTemplate = urlTemplate;
    }

    /**
     * {@code true} when {@code userMessage} asks the agent to produce an image.
     * Kept next to the tool so the orchestrator and the LLM share the same intent
     * vocabulary.
     */
    public static boolean isImageRequest(String userMessage) {
        if (userMessage == null) {
            return false;
        }
        String lower = userMessage.trim().toLowerCase(Locale.ROOT);
        if (lower.isEmpty()) {
            return false;
        }
        return DRAW_WORD.matcher(lower).find()
                || IMAGE_PHRASES.stream().anyMatch(lower::contains);
    }

    /**
     * Generates an image for {@code prompt} and returns an HTML fragment ready to
     * be injected into the chat UI. The fragment is trusted output (produced by
     * this method, not by the LLM) — all prompt-derived text is HTML-escaped.
     */
    @Tool("Generates an image from a text description and returns HTML that renders directly in the chat.")
    public String generateImage(@P("Text description of the image to generate") String prompt) {
        String safePrompt = prompt == null ? "" : escape(prompt);
        String seed = Integer.toHexString((prompt == null ? "" : prompt).hashCode());
        String url = String.format(urlTemplate, seed);
        return "<div class=\"tool-image-result\">"
                + "<p class=\"tool-image-caption\">Generated image — \"" + safePrompt + "\"</p>"
                + "<img class=\"tool-image\" src=\"" + url + "\" alt=\"Generated image for: "
                + safePrompt + "\" loading=\"lazy\">"
                + "</div>";
    }

    private static String escape(String input) {
        return input.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#039;");
    }
}