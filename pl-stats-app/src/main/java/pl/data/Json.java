package pl.data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal, dependency-free JSON parser.
 * Produces Map<String,Object>, List<Object>, String, Double, Boolean or null.
 */
public final class Json {
    private final String s;
    private int i;

    private Json(String s) { this.s = s; }

    public static Object parse(String text) {
        Json p = new Json(text);
        p.ws();
        Object v = p.value();
        p.ws();
        if (p.i != p.s.length()) throw p.err("Trailing characters");
        return v;
    }

    // ---- typed helpers for reading parsed objects ----

    @SuppressWarnings("unchecked")
    public static Map<String, Object> obj(Object o) { return (Map<String, Object>) o; }

    @SuppressWarnings("unchecked")
    public static List<Object> arr(Object o) { return o == null ? List.of() : (List<Object>) o; }

    public static String str(Map<String, Object> m, String k) {
        Object v = m.get(k);
        return v == null ? null : v.toString();
    }

    public static int integer(Map<String, Object> m, String k) {
        Integer v = intOrNull(m, k);
        return v == null ? 0 : v;
    }

    public static Integer intOrNull(Map<String, Object> m, String k) {
        Object v = m.get(k);
        if (v instanceof Number n) return n.intValue();
        if (v instanceof String str && !str.isBlank()) {
            try { return (int) Double.parseDouble(str); } catch (NumberFormatException ignored) { }
        }
        return null;
    }

    /** Reads numbers that FPL sometimes sends as strings (e.g. "0.45"). */
    public static double dbl(Map<String, Object> m, String k) {
        Object v = m.get(k);
        if (v instanceof Number n) return n.doubleValue();
        if (v instanceof String str && !str.isBlank()) {
            try { return Double.parseDouble(str); } catch (NumberFormatException ignored) { }
        }
        return 0;
    }

    public static boolean bool(Map<String, Object> m, String k) {
        return Boolean.TRUE.equals(m.get(k));
    }

    // ---- parser ----

    private Object value() {
        if (i >= s.length()) throw err("Unexpected end");
        char c = s.charAt(i);
        switch (c) {
            case '{': return object();
            case '[': return array();
            case '"': return string();
            case 't': expect("true"); return Boolean.TRUE;
            case 'f': expect("false"); return Boolean.FALSE;
            case 'n': expect("null"); return null;
            default: return number();
        }
    }

    private Map<String, Object> object() {
        Map<String, Object> m = new LinkedHashMap<>();
        i++; ws();
        if (peek() == '}') { i++; return m; }
        while (true) {
            ws();
            String key = string();
            ws();
            if (s.charAt(i++) != ':') throw err("Expected ':'");
            ws();
            m.put(key, value());
            ws();
            char c = s.charAt(i++);
            if (c == '}') return m;
            if (c != ',') throw err("Expected ',' or '}'");
        }
    }

    private List<Object> array() {
        List<Object> l = new ArrayList<>();
        i++; ws();
        if (peek() == ']') { i++; return l; }
        while (true) {
            ws();
            l.add(value());
            ws();
            char c = s.charAt(i++);
            if (c == ']') return l;
            if (c != ',') throw err("Expected ',' or ']'");
        }
    }

    private String string() {
        if (s.charAt(i) != '"') throw err("Expected string");
        i++;
        StringBuilder sb = new StringBuilder();
        while (true) {
            char c = s.charAt(i++);
            if (c == '"') return sb.toString();
            if (c == '\\') {
                char e = s.charAt(i++);
                switch (e) {
                    case '"': sb.append('"'); break;
                    case '\\': sb.append('\\'); break;
                    case '/': sb.append('/'); break;
                    case 'b': sb.append('\b'); break;
                    case 'f': sb.append('\f'); break;
                    case 'n': sb.append('\n'); break;
                    case 'r': sb.append('\r'); break;
                    case 't': sb.append('\t'); break;
                    case 'u': sb.append((char) Integer.parseInt(s.substring(i, i + 4), 16)); i += 4; break;
                    default: throw err("Bad escape");
                }
            } else {
                sb.append(c);
            }
        }
    }

    private Double number() {
        int start = i;
        while (i < s.length() && "+-0123456789.eE".indexOf(s.charAt(i)) >= 0) i++;
        if (start == i) throw err("Unexpected character '" + s.charAt(i) + "'");
        return Double.parseDouble(s.substring(start, i));
    }

    private void expect(String word) {
        if (!s.startsWith(word, i)) throw err("Expected " + word);
        i += word.length();
    }

    private char peek() { return i < s.length() ? s.charAt(i) : '\0'; }

    private void ws() {
        while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++;
    }

    private IllegalArgumentException err(String msg) {
        return new IllegalArgumentException("JSON parse error at " + i + ": " + msg);
    }
}
