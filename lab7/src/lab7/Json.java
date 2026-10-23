package lab7;

import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Запис результатів у JSON (Map, Collection, Number, String, Boolean, Stats). */
public final class Json {

    private Json() {
    }

    /** Впорядкований словник з пар «ключ, значення». */
    public static Map<String, Object> obj(Object... keyValues) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            m.put((String) keyValues[i], keyValues[i + 1]);
        }
        return m;
    }

    public static String write(Object value) {
        StringBuilder sb = new StringBuilder();
        append(sb, value, 0);
        return sb.append('\n').toString();
    }

    private static void append(StringBuilder sb, Object v, int indent) {
        switch (v) {
            case null -> sb.append("null");
            case Stats s -> append(sb, obj("mean", s.mean(), "ci", s.ci()), indent);
            case Map<?, ?> m -> container(sb, '{', '}', m.entrySet().iterator(), indent, true);
            case Collection<?> c -> container(sb, '[', ']', c.iterator(), indent, false);
            case double[] a -> append(sb, java.util.Arrays.stream(a).boxed().toList(), indent);
            case Double d -> sb.append(Double.isFinite(d) ? String.format(Locale.ROOT, "%.10g", d) : "null");
            case Number n -> sb.append(n);
            case Boolean b -> sb.append(b);
            default -> sb.append('"').append(v.toString().replace("\\", "\\\\").replace("\"", "\\\"")
                    .replace("\n", "\\n")).append('"');
        }
    }

    private static void container(StringBuilder sb, char open, char close, Iterator<?> it, int indent,
                                  boolean map) {
        sb.append(open);
        boolean first = true;
        while (it.hasNext()) {
            Object item = it.next();
            sb.append(first ? "\n" : ",\n").append(" ".repeat(indent + 1));
            first = false;
            if (map) {
                Map.Entry<?, ?> e = (Map.Entry<?, ?>) item;
                append(sb, String.valueOf(e.getKey()), indent + 1);
                sb.append(": ");
                append(sb, e.getValue(), indent + 1);
            } else {
                append(sb, item, indent + 1);
            }
        }
        if (!first) {
            sb.append('\n').append(" ".repeat(indent));
        }
        sb.append(close);
    }
}
