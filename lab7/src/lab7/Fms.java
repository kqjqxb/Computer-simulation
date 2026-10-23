package lab7;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Завдання 2: Петрі-об'єктна модель роботизованої гнучкої виробничої системи (задача 2 практикуму 5).
 * Варіант 1 – по одному закріпленому роботу на кожному шляху, варіант 2 – спільний парк роботів.
 */
public final class Fms {

    static final double ARRIVAL = 40;
    static final Delay HANDLE = Delay.unif(8, 1);
    static final int SLOTS = 3;
    static final Map<String, Double> POSITION = new LinkedHashMap<>();
    /** Шлях: назва, початок, кінець, запит, місця на верстаті призначення, звільнені місця, результат. */
    static final String[][] ROUTES = {
        {"вхід–В1", "вхід", "В1", "Деталь на вході", "Вільні місця В1", null, "Деталь на В1"},
        {"В1–В2", "В1", "В2", "Оброблена на В1", "Вільні місця В2", "Вільні місця В1", "Деталь на В2"},
        {"В2–склад", "В2", "склад", "Оброблена на В2", null, "Вільні місця В2", "Склад"},
    };
    static final double BAND = 620;
    static final double TIME = 100000;
    static final double WARMUP = 10000;
    static final double TAIL = 5000;
    static final int RUNS = 20;
    static final int SATURATION = 100000;
    static final double[] INTERVALS = {45, 50, 55, 60, 70, 80};
    static final String[][] METRICS = {
        {"throughput", "пропускна здатність, деталей/год"},
        {"time_in_system", "середній час перебування деталі, с"},
        {"input_queue", "середня черга на вході"},
        {"robots_busy", "середня кількість зайнятих роботів"},
        {"m1_slots", "зайняті місця В1 (з резервом)"},
        {"m2_slots", "зайняті місця В2 (з резервом)"},
        {"m1_processing", "деталей в обробці на В1"},
        {"m2_processing", "деталей в обробці на В2"},
        {"m1_done_waiting", "оброблені на В1 чекають робота"},
        {"m2_done_waiting", "оброблені на В2 чекають робота"},
        {"idle_moves", "роботів у холостому ході"},
    };

    static {
        POSITION.put("вхід", 0.0);
        POSITION.put("В1", 6.0);
        POSITION.put("В2", 13.0);
        POSITION.put("склад", 18.0);
    }

    private Fms() {
    }

    static double distance(String a, String b) {
        return Math.abs(POSITION.get(a) - POSITION.get(b));
    }

    /** arrival – середній інтервал надходження деталей, 0 – необмежене надходження. */
    static ObjectModel model(int variant, double arrival) {
        ObjectModel m = new ObjectModel("Гнучка виробнича система, варіант " + variant);
        boolean saturated = arrival == 0;
        Delay interval = Delay.exp(saturated ? ARRIVAL : arrival);
        Generator gen = m.add(new Generator("Генератор", "Генератор", "Надходження", "Деталь на вході",
                interval, saturated ? 0 : 1, saturated ? SATURATION : 0), 0, 0);
        double shift = variant == 1 ? 0 : 520;
        Machine m1 = m.add(new Machine("В1", SLOTS, Delay.norm(60, 10)), 20, BAND);
        Machine m2 = m.add(new Machine("В2", SLOTS, Delay.exp(100)), 20, 2 * BAND);
        List<Transfer> transfers = new ArrayList<>();
        List<RobotPool.Route> poolRoutes = new ArrayList<>();
        for (int k = 1; k <= ROUTES.length; k++) {
            String[] r = ROUTES[k - 1];
            double move = distance(r[1], r[2]);
            String robot = variant == 1 ? "Робот " + k + " повертається" : "Вільний робот: " + r[2];
            double y = BAND * (k - 1);
            transfers.add(m.add(new Transfer(r[0], move, HANDLE, r[5], r[6], robot), 660 + shift, y));
            if (variant == 1) {
                AssignedRobot a = m.add(new AssignedRobot(k, r[0], move, r[3], r[4]), 260, y);
                m.share(a, "Робот " + k + " повертається");
            } else {
                poolRoutes.add(new RobotPool.Route(r[0], r[1], r[3], r[4]));
            }
        }
        if (variant == 2) {
            Map<String, Integer> robots = Map.of("вхід", 1, "В1", 1, "В2", 1);
            RobotPool pool = m.add(new RobotPool(POSITION, robots, poolRoutes, BAND), 420, 0);
            // після вивільнення деталі робот стає вільним у кінці шляху
            for (String[] r : ROUTES) {
                m.share(pool, "Вільний робот: " + r[2]);
            }
        }
        m.share(gen, "Деталь на вході");
        m.share(m1, "Деталь на В1", "Оброблена на В1", "Вільні місця В1");
        m.share(m2, "Деталь на В2", "Оброблена на В2", "Вільні місця В2");
        for (int k = 0; k < ROUTES.length; k++) {
            m.share(transfers.get(k), "Робот біля деталі (" + ROUTES[k][0] + ")");
        }
        return m;
    }

    static int robotsBusy(Collector s, int variant) {
        int free = 0;
        if (variant == 1) {
            for (int k = 1; k <= 3; k++) {
                free += s.mark("Робот " + k + " вільний");
            }
        } else {
            for (String loc : POSITION.keySet()) {
                free += s.mark("Вільний робот: " + loc);
            }
        }
        return 3 - free;
    }

    /** Кількість маркерів у переходах, назва яких починається з prefix і закінчується suffix. */
    static int inside(Collector s, String prefix, String suffix) {
        int n = 0;
        for (var t : s.model().transitions()) {
            if (t.getName().startsWith(prefix) && t.getName().endsWith(suffix)) {
                n += t.getBuffer();
            }
        }
        return n;
    }

    static int inside(Collector s, String prefix) {
        return inside(s, prefix, "");
    }

    /** Робот або деталь на шляху route: від прибуття робота до деталі до вивільнення деталі. */
    static int onRoute(Collector s, String route) {
        int n = 0;
        for (String stage : new String[]{"Робот біля деталі", "Деталь захоплена", "Деталь доставлена"}) {
            n += s.mark(stage + " (" + route + ")");
        }
        for (String stage : new String[]{"Захоплення", "Переміщення", "Вивільнення"}) {
            n += s.buffer(stage + " (" + route + ")");
        }
        return n;
    }

    /** Переходи призначення робота на шлях route (у них маркер перебуває під час виклику). */
    static int assigning(Collector s, int variant, int k) {
        return variant == 1 ? s.buffer("Призначення робота " + k) : inside(s, "Виклик", ROUTES[k - 1][0]);
    }

    /** P-інваріанти роботів і місць на верстатах та збереження деталей; перевіряються після кожної події. */
    static void checkInvariants(Collector s, int variant) {
        if (variant == 1) {
            for (int k = 1; k <= 3; k++) {
                int idle = s.mark("Робот " + k + " вільний") + s.mark("Робот " + k + " повертається")
                        + s.buffer("Повернення робота " + k);
                int robot = idle + assigning(s, 1, k) + onRoute(s, ROUTES[k - 1][0]);
                Checks.check(robot == 1, "робот " + k);
            }
        } else {
            int robots = inside(s, "Виклик");
            for (String loc : POSITION.keySet()) {
                robots += s.mark("Вільний робот: " + loc);
            }
            for (String[] r : ROUTES) {
                robots += onRoute(s, r[0]);
            }
            Checks.check(robots == 3, "роботи");
        }
        for (int i = 1; i <= 2; i++) {
            String m = "В" + i;
            int onMachine = s.mark("Деталь на " + m) + s.buffer("Обробка на " + m)
                    + s.mark("Оброблена на " + m);
            int beforeGrab = s.mark("Робот біля деталі (" + ROUTES[i][0] + ")")
                    + s.buffer("Захоплення (" + ROUTES[i][0] + ")") + assigning(s, variant, i + 1);
            int reserved = assigning(s, variant, i) + onRoute(s, ROUTES[i - 1][0]);
            int free = s.mark("Вільні місця " + m);
            Checks.check(free + reserved + onMachine + beforeGrab == SLOTS, "місця " + m);
        }
        int parts = s.mark("Деталь на вході") + s.mark("Склад");
        for (int i = 1; i <= 2; i++) {
            String m = "В" + i;
            parts += s.mark("Деталь на " + m) + s.buffer("Обробка на " + m) + s.mark("Оброблена на " + m);
        }
        for (int k = 1; k <= 3; k++) {
            parts += onRoute(s, ROUTES[k - 1][0]) + assigning(s, variant, k);
        }
        Checks.check(parts == s.fired("Надходження"), "збереження деталей");
        Checks.check(s.tracksConsistently(), "мітки маркерів");
    }

    static Map<String, Object> run(int variant, double arrival, Collector s) {
        List<Double> times = new ArrayList<>();
        s.onOut("Вивільнення (В2–склад)", (t, born) -> {
            if (born >= WARMUP && born <= TIME - TAIL) {
                times.add(t - born);
            }
        });
        Collector.Watch busy = s.watch(c -> robotsBusy(c, variant));
        s.run(TIME);
        double hour = s.period() / 3600;
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("throughput", s.fired("Вивільнення (В2–склад)") / hour);
        r.put("arrivals", arrival > 0 ? s.fired("Надходження") / hour : null);
        r.put("time_in_system", times.isEmpty() ? null : Minibus.mean(times));
        r.put("input_queue", s.meanMark("Деталь на вході"));
        r.put("robots_busy", busy.mean());
        r.put("m1_slots", SLOTS - s.meanMark("Вільні місця В1"));
        r.put("m2_slots", SLOTS - s.meanMark("Вільні місця В2"));
        r.put("m1_processing", s.meanBuffer("Обробка на В1"));
        r.put("m2_processing", s.meanBuffer("Обробка на В2"));
        r.put("m1_done_waiting", s.meanMark("Оброблена на В1"));
        r.put("m2_done_waiting", s.meanMark("Оброблена на В2"));
        double idle = 0;
        Map<String, Double> calls = new LinkedHashMap<>();
        for (var t : s.model().transitions()) {
            String name = t.getName();
            if (name.startsWith("Виклик") || name.startsWith("Повернення")) {
                idle += s.meanBuffer(name);
            }
            if (name.startsWith("Виклик")) {
                calls.put(name, (double) s.fired(name));
            }
        }
        r.put("idle_moves", idle);
        if (variant == 1) {
            List<Double> load = new ArrayList<>();
            for (int k = 1; k <= 3; k++) {
                load.add(1 - s.meanMark("Робот " + k + " вільний"));
            }
            r.put("robot_load", load);
        } else {
            r.put("calls", calls);
        }
        return r;
    }

    record Series(Map<String, Stats> stats, List<Map<String, Object>> runs) {
    }

    static Series series(int variant, double arrival, List<String> keys) {
        List<Map<String, Object>> runs = new ArrayList<>();
        for (int i = 0; i < RUNS; i++) {
            runs.add(run(variant, arrival, new Collector(model(variant, arrival), WARMUP)));
        }
        Map<String, Stats> stats = new LinkedHashMap<>();
        for (String k : keys) {
            if (runs.getFirst().get(k) != null) {
                stats.put(k, Stats.of(runs.stream().map(r -> (Double) r.get(k)).toList()));
            }
        }
        return new Series(stats, runs);
    }

    private static final Pattern FAR_CALL = Pattern.compile("Виклик робота: (\\S+) → ([^,\\n]+)");

    static Map<String, Object> main(Output out) {
        out.println("ЗАВДАННЯ 2. Гнучка виробнича система: Петрі-об'єктна модель (PetriObjModel.go)");
        out.printf("Надходження exp(%.0f) с, захоплення/вивільнення %s с, В1 N(60; 10) с, В2 exp(100) с, "
                + "по %d місця на верстаті; ходи вхід–В1 6 с, В1–В2 7 с, В2–склад 5 с%n%n", ARRIVAL, HANDLE,
                SLOTS);
        Map<String, Object> structure = new LinkedHashMap<>();
        for (int variant = 1; variant <= 2; variant++) {
            ObjectModel m = model(variant, ARRIVAL);
            m.build();
            out.printf("Варіант %d: %d Петрі-об'єктів, %d об'єднань позицій; мережа моделі – %d позицій, "
                    + "%d переходів%n", variant, m.objects().size(), m.links().size(), m.places().size(),
                    m.transitions().size());
            structure.put(Integer.toString(variant),
                    Json.obj("objects", m.objects().size(), "links", m.links().size()));
        }
        out.println();
        Map<String, String> traces = new LinkedHashMap<>();
        for (int variant = 1; variant <= 2; variant++) {
            out.printf("Трасування перших подій, варіант %d:%n%n", variant);
            StringBuilder sb = new StringBuilder();
            Collector traced = new Collector(model(variant, ARRIVAL), 0);
            traced.trace(10, s -> {
                out.println(s);
                sb.append(s).append('\n');
            });
            traced.run(300);
            traces.put(Integer.toString(variant), sb.toString());
        }
        // перший виклик робота, що стоїть не на початку шляху (холостий хід)
        List<String> states = new ArrayList<>();
        Collector farTrace = new Collector(model(2, ARRIVAL), 0);
        farTrace.trace(Integer.MAX_VALUE, states::add);
        farTrace.run(3000);
        String far = "";
        for (int i = 1; i < states.size() && far.isEmpty(); i++) {
            String inputs = states.get(i).lines().filter(l -> l.startsWith("  вхід:")).findFirst().orElse("");
            Matcher mt = FAR_CALL.matcher(inputs);
            while (mt.find() && far.isEmpty()) {
                if (!mt.group(2).startsWith(mt.group(1))) {
                    far = states.get(i - 1) + "\n" + states.get(i);
                }
            }
        }
        out.println("Варіант 2, перший виклик робота, що стоїть не на початку шляху (холостий хід):\n");
        out.println(far);

        Map<String, Object> checksDone = new LinkedHashMap<>();
        for (int variant = 1; variant <= 2; variant++) {
            int v = variant;
            Collector checked = new Collector(model(variant, ARRIVAL), 0);
            long[] n = {0};
            checked.afterEvent(s -> {
                checkInvariants(s, v);
                n[0]++;
            });
            checked.run(TIME);
            out.printf("Перевірка інваріантів, варіант %d: %d подій, порушень немає%n", variant, n[0]);
            checksDone.put(Integer.toString(variant), n[0]);
        }

        List<String> keys = new ArrayList<>();
        for (String[] mtr : METRICS) {
            keys.add(mtr[0]);
        }
        List<String> baseKeys = new ArrayList<>(keys);
        baseKeys.add("arrivals");
        Map<String, Map<String, Object>> base = new LinkedHashMap<>();
        Map<String, Map<String, Stats>> capacity = new LinkedHashMap<>();
        Map<String, Map<String, Object>> sweep = new LinkedHashMap<>();
        for (int variant = 1; variant <= 2; variant++) {
            String v = Integer.toString(variant);
            Series s = series(variant, ARRIVAL, baseKeys);
            Map<String, Object> b = new LinkedHashMap<>(s.stats());
            if (variant == 1) {
                List<Stats> load = new ArrayList<>();
                for (int k = 0; k < 3; k++) {
                    int kk = k;
                    load.add(Stats.of(s.runs().stream().map(r -> ((List<?>) r.get("robot_load")).get(kk))
                            .map(x -> (Double) x).toList()));
                }
                b.put("robot_load", load);
            } else {
                Map<String, Double> calls = new LinkedHashMap<>();
                for (Map<String, Object> r : s.runs()) {
                    ((Map<?, ?>) r.get("calls"))
                            .forEach((k, c) -> calls.merge((String) k, (Double) c / RUNS, Double::sum));
                }
                b.put("calls", calls);
            }
            base.put(v, b);
            capacity.put(v, series(variant, 0, keys).stats());
            Map<String, Object> sw = new LinkedHashMap<>();
            for (double a : INTERVALS) {
                sw.put(String.format(Locale.ROOT, "%.1f", a),
                        series(variant, a, List.of("throughput", "time_in_system", "input_queue")).stats());
            }
            sweep.put(v, sw);
        }

        out.printf("%nРЕЗУЛЬТАТИ при надходженні кожні %.0f с (%d прогонів по %.0f с, розгін %.0f с, 95 %% "
                + "довірчий інтервал):%n", ARRIVAL, RUNS, TIME, WARMUP);
        out.printf("  %-40s %20s %20s%n", "показник", "варіант 1", "варіант 2");
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[]{"arrivals", "інтенсивність надходження, деталей/год"});
        rows.addAll(List.of(METRICS));
        for (String[] mtr : rows) {
            out.printf("  %-40s %20s %20s%n", mtr[1], ((Stats) base.get("1").get(mtr[0])).format(2),
                    ((Stats) base.get("2").get(mtr[0])).format(2));
        }
        @SuppressWarnings("unchecked")
        List<Stats> load = (List<Stats>) base.get("1").get("robot_load");
        out.println("  завантаження роботів 1–3 (варіант 1): " + String.join(", ",
                load.stream().map(x -> x.format(3)).toList()));

        out.println("\nНеобмежене надходження деталей (пропускна здатність системи):");
        for (String[] mtr : METRICS) {
            if (capacity.get("1").containsKey(mtr[0])) {
                out.printf("  %-40s %20s %20s%n", mtr[1], capacity.get("1").get(mtr[0]).format(2),
                        capacity.get("2").get(mtr[0]).format(2));
            }
        }
        out.println("\nЗалежність від середнього інтервалу надходження:");
        out.printf("  %11s %17s %17s %18s %18s%n", "інтервал, с", "вар. 1, дет./год", "вар. 2, дет./год",
                "вар. 1: час, с", "вар. 2: час, с");
        for (double a : INTERVALS) {
            String key = String.format(Locale.ROOT, "%.1f", a);
            @SuppressWarnings("unchecked")
            Map<String, Stats> s1 = (Map<String, Stats>) sweep.get("1").get(key);
            @SuppressWarnings("unchecked")
            Map<String, Stats> s2 = (Map<String, Stats>) sweep.get("2").get(key);
            out.printf("  %11.0f %17s %17s %18s %18s%n", a, s1.get("throughput").format(2),
                    s2.get("throughput").format(2), s1.get("time_in_system").format(0),
                    s2.get("time_in_system").format(0));
        }

        @SuppressWarnings("unchecked")
        Map<String, Double> calls = (Map<String, Double>) base.get("2").get("calls");
        double total = calls.values().stream().mapToDouble(x -> x).sum();
        out.println("\nВаріант 2, частка викликів робота за місцем, де він стояв:");
        calls.entrySet().stream().sorted((x, y) -> Double.compare(y.getValue(), x.getValue()))
                .filter(e -> e.getValue() > 0)
                .forEach(e -> out.printf("  %-32s %.4f%n", e.getKey(), e.getValue() / total));
        Map<String, Double> idle = new LinkedHashMap<>();
        for (String[] r : ROUTES) {
            double n = 0;
            double d = 0;
            for (String loc : POSITION.keySet()) {
                double c = calls.getOrDefault("Виклик робота: " + loc + " → " + r[0], 0.0);
                n += c;
                d += c * distance(loc, r[1]);
            }
            idle.put(r[0], d / n);
        }
        List<String> idleText = new ArrayList<>();
        idle.forEach((route, d) -> idleText.add(String.format(Locale.ROOT, "%s %.2f", route, d)));
        out.println("  середній холостий хід до деталі, с: " + String.join(", ", idleText));

        double x = capacity.get("1").get("throughput").mean() / 3600;
        out.printf("%nПеревірка (варіант 1, необмежене надходження): за пропускної здатності X = %.2f "
                + "дет./год%n", x * 3600);
        out.printf("  в обробці на В1 X·60 = %.3f (імітація %.3f), на В2 X·100 = %.3f (імітація %.3f)%n",
                x * 60, capacity.get("1").get("m1_processing").mean(), x * 100,
                capacity.get("1").get("m2_processing").mean());
        double cycle = 0;
        for (String[] r : ROUTES) {
            cycle += 2 * 8 + 2 * distance(r[1], r[2]);
        }
        out.printf("  зайняті роботи X·(28 + 30 + 26) = %.3f (імітація %.3f)%n", x * cycle,
                capacity.get("1").get("robots_busy").mean());

        return Json.obj("structure", structure, "traces", traces, "far_call", far, "check_events", checksDone,
                "base", base, "capacity", capacity, "sweep", sweep, "idle", idle, "runs", RUNS,
                "metrics", java.util.Arrays.stream(METRICS).map(List::of).toList());
    }
}
