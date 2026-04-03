package com.solfini.matchengine.liquidity.direct.aiGenerated;

import java.util.ArrayList;
import java.util.List;

public class JsonHelper {

    public static String minExtract(final String json, final String key) {
        // finds "key":"value" or "key":123 or "key":true/false
        final String pat = "\"" + key + "\":";
        final int i = json.indexOf(pat);
        if (i < 0)
            return null;
        int j = i + pat.length();
        // skip spaces
        while (j < json.length() && (json.charAt(j) == ' '))
            j++;
        if (j >= json.length())
            return null;
        final char c = json.charAt(j);
        if (c == '"') {
            final int k = json.indexOf('"', j + 1);
            if (k < 0)
                return null;
            return json.substring(j + 1, k);
        } else if (c == 'n' && j + 4 <= json.length() &&
                json.charAt(j + 1) == 'u' &&
                json.charAt(j + 2) == 'l' &&
                json.charAt(j + 3) == 'l') {
            // Check if it's actually "null" (not "null123" or similar)
            if (j + 4 == json.length() ||
                    json.charAt(j + 4) == ',' ||
                    json.charAt(j + 4) == '}' ||
                    json.charAt(j + 4) == ']' ||
                    json.charAt(j + 4) == ' ') {
                return null;
            }
            // Fall through to number/bool handling if not actually null
            int k = j;
            final int n = json.length();
            while (k < n) {
                final char ch = json.charAt(k);
                if (ch == ',' || ch == '}' || ch == ']')
                    break;
                k++;
            }
            return json.substring(j, k).trim();
        } else {
            // number, bool, or unquoted value until , } or ]
            int k = j;
            final int n = json.length();
            while (k < n) {
                final char ch = json.charAt(k);
                if (ch == ',' || ch == '}' || ch == ']')
                    break;
                k++;
            }
            String val = json.substring(j, k).trim();
            // explicitly check for boolean values
            if ("true".equals(val) || "false".equals(val)) {
                return val;
            }
            return val;
        }
    }

    public static long parseLongSafe(final String s) {
        if (s == null || s.isEmpty())
            return 0L;
        try {
            return Long.parseLong(s.replace("\"", ""));
        } catch (Exception e) {
            return 0L;
        }
    }

    public static double parseDoubleSafe(final String s) {
        if (s == null || s.isEmpty())
            return 0.0;
        try {
            return Double.parseDouble(s.replace("\"", ""));
        } catch (Exception e) {
            return 0.0;
        }
    }

    /**
     * Properly extracts a JSON value by finding the complete object/value for a
     * given key
     * Handles nested JSON structures with proper bracket/brace matching
     * Searches for the key as a proper JSON key (with colon after it) to avoid
     * matching substrings
     *
     * @param json The JSON string to parse
     * @param key  The key to extract value for
     * @return The extracted value as string, or null if not found
     */
    public static String extractJsonValue(final String json, final String key) {
        if (json == null || key == null) {
            return null;
        }

        final String searchPattern = "\"" + key + "\"";
        int searchStartPos = 0;

        // Keep searching until we find a match followed by a colon (proper JSON key)
        while (searchStartPos < json.length()) {
            final int keyStart = json.indexOf(searchPattern, searchStartPos);
            if (keyStart < 0) {
                return null;
            }

            // Find the colon after the key
            int colonPos = -1;
            boolean isValidKey = true;
            for (int i = keyStart + searchPattern.length(); i < json.length(); i++) {
                final char c = json.charAt(i);
                if (c == ':') {
                    colonPos = i;
                    break;
                } else if (c != ' ' && c != '\n' && c != '\r' && c != '\t') {
                    // Not a valid key (no colon immediately after), continue searching
                    isValidKey = false;
                    break;
                }
            }

            if (!isValidKey) {
                // This was a false match (key string inside another string), continue searching
                searchStartPos = keyStart + searchPattern.length();
                continue;
            }

            if (colonPos < 0) {
                return null;
            }

            // Skip whitespace after colon to find value start
            int valueStart = -1;
            for (int i = colonPos + 1; i < json.length(); i++) {
                final char c = json.charAt(i);
                if (c != ' ' && c != '\n' && c != '\r' && c != '\t') {
                    valueStart = i;
                    break;
                }
            }

            if (valueStart < 0) {
                return null;
            }

            final char firstChar = json.charAt(valueStart);

            // Determine value type and extract accordingly
            if (firstChar == '{') {
                // Extract object
                return extractJsonObject(json, valueStart);
            } else if (firstChar == '[') {
                // Extract array
                return extractJsonArray(json, valueStart);
            } else if (firstChar == '"') {
                // Extract string
                return extractJsonString(json, valueStart);
            } else {
                // Extract primitive (number, boolean, null)
                return extractJsonPrimitive(json, valueStart);
            }
        }

        return null;
    }

    /**
     * Extracts a complete JSON object starting from the given position
     * Uses proper brace matching to handle nested objects
     */
    public static String extractJsonObject(final String json, final int start) {
        int braceDepth = 0;
        boolean inString = false;
        boolean escapeNext = false;

        for (int i = start; i < json.length(); i++) {
            final char c = json.charAt(i);

            if (escapeNext) {
                escapeNext = false;
                continue;
            }

            if (c == '\\') {
                escapeNext = true;
                continue;
            }

            if (c == '"') {
                inString = !inString;
                continue;
            }

            if (!inString) {
                if (c == '{') {
                    braceDepth++;
                } else if (c == '}') {
                    braceDepth--;
                    if (braceDepth == 0) {
                        return json.substring(start, i + 1);
                    }
                }
            }
        }
        return null;
    }

    /**
     * Extracts a complete JSON array starting from the given position
     * Uses proper bracket matching to handle nested arrays
     */
    public static String extractJsonArray(final String json, final int start) {
        int bracketDepth = 0;
        boolean inString = false;
        boolean escapeNext = false;

        for (int i = start; i < json.length(); i++) {
            final char c = json.charAt(i);

            if (escapeNext) {
                escapeNext = false;
                continue;
            }

            if (c == '\\') {
                escapeNext = true;
                continue;
            }

            if (c == '"') {
                inString = !inString;
                continue;
            }

            if (!inString) {
                if (c == '[') {
                    bracketDepth++;
                } else if (c == ']') {
                    bracketDepth--;
                    if (bracketDepth == 0) {
                        return json.substring(start, i + 1);
                    }
                }
            }
        }
        return null;
    }

    /**
     * Extracts a JSON string value (without quotes)
     */
    public static String extractJsonString(final String json, final int start) {
        boolean escapeNext = false;

        for (int i = start + 1; i < json.length(); i++) {
            final char c = json.charAt(i);

            if (escapeNext) {
                escapeNext = false;
                continue;
            }

            if (c == '\\') {
                escapeNext = true;
                continue;
            }

            if (c == '"') {
                return json.substring(start + 1, i); // Return without quotes
            }
        }
        return null;
    }

    /**
     * Extracts a JSON primitive value (number, boolean, null)
     */
    public static String extractJsonPrimitive(final String json, final int start) {
        for (int i = start; i < json.length(); i++) {
            final char c = json.charAt(i);
            if (c == ',' || c == '}' || c == ']' || c == '\n' || c == '\r') {
                return json.substring(start, i).trim();
            }
        }
        return json.substring(start).trim();
    }

    public static List<String> extractJsonObjects(final String arrayJson) {
        final List<String> result = new ArrayList<>();
        if (arrayJson == null || !arrayJson.startsWith("[")) return result;
        int idx = 0;
        while (idx < arrayJson.length()) {
            idx = arrayJson.indexOf('{', idx);
            if (idx < 0) break;
            int braceCount = 0;
            int objEnd = idx;
            for (int i = idx; i < arrayJson.length(); i++) {
                if (arrayJson.charAt(i) == '{') braceCount++;
                else if (arrayJson.charAt(i) == '}') {
                    braceCount--;
                    if (braceCount == 0) { objEnd = i; break; }
                }
            }
            if (objEnd <= idx) break;
            result.add(arrayJson.substring(idx, objEnd + 1));
            idx = objEnd + 1;
        }
        return result;
    }

    public static double parseFirstArrayElementDouble(final String arrStr) {
        if (arrStr == null || !arrStr.startsWith("[")) return 0.0;
        final int comma = arrStr.indexOf(',');
        final int end = arrStr.indexOf(']');
        if (comma > 0) return parseDoubleSafe(arrStr.substring(1, comma).trim());
        if (end > 1) return parseDoubleSafe(arrStr.substring(1, end).trim());
        return 0.0;
    }

    public static double parseSecondArrayElementDouble(final String arrStr) {
        if (arrStr == null || !arrStr.startsWith("[")) return 0.0;
        final int comma = arrStr.indexOf(',');
        final int end = arrStr.indexOf(']');
        if (comma > 0 && end > comma) return parseDoubleSafe(arrStr.substring(comma + 1, end).trim());
        return 0.0;
    }

    public static String escapeJson(final String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    /**
     * Extracts the first order object from a JSON array
     * Handles proper brace matching to extract the complete first object
     *
     * @param arrayJson The JSON array as string
     * @return The first order object as JSON string, or null if not found
     */
    public static String extractFirstOrderFromArray(final String arrayJson) {
        if (arrayJson == null || arrayJson.trim().isEmpty()) {
            return null;
        }

        final String trimmed = arrayJson.trim();

        // Find the opening brace of the first object
        final int objStart = trimmed.indexOf('{');
        if (objStart < 0) {
            return null;
        }

        // Find the matching closing brace for the first object
        int braceDepth = 0;
        boolean inString = false;
        boolean escapeNext = false;

        for (int i = objStart; i < trimmed.length(); i++) {
            final char c = trimmed.charAt(i);

            if (escapeNext) {
                escapeNext = false;
                continue;
            }

            if (c == '\\') {
                escapeNext = true;
                continue;
            }

            if (c == '"') {
                inString = !inString;
                continue;
            }

            if (!inString) {
                if (c == '{') {
                    braceDepth++;
                } else if (c == '}') {
                    braceDepth--;
                    if (braceDepth == 0) {
                        // Found the end of the first complete object
                        final String orderObject = trimmed.substring(objStart, i + 1);
                        return orderObject;
                    }
                }
            }
        }

        return null;
    }

}