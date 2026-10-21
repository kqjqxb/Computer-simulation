package lab6;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToDoubleFunction;

import ua.stetsenkoinna.petriobj.PetriP;
import ua.stetsenkoinna.petriobj.PetriT;

/**
 * Завдання 3: багатопотокова обробка запитів на головному (С1) і допоміжному (С2) серверах зі
 * спільною базою даних. Одиниця часу – мілісекунда.
 */
public final class Servers {

    /** Параметри моделі; rate – інтенсивність надходження запитів, запитів/мс. */
    record Config(double rate, double pAux, int queue, int[] threads, int[] cores, double[] cpu,
                  int connections, double db, int routingPriority) {

        static Config base() {
            return new Config(0.2, 0.3, 1000, new int[] {32, 16}, new int[] {8, 4}, new double[] {40, 50}, 10,
                    20, 3);
        }

        Config withRate(double r) {
            return new Config(r, pAux, queue, threads, cores, cpu, connections, db, routingPriority);
        }

        Config withThreads(int main, int aux) {
            return new Config(rate, pAux, queue, new int[] {main, aux}, cores, cpu, connections, db,
                    routingPriority);
        }

        Config withRoutingPriority(int priority) {
            return new Config(rate, pAux, queue, threads, cores, cpu, connections, db, priority);
        }

        double serverRate(int s) {
            return rate * (s == 0 ? 1 - pAux : pAux);
        }
    }

    static final double TIME = 600_000;
    static final double WARMUP = 20_000;
    static final int RUNS = 20;
    static final double EXP_TIME = 300_000;
    static final int EXP_RUNS = 10;

    private Servers() {
    }

    static String sfx(int s) {
        return " С" + (s + 1);
    }

    static NetBuilder net(Config c) {
        NetBuilder b = new NetBuilder("Servers");
        PetriP generator = b.place("Генератор", 1, 40, 500);
        PetriP request = b.place("Запит", 0, 270, 500);
        PetriP connections = b.place("З'єднання БД", c.connections(), 1100, 500);
        PetriT arrival = b.expTransition("Надходження", 1 / c.rate(), 150, 500);
        b.arc(generator, arrival);
        b.arc(arrival, generator);
        b.arc(arrival, request);
        for (int s = 0; s < 2; s++) {
            double y = s == 0 ? 300 : 700;
            double ry = s == 0 ? 150 : 920; // ряд позицій-ресурсів сервера
            String n = sfx(s);
            PetriT route = b.transition("На" + n, 0, 380, y);
            route.setPriority(c.routingPriority());
            route.setProbability(s == 0 ? 1 - c.pAux() : c.pAux());
            PetriP incoming = b.place("Запит" + n, 0, 500, y);
            PetriP slots = b.place("Місця" + n, c.queue(), s == 0 ? 700 : 530, ry);
            PetriP queue = b.place("Черга" + n, 0, 740, y);
            PetriP threads = b.place("Потоки" + n, c.threads()[s], s == 0 ? 920 : 770, ry);
            PetriP ready = b.place("Готові" + n, 0, 980, y);
            PetriP cores = b.place("Ядра" + n, c.cores()[s], s == 0 ? 1160 : 1000, ry);
            PetriP computed = b.place("Обчислено" + n, 0, 1220, y);
            PetriP responses = b.place("Відповіді" + n, 0, 1460, y);
            PetriP rejected = b.place("Відхилено" + n, 0, 740, s == 0 ? 420 : 580);

            b.arc(request, route);
            b.arc(route, incoming);
            PetriT accept = b.transition("Прийом" + n, 0, 620, y);
            accept.setPriority(2);
            b.arc(incoming, accept);
            b.arc(slots, accept);
            b.arc(accept, queue);
            PetriT reject = b.transition("Відмова" + n, 0, 620, s == 0 ? 420 : 580);
            reject.setPriority(1);
            b.arc(incoming, reject);
            b.arc(reject, rejected);
            PetriT take = b.transition("Потік" + n, 0, 860, y);
            b.arc(queue, take);
            b.arc(threads, take);
            b.arc(take, ready);
            b.arc(take, slots);
            PetriT compute = b.expTransition("Обробка" + n, c.cpu()[s], 1100, y);
            b.arc(ready, compute);
            b.arc(cores, compute);
            b.arc(compute, cores);
            b.arc(compute, computed);
            PetriT write = b.expTransition("Запис" + n, c.db(), 1340, y);
            b.arc(computed, write);
            b.arc(connections, write);
            b.arc(write, connections);
            b.arc(write, threads);
            b.arc(write, responses);
        }
        return b;
    }

    /** Запити, що перебувають на сервері s: у черзі, у потоках, на обчисленні та записі в БД. */
    static String[] stagePlaces(int s) {
        return new String[] {"Черга" + sfx(s), "Готові" + sfx(s), "Обчислено" + sfx(s)};
    }

    record ServerRun(double lambda, double response, double queueWait, double coreWait, double compute,
                     double dbWait, double dbWrite, double rejectedShare, double coreLoad, double threadsBusy,
                     double queueMean, int queueMax) {
    }

    record Run(ServerRun[] server, double response, double auxShare, double dbBusy, double rejectedShare) {
    }

    static Run run(Config c, double time, double warmup) {
        Simulation s = new Simulation(net(c).build(), warmup);
        int[] queueMax = new int[2];
        s.afterEvent(x -> {
            for (int k = 0; k < 2; k++) {
                queueMax[k] = Math.max(queueMax[k], x.mark("Черга" + sfx(k)));
            }
        });
        s.run(time);
        ServerRun[] servers = new ServerRun[2];
        double inSystem = 0;
        double done = 0;
        double dbBusy = 0;
        double rejected = 0;
        for (int k = 0; k < 2; k++) {
            String n = sfx(k);
            double lambda = s.fired("Запис" + n) / s.period();
            double[] parts = {s.meanMark("Черга" + n), s.meanMark("Готові" + n), s.meanBuffer("Обробка" + n),
                    s.meanMark("Обчислено" + n), s.meanBuffer("Запис" + n)};
            double total = 0;
            for (double v : parts) {
                total += v;
            }
            // формула Літтла для кожного етапу: W = L / λ
            servers[k] = new ServerRun(lambda, total / lambda, parts[0] / lambda, parts[1] / lambda,
                    parts[2] / lambda, parts[3] / lambda, parts[4] / lambda,
                    s.fired("Відмова" + n) / (double) s.fired("На" + n),
                    s.meanBuffer("Обробка" + n) / c.cores()[k], c.threads()[k] - s.meanMark("Потоки" + n),
                    s.meanMark("Черга" + n), queueMax[k]);
            inSystem += total;
            done += lambda;
            dbBusy += parts[4];
            rejected += s.fired("Відмова" + n);
        }
        double arrived = s.fired("Надходження");
        double routed = s.fired("На С1") + s.fired("На С2");
        return new Run(servers, inSystem / done, s.fired("На С2") / routed,
                dbBusy / c.connections(), rejected / arrived);
    }

    /** Час відповіді за мережею Джексона (потоки й черга необмежені): M/M/K для ядер, M/M/M для БД. */
    static double[] analytic(Config c) {
        double db = Queueing.sojournMMc(c.connections(), c.rate(), c.db());
        double[] w = new double[3];
        for (int k = 0; k < 2; k++) {
            w[k] = Queueing.sojournMMc(c.cores()[k], c.serverRate(k), c.cpu()[k]) + db;
        }
        w[2] = (1 - c.pAux()) * w[0] + c.pAux() * w[1];
        return w;
    }

    static List<Run> runs(Config c, int count, double time, double warmup) {
        List<Run> list = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            list.add(run(c, time, warmup));
        }
        return list;
    }

    static Stats stats(List<Run> runs, ToDoubleFunction<Run> f) {
        return Stats.of(runs.stream().map(f::applyAsDouble).toList());
    }

    static Map<String, Object> main(Output out) {
        Config c = Config.base();
        out.println("ЗАВДАННЯ 3. Обробка запитів на двох серверах зі спільною БД (PetriSim), час у мс");
        out.printf("Запити exp(%.0f) мс (%.0f запитів/с), на допоміжний сервер з ймовірністю %.1f, "
                + "черга до %d%n",
                1 / c.rate(), c.rate() * 1000, c.pAux(), c.queue());
        for (int k = 0; k < 2; k++) {
            out.printf("Сервер%s: потоків %d, ядер %d, обчислення exp(%.0f) мс%n", sfx(k), c.threads()[k],
                    c.cores()[k], c.cpu()[k]);
        }
        out.printf("БД: з'єднань %d, запис exp(%.0f) мс%n%n", c.connections(), c.db());

        out.println("Трасування перших подій:\n");
        Simulation traced = new Simulation(net(c).build(), 0);
        traced.trace(10, out::println);
        traced.run(30);

        Simulation checked = new Simulation(net(c).build(), 0);
        long[] checks = {0};
        checked.afterEvent(s -> {
            int inSystem = s.mark("Запит");
            int finished = 0;
            for (int k = 0; k < 2; k++) {
                String n = sfx(k);
                Checks.check(s.mark("Потоки" + n) + s.buffer("Потік" + n) + s.mark("Готові" + n)
                        + s.buffer("Обробка" + n) + s.mark("Обчислено" + n) + s.buffer("Запис" + n)
                        == c.threads()[k], "потоки" + n);
                Checks.check(s.mark("Ядра" + n) + s.buffer("Обробка" + n) == c.cores()[k], "ядра" + n);
                Checks.check(s.mark("Місця" + n) + s.buffer("Прийом" + n) + s.mark("Черга" + n)
                        + s.buffer("Потік" + n) == c.queue(), "черга" + n);
                inSystem += s.mark("Запит" + n) + s.mark("Черга" + n) + s.mark("Готові" + n)
                        + s.mark("Обчислено" + n);
                finished += s.mark("Відповіді" + n) + s.mark("Відхилено" + n);
            }
            for (PetriT t : s.transitions()) {
                if (!t.getName().equals("Надходження")) {
                    inSystem += t.getBuffer();
                }
            }
            int db = s.mark("З'єднання БД") + s.buffer("Запис С1") + s.buffer("Запис С2");
            Checks.check(db == c.connections(),
                    "з'єднання БД");
            Checks.check(s.fired("Надходження") == inSystem + finished, "збереження запитів");
            checks[0]++;
        });
        checked.run(TIME);
        double libraryDiff = Checks.libraryDifference(checked);
        out.printf("Перевірка інваріантів після кожної з %d подій: порушень немає%n", checks[0]);
        out.printf("Середні значення бібліотеки і власні статистики прогону без розгону: найбільша "
                + "розбіжність %.2e%n", libraryDiff);

        List<Run> base = runs(c, RUNS, TIME, WARMUP);
        Map<String, Stats> table = new LinkedHashMap<>();
        table.put("середній час відповіді (усі запити), мс", stats(base, Run::response));
        for (int k = 0; k < 2; k++) {
            int i = k;
            String n = " (С" + (k + 1) + ")";
            table.put("час відповіді" + n + ", мс", stats(base, r -> r.server()[i].response()));
            table.put("  очікування в черзі" + n + ", мс", stats(base, r -> r.server()[i].queueWait()));
            table.put("  очікування ядра" + n + ", мс", stats(base, r -> r.server()[i].coreWait()));
            table.put("  обчислення" + n + ", мс", stats(base, r -> r.server()[i].compute()));
            table.put("  очікування з'єднання БД" + n + ", мс", stats(base, r -> r.server()[i].dbWait()));
            table.put("  запис у БД" + n + ", мс", stats(base, r -> r.server()[i].dbWrite()));
            table.put("пропускна здатність" + n + ", запитів/с",
                    stats(base, r -> r.server()[i].lambda() * 1000));
            table.put("завантаження ядер" + n, stats(base, r -> r.server()[i].coreLoad()));
            table.put("зайнято потоків" + n, stats(base, r -> r.server()[i].threadsBusy()));
            table.put("середня довжина черги" + n, stats(base, r -> r.server()[i].queueMean()));
        }
        table.put("завантаження з'єднань БД", stats(base, Run::dbBusy));
        table.put("частка запитів на С2", stats(base, Run::auxShare));
        table.put("частка відхилених запитів", stats(base, Run::rejectedShare));
        out.printf("%nРЕЗУЛЬТАТИ (%d прогонів по %.0f с, розгін %.0f с, 95 %% довірчий інтервал):%n", RUNS,
                TIME / 1000, WARMUP / 1000);
        table.forEach((k, v) -> out.printf("  %-46s %s%n", k, v));

        double[] w = analytic(c);
        out.printf("%nПеревірка за мережею Джексона (M/M/K для ядер, M/M/%d для БД): час відповіді "
                + "С1 %.4f, С2 %.4f, усі %.4f мс%n", c.connections(), w[0], w[1], w[2]);
        for (int k = 0; k < 2; k++) {
            out.printf("Завантаження ядер С%d: λ·E[S]/K = %.4f%n", k + 1,
                    c.serverRate(k) * c.cpu()[k] / c.cores()[k]);
        }
        out.printf("Завантаження з'єднань БД: λ·E[S]/M = %.4f%n", c.rate() * c.db() / c.connections());
        double lam = (table.get("пропускна здатність (С1), запитів/с").mean()
                + table.get("пропускна здатність (С2), запитів/с").mean());
        out.printf("Сумарна пропускна здатність %.4f запитів/с при інтенсивності надходження %.1f%n", lam,
                c.rate() * 1000);

        List<Run> wrong = runs(c.withRoutingPriority(0), 5, EXP_TIME, WARMUP);
        Stats wrongShare = stats(wrong, Run::auxShare);
        out.printf("%nПереходи розподілу з пріоритетом 0 (як у переходу «Надходження»): частка "
                + "запитів на С2 %s замість %.1f%n", wrongShare, c.pAux());

        out.printf("%nЕКСПЕРИМЕНТ 1: кількість потоків головного сервера (%d прогонів по %.0f с)%n", EXP_RUNS,
                EXP_TIME / 1000);
        out.println("  потоків С1   час відповіді С1, мс    очікування в черзі, мс   завантаження ядер  "
                + "зайнято потоків   відхилено");
        List<Object> threadRows = new ArrayList<>();
        for (int n : new int[] {8, 10, 12, 14, 16, 24, 32, 64}) {
            List<Run> r = runs(c.withThreads(n, c.threads()[1]), EXP_RUNS, EXP_TIME, WARMUP);
            Stats resp = stats(r, x -> x.server()[0].response());
            Stats wait = stats(r, x -> x.server()[0].queueWait());
            Stats load = stats(r, x -> x.server()[0].coreLoad());
            Stats busy = stats(r, x -> x.server()[0].threadsBusy());
            Stats rej = stats(r, x -> x.server()[0].rejectedShare());
            out.printf("  %9d   %22s  %22s  %17s  %15s  %10.4f%n", n, resp.format(2), wait.format(2),
                    load.format(4), busy.format(2), rej.mean());
            threadRows.add(Json.obj("threads", n, "response", resp, "queue_wait", wait, "core_load", load,
                    "threads_busy", busy, "rejected", rej));
        }

        out.printf("%nЕКСПЕРИМЕНТ 2: інтенсивність надходження (%d прогонів по %.0f с)%n", EXP_RUNS,
                EXP_TIME / 1000);
        out.println("  запитів/с  потоки С1/С2  час відповіді, мс        теорія, мс   відхилено   "
                + "черга С1 max  черга С2 max");
        List<Object> loadRows = new ArrayList<>();
        for (double perSecond : new double[] {100, 150, 200, 230, 250, 280, 400}) {
            for (int[] threads : new int[][] {{32, 16}, {12, 6}}) {
                Config cfg = c.withRate(perSecond / 1000).withThreads(threads[0], threads[1]);
                List<Run> r = runs(cfg, EXP_RUNS, EXP_TIME, WARMUP);
                Stats resp = stats(r, Run::response);
                Stats rej = stats(r, Run::rejectedShare);
                int q1 = r.stream().mapToInt(x -> x.server()[0].queueMax()).max().orElse(0);
                int q2 = r.stream().mapToInt(x -> x.server()[1].queueMax()).max().orElse(0);
                double theory = analytic(cfg)[2];
                out.printf("  %9.0f  %12s  %22s  %10.2f   %9.4f   %12d  %12d%n", perSecond,
                        threads[0] + "/" + threads[1], resp.format(2), theory, rej.mean(), q1, q2);
                loadRows.add(Json.obj("rate", perSecond, "threads", List.of(threads[0], threads[1]),
                        "response", resp, "response1", stats(r, x -> x.server()[0].response()),
                        "response2", stats(r, x -> x.server()[1].response()), "theory", theory,
                        "rejected", rej, "queue_max", List.of(q1, q2)));
            }
        }
        return Json.obj("config", Json.obj("rate_per_s", c.rate() * 1000, "p_aux", c.pAux(),
                        "queue", c.queue(),
                        "threads", List.of(c.threads()[0], c.threads()[1]),
                        "cores", List.of(c.cores()[0], c.cores()[1]), "cpu", List.of(c.cpu()[0], c.cpu()[1]),
                        "connections", c.connections(), "db", c.db()),
                "table", table, "analytic", List.of(w[0], w[1], w[2]), "check_events", checks[0],
                "library_difference", libraryDiff, "wrong_priority_share", wrongShare, "threads", threadRows,
                "load", loadRows, "runs", RUNS);
    }
}
