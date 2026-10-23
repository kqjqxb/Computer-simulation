package lab7;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Завдання 3: Петрі-об'єктна модель маршруту з двома мікроавтобусами (задача 3 практикуму 5). Об'єкти:
 * два генератори пасажирів, дві зупинки, мікроавтобуси A і B. Пасажир сідає в B, лише коли на зупинці
 * немає A: об'єкт «Мікроавтобус A» має вищий пріоритет, тому першим виконує вхід маркерів у переходи.
 */
public final class Minibus {

    static final Delay ARRIVAL = Delay.unif(0.5, 0.2);
    static final Delay UNLOAD = Delay.unif(5, 1);
    static final Map<String, Delay> TRIP = Map.of("A", Delay.unif(20, 5), "B", Delay.unif(30, 5));
    static final Map<String, Integer> START = Map.of("A", 1, "B", 2);
    static final String[] BUSES = {"A", "B"};
    static final int QUEUE_LIMIT = 30;
    static final int FARE = 20;
    static final double DAY = 600;
    static final int DAYS = 50;
    static final int MAX_SEATS = 25;

    private Minibus() {
    }

    static ObjectModel model(int n) {
        ObjectModel m = new ObjectModel("Маршрут мікроавтобусів, n = " + n);
        Bus a = m.add(new Bus("A", n, START.get("A"), TRIP.get("A"), UNLOAD, 6, 4), 300, 0);
        Bus b = m.add(new Bus("B", n, START.get("B"), TRIP.get("B"), UNLOAD, 5, 3), 300, 720);
        a.priority(2);
        b.priority(1);
        for (int s = 1; s <= 2; s++) {
            Generator g = m.add(new Generator("Генератор З" + s, "Генератор З" + s, "Прихід на З" + s,
                    "Пасажир на З" + s, ARRIVAL), s == 1 ? -560 : 1820, 40);
            Stop stop = m.add(new Stop(s, QUEUE_LIMIT), s == 1 ? -300 : 1820, 360);
            m.share(stop, "Пасажир на З" + s, "Черга на З" + s, "Місця в черзі З" + s);
        }
        m.share(a, "Перевезені пасажири");
        return m;
    }

    /** Показники одного дня (прогону). */
    record Day(double meanWait, double waitQueued, double queue, int boarded, int lost, int joined,
               Map<String, Integer> trips) {
        int revenue() {
            return FARE * (boarded - lost);
        }
    }

    static Day run(int n, double time, Collector s) {
        List<Double> waits = new ArrayList<>();
        List<Double> queued = new ArrayList<>();
        for (int st = 1; st <= 2; st++) {
            for (String b : BUSES) {
                s.onOut("Посадка з черги в " + b + " на З" + st, (t, born) -> {
                    waits.add(t - born);
                    queued.add(t - born);
                });
                s.onOut("Посадка в " + b + " на З" + st, (t, born) -> waits.add(0.0));
            }
        }
        s.run(time);
        int lost = 0;
        int joined = 0;
        double queue = 0;
        for (int st = 1; st <= 2; st++) {
            lost += s.fired("Відмова на З" + st);
            joined += s.fired("Стати в чергу на З" + st);
            queue += s.meanMark("Черга на З" + st) / 2;
        }
        Map<String, Integer> trips = new LinkedHashMap<>();
        for (String b : BUSES) {
            trips.put(b, s.entered("Рейс " + b + " З1→З2") + s.entered("Рейс " + b + " З2→З1"));
        }
        return new Day(mean(waits), mean(queued), queue, waits.size(), lost, joined, trips);
    }

    static double mean(List<Double> v) {
        return v.stream().mapToDouble(x -> x).average().orElse(0);
    }

    /** Інваріанти після кожної події: автобус в одному місці, місця автобуса, черга, баланс пасажирів. */
    static void checkInvariants(Collector s, int n) {
        for (String b : BUSES) {
            int where = 0;
            for (int st = 1; st <= 2; st++) {
                String at = " на З" + st;
                where += s.mark(b + at) + s.mark(b + " прибув" + at) + s.buffer("Висадка " + b + at)
                        + s.buffer("Рейс " + b + " З" + st + "→З" + (3 - st));
                int seats = s.mark("Вільні місця " + b + at) + s.mark("Пасажири в " + b + at)
                        + s.buffer("Посадка з черги в " + b + at) + s.buffer("Посадка в " + b + at);
                Checks.check(seats == n * s.mark(b + at), "місця " + b);
            }
            Checks.check(where == 1, "автобус " + b);
        }
        for (int st = 1; st <= 2; st++) {
            int inQueue = s.mark("Черга на З" + st);
            int boarding = 0;
            int boarded = 0;
            for (String b : BUSES) {
                String at = " в " + b + " на З" + st;
                boarding += s.buffer("Посадка з черги" + at);
                boarded += s.entered("Посадка з черги" + at) + s.entered("Посадка" + at);
                if (inQueue > 0) {
                    Checks.check(s.mark("Вільні місця " + b + " на З" + st) == 0, "черга при вільних місцях");
                }
            }
            int joining = s.buffer("Стати в чергу на З" + st);
            Checks.check(inQueue + s.mark("Місця в черзі З" + st) + joining + boarding == QUEUE_LIMIT,
                    "місця в черзі");
            int passengers = s.mark("Пасажир на З" + st) + joining + inQueue + boarded
                    + s.entered("Відмова на З" + st);
            Checks.check(s.fired("Прихід на З" + st) == passengers, "баланс пасажирів");
        }
        Checks.check(s.tracksConsistently(), "мітки маркерів");
    }

    static Map<String, Object> main(Output out) {
        out.println("ЗАВДАННЯ 3. Маршрут мікроавтобусів: Петрі-об'єктна модель (PetriObjModel.go)");
        out.printf("Пасажири кожні %s хв на кожну з двох кінцевих зупинок, черга до %d, рейс A %s хв, "
                + "B %s хв, висадка %s хв, день %.0f хв%n%n", ARRIVAL, QUEUE_LIMIT, TRIP.get("A"),
                TRIP.get("B"), UNLOAD, DAY);
        ObjectModel shown = model(1);
        shown.build();
        out.printf("%d Петрі-об'єктів, %d об'єднань позицій; мережа моделі – %d позицій, %d переходів%n%n",
                shown.objects().size(), shown.links().size(), shown.places().size(),
                shown.transitions().size());
        out.println("Трасування перших подій (n = 3):\n");
        StringBuilder trace = new StringBuilder();
        Collector traced = new Collector(model(3), 0);
        traced.trace(14, st -> {
            out.println(st);
            trace.append(st).append('\n');
        });
        traced.run(30);

        Collector checked = new Collector(model(10), 0);
        long[] events = {0};
        checked.afterEvent(st -> {
            checkInvariants(st, 10);
            events[0]++;
        });
        checked.run(DAY);
        out.printf("Перевірка інваріантів (n = 10): %d подій, порушень немає%n", events[0]);

        // довгий прогін для перевірки формули Літтла і пропускної здатності
        double longTime = 100 * DAY;
        Day lr = run(MAX_SEATS, longTime, new Collector(model(MAX_SEATS), 0));
        double lam = lr.joined() / longTime / 2;
        out.printf("Довгий прогін %.0f хв, n = %d: середня черга на зупинці %.4f, λ_черги · W_черги = "
                + "%.4f · %.4f = %.4f%n", longTime, MAX_SEATS, lr.queue(), lam, lr.waitQueued(),
                lam * lr.waitQueued());
        double cap = MAX_SEATS * (1.0 / (2 * 20 + 2 * 5) + 1.0 / (2 * 30 + 2 * 5));
        out.printf("Перевезено за хв в одному напрямку %.4f; оцінка при постійній черзі n·(1/50 + 1/70) = "
                + "%.4f; рейсів A %d, B %d (оцінка %.0f і %.0f)%n", lr.boarded() / longTime / 2, cap,
                lr.trips().get("A"), lr.trips().get("B"), longTime / 25, longTime / 35);

        List<Map<String, Object>> sweep = new ArrayList<>();
        out.printf("%nРЕЗУЛЬТАТИ (%d днів для кожного n, 95 %% довірчий інтервал):%n", DAYS);
        out.printf("  %2s %18s %22s %10s %8s %20s%n", "n", "очікування, хв", "серед тих, хто чекав",
                "перевезено", "пішли", "виручка, грн");
        for (int n = 1; n <= MAX_SEATS; n++) {
            List<Day> days = new ArrayList<>();
            for (int d = 0; d < DAYS; d++) {
                days.add(run(n, DAY, new Collector(model(n), 0)));
            }
            Map<String, Object> row = Json.obj("n", n,
                    "wait", Stats.of(days.stream().map(Day::meanWait).toList()),
                    "wait_queued", Stats.of(days.stream().map(Day::waitQueued).toList()),
                    "queue", Stats.of(days.stream().map(Day::queue).toList()),
                    "boarded", Stats.of(days.stream().map(d -> (double) d.boarded()).toList()),
                    "lost", Stats.of(days.stream().map(d -> (double) d.lost()).toList()),
                    "revenue", Stats.of(days.stream().map(d -> (double) d.revenue()).toList()),
                    "income", Stats.of(days.stream().map(d -> (double) FARE * d.boarded()).toList()),
                    "loss", Stats.of(days.stream().map(d -> (double) FARE * d.lost()).toList()),
                    "trips_A", days.stream().mapToInt(d -> d.trips().get("A")).average().orElse(0),
                    "trips_B", days.stream().mapToInt(d -> d.trips().get("B")).average().orElse(0));
            sweep.add(row);
            out.printf("  %2d %18s %22s %10.1f %8.1f %20s%n", n, ((Stats) row.get("wait")).format(2),
                    ((Stats) row.get("wait_queued")).format(2), ((Stats) row.get("boarded")).mean(),
                    ((Stats) row.get("lost")).mean(), ((Stats) row.get("revenue")).format(0));
        }
        Map<String, Object> best = sweep.stream()
                .min((x, y) -> Double.compare(((Stats) x.get("wait")).mean(), ((Stats) y.get("wait")).mean()))
                .orElseThrow();
        out.printf("%nНайменший середній час очікування при n = %d: %s хв%n", best.get("n"),
                ((Stats) best.get("wait")).format(3));
        out.printf("Виручка за день при n = %d: дохід %.0f, втрати %.0f, виручка %s грн%n", best.get("n"),
                ((Stats) best.get("income")).mean(), ((Stats) best.get("loss")).mean(),
                ((Stats) best.get("revenue")).format(0));

        int objects = shown.objects().size();
        return Json.obj("structure", Json.obj("objects", objects, "links", shown.links().size()),
                "trace", trace.toString(), "sweep", sweep, "best", best.get("n"), "check_events", events[0],
                "little", Json.obj("queue", lr.queue(), "rate", lam, "wait", lr.waitQueued()),
                "capacity", Json.obj("sim", lr.boarded() / longTime / 2, "estimate", cap, "trips", lr.trips(),
                        "time", longTime), "runs", DAYS);
    }
}
