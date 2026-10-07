package com.wingmark.backend.util;

import java.util.HashMap;
import java.util.Map;

/**
 * Builds a regular expression for a user's search text that matches literally, ignores Turkish and common Western
 * European accents in both the text and the stored value (so "serce" finds "Serçe" and "serçe" finds "Serce"), and
 * is meant to be used with a case-insensitive match.
 */
public final class SearchPattern {

    /** Each group lists a base letter followed by every variant that should match it, in both cases. */
    private static final String[] GROUPS = {
            "aAâÂàÀáÁäÄãÃåÅ", "cCçÇ", "eEéÉèÈêÊëË", "gGğĞ", "iIıİîÎíÍìÌïÏ", "nNñÑ", "oOöÖôÔòÒóÓõÕ", "sSşŞ",
            "uUüÜûÛùÙúÚ", "yYýÝÿ"
    };

    private static final Map<Character, String> CLASS_BY_CHAR = new HashMap<>();

    static {
        for (String group : GROUPS) {
            String charClass = "[" + group + "]";
            for (char c : group.toCharArray()) {
                CLASS_BY_CHAR.put(c, charClass);
            }
        }
    }

    private SearchPattern() {
    }

    /** Returns a regex that matches the text anywhere, literally apart from the accent folding. */
    public static String accentInsensitive(String text) {
        StringBuilder pattern = new StringBuilder();
        for (char c : text.toCharArray()) {
            String charClass = CLASS_BY_CHAR.get(c);
            if (charClass != null) {
                pattern.append(charClass);
            } else if (".[]{}()*+-?^$|\\/".indexOf(c) >= 0) {
                pattern.append('\\').append(c);
            } else {
                pattern.append(c);
            }
        }
        return pattern.toString();
    }
}
