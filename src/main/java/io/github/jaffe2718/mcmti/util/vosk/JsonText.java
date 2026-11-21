package io.github.jaffe2718.mcmti.util.vosk;

/**
 * Minimal JSON helper for the Vosk result documents.
 * <p>
 * Vosk returns documents of the form <code>{"text" : "hello world"}</code>.
 * Parsing those by hand avoids depending on whichever Gson version the
 * Minecraft installation happens to ship.
 */
public final class JsonText {

    private JsonText() {}

    public static String extractText(String json) {
        if (json == null) {
            return "";
        }
        int key = json.indexOf("\"text\"");
        if (key < 0) {
            return "";
        }
        int colon = json.indexOf(':', key + 6);
        if (colon < 0) {
            return "";
        }
        int quote = json.indexOf('"', colon + 1);
        if (quote < 0) {
            return "";
        }
        StringBuilder result = new StringBuilder();
        for (int i = quote + 1; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '"') {
                return result.toString();
            }
            if (c != '\\') {
                result.append(c);
                continue;
            }
            if (++i >= json.length()) {
                break;
            }
            char escaped = json.charAt(i);
            switch (escaped) {
                case 'n':
                    result.append('\n');
                    break;
                case 'r':
                    result.append('\r');
                    break;
                case 't':
                    result.append('\t');
                    break;
                case 'b':
                    result.append('\b');
                    break;
                case 'f':
                    result.append('\f');
                    break;
                case 'u':
                    if (i + 4 < json.length()) {
                        try {
                            result.append((char) Integer.parseInt(json.substring(i + 1, i + 5), 16));
                            i += 4;
                        } catch (NumberFormatException ignored) {
                            // keep the escape as is
                        }
                    }
                    break;
                default:
                    result.append(escaped);
                    break;
            }
        }
        return result.toString();
    }
}
