package lab7;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.ToDoubleFunction;

/**
 * Завдання 4: Петрі-об'єктна модель конвеєрної системи (задача 1 практикуму 5), для якої в звіті
 * записано математичні рівняння. Об'єкти: генератор деталей і п'ять однакових об'єктів «Пристрій».
 */
public final class Conveyor {

    static final int DEVICES = 5;
    static final double ARRIVAL = 0.25;
    static final double SERVICE = 1.0;
    static final double STEP = 1.0;
    static final double BACK = 5.0;
    static final double TIME = 10000;
    static final double WARMUP = 1000;
    static final double TAIL = 300;
    static final int RUNS = 20;

    private Conveyor() {
    }

    /**
     * returnFirst – об'єкт «Пристрій П5» має вищий пріоритет, ніж генератор: якщо деталь, що повертається,
     * і нова деталь з'являються біля П1 одночасно, першою вихід маркера виконує П5 і вільний П1 займає
     * деталь, що повертається (так упорядковано одночасні події в мережі практикуму 5).
     */
    static ObjectModel model(boolean returnFirst) {
        ObjectModel m = new ObjectModel("Конвеєрна система");
        Generator g = m.add(new Generator("Генератор", "Генератор", "Надходження", "Деталь біля П1",
                Delay.fixed(ARRIVAL)), 0, 0);
        List<Device> devices = new ArrayList<>();
        for (int i = 1; i <= DEVICES; i++) {
            boolean last = i == DEVICES;
            String move = last ? "Повернення до П1" : "Рух до П" + (i + 1);
            String next = "Деталь біля П" + (last ? 1 : i + 1);
            devices.add(m.add(new Device(i, Delay.exp(SERVICE), move, last ? BACK : STEP, next),
                    290 + 380 * (i - 1), 0));
        }
        for (Device d : devices) {
            m.share(d, d.name().replace("Пристрій", "Деталь біля"));
        }
        m.share(devices.getFirst(), "Оброблені деталі");
        m.drawAt("Повернення до П1", 290 + 380 * (DEVICES - 1) + 120, 300);
        if (returnFirst) {
            devices.getLast().priority(1);
        }
        return m;
    }

    static int busyDevices(Collector s) {
        int busy = 0;
        for (int i = 1; i <= DEVICES; i++) {
            busy += 1 - s.mark("П" + i + " вільний");
        }
        return busy;
    }

    /** Деталі в системі: біля пристроїв, на обробці та на конвеєрі (у переходах руху й повернення). */
    static int inSystem(Collector s) {
        int n = 0;
        for (int i = 1; i <= DEVICES; i++) {
            n += s.mark("Деталь біля П" + i);
        }
        for (var t : s.model().transitions()) {
            if (!t.getName().equals("Надходження")) {
                n += t.getBuffer();
            }
        }
        return n;
    }

    record Run(List<Double> times, double meanTime, double[] load, double[] share, double busy,
               Map<Double, Double> busyDist, double throughput, double returnsPerPart, double inSystem) {
    }

    static Run run(Collector s) {
        List<Double> times = new ArrayList<>();
        for (int i = 1; i <= DEVICES; i++) {
            s.onOut("Обробка П" + i, (t, born) -> {
                if (born >= WARMUP && born <= TIME - TAIL) {
                    times.add(t - born);
                }
            });
        }
        Collector.Watch busy = s.watch(Conveyor::busyDevices);
        s.run(TIME);
        double served = 0;
        double[] load = new double[DEVICES];
        double[] share = new double[DEVICES];
        for (int i = 1; i <= DEVICES; i++) {
            served += s.fired("Обробка П" + i);
            load[i - 1] = s.meanBuffer("Обробка П" + i);
        }
        double inSystem = 0;
        for (int i = 1; i <= DEVICES; i++) {
            share[i - 1] = s.fired("Обробка П" + i) / served;
            inSystem += s.meanMark("Деталь біля П" + i);
        }
        for (var t : s.model().transitions()) {
            if (!t.getName().equals("Надходження")) {
                inSystem += s.meanBuffer(t.getName());
            }
        }
        return new Run(times, mean(times), load, share, busy.mean(), busy.distribution(), served / s.period(),
                s.fired("Повернення до П1") / served, inSystem);
    }

    static double mean(List<Double> v) {
        return v.stream().mapToDouble(x -> x).average().orElse(0);
    }

    /** Розподіл часу перебування за деталями всіх прогонів (вибірка впорядкована). */
    static Map<String, Object> distribution(List<Run> runs) {
        List<Double> all = new ArrayList<>();
        runs.forEach(r -> all.addAll(r.times()));
        all.sort(null);
        double m = mean(all);
        double sd = Math.sqrt(all.stream().mapToDouble(x -> (x - m) * (x - m)).sum() / (all.size() - 1));
        return Json.obj("mean", m, "stdev", sd, "min", all.getFirst(), "median", quantile(all, 0.5),
                "p90", quantile(all, 0.9), "p95", quantile(all, 0.95), "max", all.getLast(),
                "count", all.size(), "survival", survival(all));
    }

    static Map<String, Object> main(Output out) {
        out.println("ЗАВДАННЯ 4. Конвеєрна система: Петрі-об'єктна модель (PetriObjModel.go)");
        out.printf("Надходження кожні %.2f хв, обробка exp(%.1f), рух між пристроями %.1f хв, "
                + "повернення %.1f хв, %d пристроїв%n", ARRIVAL, SERVICE, STEP, BACK, DEVICES);
        ObjectModel shown = model(true);
        shown.build();
        out.printf("%d Петрі-об'єктів, %d об'єднань позицій; мережа моделі – %d позицій, %d переходів%n%n",
                shown.objects().size(), shown.links().size(), shown.places().size(),
                shown.transitions().size());
        out.println("Трасування перших подій:\n");
        StringBuilder trace = new StringBuilder();
        Collector traced = new Collector(model(true), 0);
        traced.trace(12, s -> {
            out.println(s);
            trace.append(s).append('\n');
        });
        traced.run(5);

        Collector checked = new Collector(model(true), 0);
        int[] maxInSystem = {0};
        long[] checks = {0};
        checked.afterEvent(s -> {
            for (int i = 1; i <= DEVICES; i++) {
                Checks.check(s.mark("П" + i + " вільний") + s.buffer("Обробка П" + i) == 1, "пристрій П" + i);
            }
            Checks.check(s.mark("Генератор") + s.buffer("Надходження") == 1, "генератор");
            Checks.check(s.fired("Надходження") == s.mark("Оброблені деталі") + inSystem(s), "деталі");
            Checks.check(s.tracksConsistently(), "мітки маркерів");
            maxInSystem[0] = Math.max(maxInSystem[0], inSystem(s));
            checks[0]++;
        });
        checked.run(TIME);
        out.printf("Перевірка інваріантів після кожної з %d подій: порушень немає; найбільше деталей "
                + "у системі %d%n", checks[0], maxInSystem[0]);
        out.printf("Середні значення бібліотеки і власні статистики прогону без розгону: найбільша "
                + "розбіжність %.2e%n", checked.libraryDifference());

        List<Run> runs = new ArrayList<>();
        for (int r = 0; r < RUNS; r++) {
            runs.add(run(new Collector(model(true), WARMUP)));
        }
        Map<String, Stats> table = new LinkedHashMap<>();
        table.put("середній час перебування, хв", stats(runs, Run::meanTime));
        for (int i = 0; i < DEVICES; i++) {
            int k = i;
            table.put("завантаження П" + (i + 1), stats(runs, r -> r.load()[k]));
        }
        table.put("середня кількість зайнятих пристроїв", stats(runs, Run::busy));
        table.put("пропускна здатність, деталей/хв", stats(runs, Run::throughput));
        table.put("повернень до П1 на одну деталь", stats(runs, Run::returnsPerPart));
        table.put("середня кількість деталей у системі", stats(runs, Run::inSystem));
        out.printf("%nРЕЗУЛЬТАТИ (%d прогонів по %.0f хв, розгін %.0f хв, 95 %% довірчий інтервал):%n",
                RUNS, TIME, WARMUP);
        table.forEach((k, v) -> out.printf("  %-40s %s%n", k, v));

        Map<String, Object> times = distribution(runs);
        out.printf("Час перебування деталі (усі прогони): середній %.4f, ст. відхилення %.4f, "
                + "медіана %.4f, 90 %% – %.4f, 95 %% – %.4f, макс. %.4f; деталей %d%n", times.get("mean"),
                times.get("stdev"), times.get("median"), times.get("p90"), times.get("p95"), times.get("max"),
                times.get("count"));
        Map<String, Double> dist = new LinkedHashMap<>();
        List<String> distText = new ArrayList<>();
        for (int k = 0; k <= DEVICES; k++) {
            double key = k;
            double p = runs.stream().mapToDouble(r -> r.busyDist().getOrDefault(key, 0.0)).sum() / RUNS;
            dist.put(Integer.toString(k), p);
            distText.add(String.format(Locale.ROOT, "P(%d) = %.4f", k, p));
        }
        out.println("Розподіл кількості зайнятих пристроїв: " + String.join(", ", distText));
        double[] share = new double[DEVICES];
        List<String> shareText = new ArrayList<>();
        for (int i = 0; i < DEVICES; i++) {
            int k = i;
            share[i] = runs.stream().mapToDouble(r -> r.share()[k]).average().orElse(0);
            shareText.add(String.format(Locale.ROOT, "П%d %.4f", i + 1, share[i]));
        }
        out.println("Частка деталей, оброблених на пристрої: " + String.join(", ", shareText));
        double lambda = table.get("пропускна здатність, деталей/хв").mean();
        double w = table.get("середній час перебування, хв").mean();
        out.printf("Формула Літтла: λ·W = %.4f · %.4f = %.4f, середня кількість деталей у системі %.4f%n%n",
                lambda, w, lambda * w, table.get("середня кількість деталей у системі").mean());

        // однакові пріоритети об'єктів: одночасні виходи генератора і П5 виконуються у випадковому порядку
        List<Run> equal = new ArrayList<>();
        for (int r = 0; r < RUNS; r++) {
            equal.add(run(new Collector(model(false), WARMUP)));
        }
        Map<String, Object> order = new LinkedHashMap<>();
        for (boolean first : new boolean[]{true, false}) {
            List<Run> rs = first ? runs : equal;
            Map<String, Object> d = distribution(rs);
            Map<String, Object> row = Json.obj("mean_time", stats(rs, Run::meanTime),
                    "returns", stats(rs, Run::returnsPerPart), "load1", stats(rs, r -> r.load()[0]),
                    "in_system", stats(rs, Run::inSystem), "times", d);
            order.put(first ? "return_first" : "equal", row);
            out.printf("%s: час перебування %s (ст. відхилення %.2f, медіана %.2f, 95 %% – %.2f, "
                    + "макс. %.1f), повернень на деталь %s, завантаження П1 %s%n",
                    first ? "Пріоритет П5 вищий (як у ЛР5)" : "Однакові пріоритети об'єктів",
                    row.get("mean_time"), d.get("stdev"), d.get("median"), d.get("p95"), d.get("max"),
                    row.get("returns"), row.get("load1"));
        }

        return Json.obj("table", table, "order", order, "time_stats", times, "busy_distribution", dist,
                "share", share, "check_events", checks[0], "max_in_system", maxInSystem[0],
                "library_difference", checked.libraryDifference(), "trace", trace.toString(), "runs", RUNS,
                "structure", Json.obj("objects", shown.objects().size(), "links", shown.links().size()));
    }

    /** Частка деталей з часом перебування, більшим за t = 0, 2, 4, …, 300 хв (за впорядкованою вибіркою). */
    static List<Double> survival(List<Double> sorted) {
        List<Double> out = new ArrayList<>();
        int i = 0;
        for (int t = 0; t <= 300; t += 2) {
            while (i < sorted.size() && sorted.get(i) <= t) {
                i++;
            }
            out.add((double) (sorted.size() - i) / sorted.size());
        }
        return out;
    }

    static double quantile(List<Double> sorted, double q) {
        double pos = q * (sorted.size() - 1);
        int i = (int) pos;
        double f = pos - i;
        return i + 1 < sorted.size() ? sorted.get(i) * (1 - f) + sorted.get(i + 1) * f : sorted.get(i);
    }

    static Stats stats(List<Run> runs, ToDoubleFunction<Run> f) {
        return Stats.of(runs.stream().map(f::applyAsDouble).toList());
    }
}
