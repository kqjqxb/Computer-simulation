package lab6;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import ua.stetsenkoinna.petriobj.PetriP;
import ua.stetsenkoinna.petriobj.PetriT;

/** Завдання 1: конвеєрна система з п'ятьма обслуговуючими пристроями (задача 1 практикуму 5). */
public final class Conveyor {

    static final int DEVICES = 5;
    static final double ARRIVAL = 0.25;
    static final double SERVICE = 1.0;
    static final double STEP = 1.0;
    static final double BACK = 5.0;
    static final double TIME = 10000;
    static final double WARMUP = 1000;
    static final int RUNS = 20;

    private Conveyor() {
    }

    static NetBuilder net() {
        NetBuilder b = new NetBuilder("Conveyor");
        PetriP generator = b.place("Генератор", 1, 40, 420);
        PetriP[] part = new PetriP[DEVICES + 1];
        PetriP[] free = new PetriP[DEVICES + 1];
        for (int i = 1; i <= DEVICES; i++) {
            part[i] = b.place("Деталь біля П" + i, 0, x(i), 420);
            free[i] = b.place("П" + i + " вільний", 1, x(i) + 210, 270);
        }
        PetriP done = b.place("Оброблені деталі", 0, 800, 50);
        PetriT arrival = b.transition("Надходження", ARRIVAL, 150, 420);
        b.arc(generator, arrival);
        b.arc(arrival, generator);
        b.arc(arrival, part[1]);
        for (int i = 1; i <= DEVICES; i++) {
            PetriT service = b.expTransition("Обробка П" + i, SERVICE, x(i) + 100, 270);
            service.setPriority(1);
            b.arc(part[i], service);
            b.arc(free[i], service);
            b.arc(service, free[i]);
            b.arc(service, done);
            PetriT move = i < DEVICES
                    ? b.transition("Рух до П" + (i + 1), STEP, x(i) + 120, 420)
                    : b.transition("Повернення до П1", BACK, 760, 760);
            b.arc(part[i], move);
            b.arc(move, part[i < DEVICES ? i + 1 : 1]);
        }
        return b;
    }

    private static double x(int device) {
        return 280 + 240 * (device - 1);
    }

    /** Деталі в системі: біля пристроїв, на обробці та на конвеєрі (у переходах руху й повернення). */
    static int inSystem(Simulation s) {
        int n = 0;
        for (int i = 1; i <= DEVICES; i++) {
            n += s.mark("Деталь біля П" + i);
        }
        for (PetriT t : s.transitions()) {
            if (!t.getName().equals("Надходження")) {
                n += t.getBuffer();
            }
        }
        return n;
    }

    static int busyDevices(Simulation s) {
        int busy = 0;
        for (int i = 1; i <= DEVICES; i++) {
            busy += 1 - s.mark("П" + i + " вільний");
        }
        return busy;
    }

    /** Показники одного прогону. */
    record Run(double meanTime, double[] load, double[] share, double busy, Map<Double, Double> busyDist,
               double throughput, double returnsPerPart, double inSystem) {
    }

    static Run run(Simulation s) {
        Simulation.Watch busy = s.watch(Conveyor::busyDevices);
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
        for (PetriT t : s.transitions()) {
            if (!t.getName().equals("Надходження")) {
                inSystem += s.meanBuffer(t.getName());
            }
        }
        double throughput = served / s.period();
        // середній час перебування за формулою Літтла: W = L / λ
        return new Run(inSystem / throughput, load, share, busy.mean(), busy.distribution(), throughput,
                s.fired("Повернення до П1") / served, inSystem);
    }

    static Map<String, Object> main(Output out) {
        out.println("ЗАВДАННЯ 1. Конвеєрна система (PetriSim)");
        out.printf("Надходження кожні %.2f хв, обробка exp(%.1f), рух між пристроями %.1f хв, "
                + "повернення %.1f хв, %d пристроїв%n%n", ARRIVAL, SERVICE, STEP, BACK, DEVICES);
        out.println("Трасування перших подій:\n");
        Simulation traced = new Simulation(net().build(), 0);
        traced.trace(12, out::println);
        traced.run(5);

        Simulation checked = new Simulation(net().build(), 0);
        int[] maxInSystem = {0};
        long[] checks = {0};
        checked.afterEvent(s -> {
            for (int i = 1; i <= DEVICES; i++) {
                Checks.check(s.mark("П" + i + " вільний") + s.buffer("Обробка П" + i) == 1, "пристрій П" + i);
            }
            Checks.check(s.mark("Генератор") + s.buffer("Надходження") == 1, "генератор");
            int created = s.fired("Надходження");
            Checks.check(created == s.mark("Оброблені деталі") + inSystem(s), "збереження деталей");
            maxInSystem[0] = Math.max(maxInSystem[0], inSystem(s));
            checks[0]++;
        });
        checked.run(TIME);
        double libraryDiff = Checks.libraryDifference(checked);
        out.printf("Перевірка інваріантів після кожної з %d подій: порушень немає; найбільше "
                + "деталей у системі %d%n",
                checks[0], maxInSystem[0]);
        out.printf("Середні значення бібліотеки (PetriP.getMean, PetriT.getMean) і власні статистики прогону "
                + "без розгону: найбільша розбіжність %.2e%n", libraryDiff);

        List<Run> runs = new ArrayList<>();
        for (int r = 0; r < RUNS; r++) {
            runs.add(run(new Simulation(net().build(), WARMUP)));
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

        out.printf("%nРЕЗУЛЬТАТИ (%d прогонів по %.0f хв, розгін %.0f хв, 95 %% довірчий "
                + "інтервал):%n", RUNS, TIME,
                WARMUP);
        table.forEach((k, v) -> out.printf("  %-40s %s%n", k, v));
        Map<String, Double> dist = new LinkedHashMap<>();
        StringBuilder distText = new StringBuilder();
        for (int k = 0; k <= DEVICES; k++) {
            double key = k;
            double p = runs.stream().mapToDouble(r -> r.busyDist().getOrDefault(key, 0.0))
                    .average().orElse(0);
            dist.put(Integer.toString(k), p);
            distText.append(String.format(java.util.Locale.ROOT, "%sP(%d) = %.4f", k > 0 ? ", " : "", k, p));
        }
        out.println("Розподіл кількості зайнятих пристроїв: " + distText);
        double[] share = new double[DEVICES];
        StringBuilder shareText = new StringBuilder();
        for (int i = 0; i < DEVICES; i++) {
            int k = i;
            share[i] = runs.stream().mapToDouble(r -> r.share()[k]).average().orElse(0);
            shareText.append(String.format(java.util.Locale.ROOT, "%sП%d %.4f", i > 0 ? ", " : "", i + 1,
                    share[i]));
        }
        out.println("Частка деталей, оброблених на пристрої: " + shareText);
        double sumLoad = 0;
        for (int i = 1; i <= DEVICES; i++) {
            sumLoad += table.get("завантаження П" + i).mean();
        }
        double lambda = table.get("пропускна здатність, деталей/хв").mean();
        out.printf("%nПеревірка: пропускна здатність %.4f при інтенсивності надходження %.1f; "
                + "сума завантажень %.4f, λ·E[S] = %.1f%n", lambda, 1 / ARRIVAL, sumLoad, SERVICE / ARRIVAL);
        out.printf("Сума завантажень дорівнює середній кількості зайнятих пристроїв: %.4f і %.4f%n", sumLoad,
                table.get("середня кількість зайнятих пристроїв").mean());

        return Json.obj("table", table, "busy_distribution", dist, "share", share, "check_events", checks[0],
                "max_in_system", maxInSystem[0], "library_difference", libraryDiff, "runs", RUNS,
                "mean_times", runs.stream().map(Run::meanTime).toList());
    }

    static Stats stats(List<Run> runs, java.util.function.ToDoubleFunction<Run> f) {
        return Stats.of(runs.stream().map(r -> f.applyAsDouble(r)).toList());
    }
}
