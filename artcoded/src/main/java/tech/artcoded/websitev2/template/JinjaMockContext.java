package tech.artcoded.websitev2.template;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Builds a mock context for a jinja template, to preview it without real data: the variables it reads (found in its
 * source), with values of a plausible type. Loops get a list of two items, values piped to a date filter get an ISO
 * date, values used in arithmetic or with a number filter get a number, the others their name.
 * <p>
 * Only a best effort: the result is meant to be edited before a preview when a template needs more.
 */
public final class JinjaMockContext {
    private static final Pattern COMMENT = Pattern.compile("\\{#.*?#\\}", Pattern.DOTALL);
    private static final Pattern TAG = Pattern.compile("\\{\\{-?(.*?)-?\\}\\}|\\{%-?(.*?)-?%\\}", Pattern.DOTALL);
    private static final Pattern STRING = Pattern.compile("\"(?:[^\"\\\\]|\\\\.)*\"|'(?:[^'\\\\]|\\\\.)*'");
    private static final Pattern PATH = Pattern
            .compile("[A-Za-z_]\\w*(?:\\s*\\.\\s*[A-Za-z_]\\w*|\\[\\s*\\d+\\s*\\])*");
    private static final Pattern FOR = Pattern
            .compile("^\\s*for\\s+([A-Za-z_]\\w*)(?:\\s*,\\s*([A-Za-z_]\\w*))?\\s+in\\s+(.*)$", Pattern.DOTALL);
    private static final Pattern SET = Pattern.compile("^\\s*set\\s+([A-Za-z_][\\w\\s,]*?)\\s*=(.*)$", Pattern.DOTALL);
    private static final Pattern BLOCK_SET = Pattern.compile("^\\s*set\\s+([A-Za-z_]\\w*)\\s*$");
    private static final Pattern WITH = Pattern.compile("([A-Za-z_]\\w*)\\s*=(?!=)");
    private static final Pattern MACRO = Pattern.compile(
            "^\\s*(macro|call)\\s*(?:\\(([^)]*)\\))?\\s*([A-Za-z_]\\w*)?\\s*(?:\\(([^)]*)\\))?", Pattern.DOTALL);
    private static final Pattern IMPORT = Pattern
            .compile("^\\s*(?:import\\s+.*\\s+as\\s+([A-Za-z_]\\w*)|from\\s+.*\\s+import\\s+(.*))$", Pattern.DOTALL);

    private static final Set<String> KEYWORDS = Set.of("if", "elif", "else", "endif", "for", "endfor", "in", "not",
            "and", "or", "is", "true", "false", "none", "True", "False", "None", "set", "endset", "macro", "endmacro",
            "call", "endcall", "filter", "endfilter", "block", "endblock", "extends", "include", "import", "from", "as",
            "with", "endwith", "without", "context", "ignore", "missing", "loop", "super", "self", "caller", "varargs",
            "kwargs", "range", "namespace", "dict", "debug", "autoescape", "endautoescape", "raw", "endraw",
            "recursive", "continue", "break", "do");
    private static final Set<String> DATE_FILTERS = Set.of("datetimeformat", "dateformat", "timeformat");
    private static final Set<String> NUMBER_FILTERS = Set.of("round", "int", "float", "abs", "number", "currency",
            "filesizeformat", "format_number");
    private static final Set<String> LIST_FILTERS = Set.of("length", "join", "first", "last", "sort", "list", "reverse",
            "unique", "batch", "slice", "count", "map", "select", "reject", "selectattr", "rejectattr", "sum", "min",
            "max", "groupby");
    private static final Set<String> NUMBER_WORDS = Set.of("total", "subtotal", "amount", "price", "rate", "vat", "tva",
            "tax", "taxes", "taxable", "sum", "count", "hours", "hour", "days", "minutes", "duration", "percent",
            "percentage", "quantity", "qty", "balance", "cost", "costs", "size", "year", "month", "advance", "charge",
            "average", "median", "ratio", "earnings", "expenses", "revenue", "profit", "loan", "index", "position");
    private static final Set<String> STRING_WORDS = Set.of("number", "name", "label", "type", "id", "ref", "reference",
            "account", "iban", "bic", "code", "key", "url", "address", "status", "description", "title", "note",
            "currency", "symbol", "unit", "text", "message", "email", "phone", "tone", "color", "chart", "style",
            "class", "icon", "kind", "mode", "html", "content", "body", "subject", "period");
    /** first words of a number: maxDaysToPay, totalExpenses */
    private static final Set<String> NUMBER_FIRST_WORDS = Set.of("max", "min", "avg", "nb", "total", "sub", "count",
            "hours", "days", "minutes", "price", "amount");
    private static final Set<String> DATE_WORDS = Set.of("date", "at", "time", "timestamp", "datetime");
    private static final Set<String> WEAK_DATE_WORDS = Set.of("day", "since", "from", "until", "to", "start", "end");
    private static final Set<String> BOOL_FIRST_WORDS = Set.of("is", "has", "can", "should", "show", "use", "with");
    private static final Set<String> BOOL_NAMES = Set.of("visible", "enabled", "disabled", "closed", "done", "locked",
            "archived", "current", "bookmarked", "seen", "sent", "student", "valid", "bcc", "imported", "hidden");

    enum Kind {
        STRING, NUMBER, DATE, BOOLEAN, LIST, MAP
    }

    /** a node of the context tree */
    static final class Node {
        final Map<String, Node> children = new LinkedHashMap<>();
        Node item; // list element
        Kind hint;
        boolean iterated;
        boolean pairs; // for k, v in x|items

        Node child(String name) {
            return children.computeIfAbsent(name, k -> new Node());
        }

        Node item() {
            if (item == null) {
                item = new Node();
            }
            return item;
        }
    }

    private final Node root = new Node();
    /** loop variables and aliases (set x = a.b): name to the path they stand for ([] = an item of the list) */
    private final Map<String, String> bound = new HashMap<>();
    private final Set<String> locals = new HashSet<>();
    private final Instant now;

    private JinjaMockContext(Instant now) {
        this.now = now;
    }

    public static Map<String, Object> of(String source) {
        return of(source, Instant.now());
    }

    public static Map<String, Object> of(String source, Instant now) {
        if (source == null || source.isBlank()) {
            return new LinkedHashMap<>();
        }
        var mock = new JinjaMockContext(now.truncatedTo(ChronoUnit.SECONDS));
        mock.parse(source);
        @SuppressWarnings("unchecked")
        var out = (Map<String, Object>) mock.value(mock.root, "", Kind.MAP);
        return out;
    }

    private void parse(String source) {
        Matcher m = TAG.matcher(COMMENT.matcher(source).replaceAll(""));
        while (m.find()) {
            if (m.group(1) != null) {
                scan(stripStrings(m.group(1)));
            } else {
                statement(stripStrings(m.group(2)));
            }
        }
    }

    private static String stripStrings(String s) {
        return STRING.matcher(s).replaceAll("\"\"");
    }

    private void statement(String s) {
        Matcher f = FOR.matcher(s);
        if (f.find()) {
            String expr = f.group(3).replaceFirst("\\s+recursive\\s*$", "");
            Matcher first = PATH.matcher(expr);
            if (first.find() && expr.substring(0, first.start()).isBlank() && !KEYWORDS.contains(first.group())) {
                String path = normalize(first.group());
                boolean method = expr.substring(first.end()).stripLeading().startsWith("(") && path.contains(".");
                if (method) {
                    path = path.substring(0, path.lastIndexOf('.'));
                }
                String iterable = resolve(path);
                if (iterable != null) {
                    boolean pairs = f.group(2) != null || method;
                    Node n = node(iterable);
                    n.iterated = true;
                    n.pairs = n.pairs || pairs || expr.contains("items()") || expr.matches("(?s).*\\|\\s*items.*");
                    if (pairs) {
                        locals.add(f.group(1));
                        locals.add(f.group(2));
                    } else {
                        bound.put(f.group(1), iterable + ".[]");
                    }
                } else {
                    // iterating a local (a macro parameter...): the loop variables are locals too
                    locals.add(f.group(1));
                    if (f.group(2) != null) {
                        locals.add(f.group(2));
                    }
                }
                scan(expr.substring(first.end()));
            } else {
                locals.add(f.group(1));
                if (f.group(2) != null) {
                    locals.add(f.group(2));
                }
                scan(expr);
            }
            return;
        }
        Matcher set = SET.matcher(s);
        if (set.find()) {
            String expr = set.group(2).trim();
            String[] names = set.group(1).split("\\s*,\\s*");
            Matcher p = PATH.matcher(expr);
            // set x = a.b (or a.b or {}): an alias, what is read through x is read in a.b
            if (names.length == 1 && p.find() && p.start() == 0 && !KEYWORDS.contains(p.group())
                    && expr.substring(p.end()).matches("\\s*(or\\s*(\\{\\s*}|\\[\\s*]|\"\"|''|none|None))?\\s*")) {
                String target = resolve(normalize(p.group()));
                if (target != null) {
                    bound.put(names[0].trim(), target);
                    return;
                }
            }
            for (String n : names) {
                locals.add(n.trim());
            }
            scan(expr);
            return;
        }
        Matcher bs = BLOCK_SET.matcher(s);
        if (bs.find()) {
            locals.add(bs.group(1));
            return;
        }
        Matcher macro = MACRO.matcher(s);
        if (macro.find()) {
            if (macro.group(3) != null && "macro".equals(macro.group(1))) {
                locals.add(macro.group(3));
            }
            for (String params : new String[] { macro.group(2), macro.group(4) }) {
                if (params == null) {
                    continue;
                }
                for (String param : params.split(",")) {
                    String name = param.split("=")[0].trim();
                    if (!name.isEmpty()) {
                        locals.add(name);
                    }
                    if (param.contains("=")) {
                        scan(param.substring(param.indexOf('=') + 1));
                    }
                }
            }
            if ("call".equals(macro.group(1))) {
                scan(s.substring(macro.end()));
            }
            return;
        }
        Matcher imp = IMPORT.matcher(s);
        if (imp.find()) {
            if (imp.group(1) != null) {
                locals.add(imp.group(1));
            } else {
                for (String n : imp.group(2).split(",")) {
                    String[] parts = n.trim().split("\\s+as\\s+");
                    locals.add(parts[parts.length - 1].trim());
                }
            }
            return;
        }
        String trimmed = s.trim();
        if (trimmed.startsWith("with ")) {
            Matcher w = WITH.matcher(trimmed);
            while (w.find()) {
                locals.add(w.group(1));
            }
        }
        // if / elif / filter / extends...: the expression part
        scan(trimmed.replaceFirst("^(if|elif|with|filter|include|extends|do|print)\\b", ""));
    }

    private static String normalize(String path) {
        return path.replaceAll("\\s+", "").replaceAll("\\[\\d+]", ".[]");
    }

    /** the path a name stands for, null for a local or a keyword */
    private String resolve(String path) {
        String head = path.split("\\.", 2)[0];
        String rest = path.contains(".") ? path.substring(head.length()) : "";
        if (KEYWORDS.contains(head) || locals.contains(head)) {
            return null;
        }
        if (bound.containsKey(head)) {
            return bound.get(head) + rest;
        }
        return path;
    }

    private Node node(String path) {
        Node n = root;
        for (String seg : path.split("\\.")) {
            n = "[]".equals(seg) ? n.item() : n.child(seg);
        }
        return n;
    }

    /** every variable read in an expression, with what its use tells about its type */
    private void scan(String expr) {
        Matcher m = PATH.matcher(expr);
        while (m.find()) {
            String raw = m.group();
            int start = m.start();
            int end = m.end();
            String before = expr.substring(0, start).stripTrailing();
            String after = expr.substring(end).stripLeading();
            // a filter, a test (is defined), a keyword argument, a number
            if (before.endsWith("|") || before.matches("(?s).*\\bis(\\s+not)?$") || after.matches("(?s)^=(?!=).*")
                    || before.endsWith(".")) {
                continue;
            }
            String path = normalize(raw);
            boolean call = after.startsWith("(");
            if (call) {
                int dot = path.lastIndexOf('.');
                if (dot < 0) {
                    continue; // a function: range(), dict()...
                }
                String method = path.substring(dot + 1);
                path = path.substring(0, dot);
                String resolved = resolve(path);
                if (resolved != null) {
                    Node n = node(resolved);
                    if (method.equals("items") || method.equals("keys") || method.equals("values")) {
                        hint(n, Kind.MAP);
                    }
                }
                continue;
            }
            String resolved = resolve(path);
            if (resolved == null) {
                continue;
            }
            Node n = node(resolved);
            Matcher filter = Pattern.compile("^\\|\\s*(\\w+)").matcher(after);
            if (filter.find()) {
                String name = filter.group(1);
                if (DATE_FILTERS.contains(name)) {
                    hint(n, Kind.DATE);
                } else if (NUMBER_FILTERS.contains(name)) {
                    hint(n, Kind.NUMBER);
                } else if (LIST_FILTERS.contains(name)) {
                    hint(n, Kind.LIST);
                } else if (name.equals("items") || name.equals("dictsort")) {
                    hint(n, Kind.MAP);
                }
            }
            if (before.matches("(?s).*[+*/%-]$") || after.matches("(?s)^[+*/%-][^%}].*|^[+*/%-]$")
                    || after.matches("(?s)^(<|>|<=|>=)\\s*-?\\d.*") || before.matches("(?s).*\\d\\s*(<|>|<=|>=)$")) {
                hint(n, Kind.NUMBER);
            }
        }
    }

    private static void hint(Node n, Kind k) {
        if (n.hint == null || n.hint == Kind.STRING) {
            n.hint = k;
        }
    }

    private Object value(Node n, String name, Kind fallback) {
        if (n.iterated || n.hint == Kind.LIST) {
            if (n.pairs) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put(name + "_key_1", name + " 1");
                m.put(name + "_key_2", name + " 2");
                return m;
            }
            List<Object> l = new ArrayList<>();
            String itemName = singular(name);
            for (int i = 1; i <= 2; i++) {
                Object v = n.item != null ? value(n.item, itemName, Kind.STRING) : itemName + " " + i;
                if (v instanceof String s && n.item != null && n.item.children.isEmpty()) {
                    v = s + " " + i;
                }
                l.add(v);
            }
            return l;
        }
        if (!n.children.isEmpty() || n.hint == Kind.MAP || fallback == Kind.MAP) {
            Map<String, Object> m = new LinkedHashMap<>();
            n.children.forEach((k, child) -> m.put(k, value(child, k, null)));
            return m;
        }
        Kind kind = n.hint != null ? n.hint : guess(name);
        return switch (kind) {
            case DATE -> DateTimeFormatter.ISO_INSTANT.format(now);
            case NUMBER -> number(name);
            case BOOLEAN -> true;
            default -> name.isEmpty() ? "value" : name;
        };
    }

    private Object number(String name) {
        List<String> w = words(name);
        String last = w.isEmpty() ? "" : w.get(w.size() - 1);
        var date = now.atOffset(ZoneOffset.UTC);
        if (last.equals("year")) {
            return date.getYear();
        }
        if (last.equals("month")) {
            return date.getMonthValue();
        }
        if (w.stream()
                .anyMatch(x -> Set
                        .of("count", "days", "hours", "hour", "minutes", "quantity", "qty", "size", "index", "position")
                        .contains(x))) {
            return 8;
        }
        if (w.stream().anyMatch(x -> Set.of("rate", "percent", "percentage", "ratio").contains(x))
                && !w.contains("price")) {
            return 21;
        }
        return 1234.56;
    }

    /** camelCase / snake_case words, lower case */
    static List<String> words(String name) {
        List<String> out = new ArrayList<>();
        for (String w : name.split("_|(?<=[a-z0-9])(?=[A-Z])|(?<=[A-Z])(?=[A-Z][a-z])")) {
            if (!w.isEmpty()) {
                out.add(w.toLowerCase(Locale.ROOT));
            }
        }
        return out;
    }

    private static String singular(String name) {
        if (name.endsWith("ies")) {
            return name.substring(0, name.length() - 3) + "y";
        }
        if (name.endsWith("s") && name.length() > 1) {
            return name.substring(0, name.length() - 1);
        }
        return name.isEmpty() ? "item" : name;
    }

    static Kind guess(String name) {
        List<String> w = words(name);
        if (w.isEmpty()) {
            return Kind.STRING;
        }
        String first = w.get(0);
        String last = w.get(w.size() - 1);
        if (BOOL_NAMES.contains(name.toLowerCase(Locale.ROOT)) || w.size() > 1 && BOOL_FIRST_WORDS.contains(first)) {
            return Kind.BOOLEAN;
        }
        if (DATE_WORDS.contains(last) || first.equals("date") || last.endsWith("date")) {
            return Kind.DATE;
        }
        if (STRING_WORDS.contains(last)) {
            return Kind.STRING;
        }
        if (NUMBER_WORDS.contains(last) || w.size() > 1 && NUMBER_FIRST_WORDS.contains(first)) {
            return Kind.NUMBER;
        }
        if (WEAK_DATE_WORDS.contains(last)) {
            return Kind.DATE;
        }
        return Kind.STRING;
    }
}
