package lab7;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToDoubleFunction;

/**
 * Завдання 1: тестування алгоритму Петрі-об'єктного моделювання на мережі масового обслуговування,
 * для якої відомі аналітичні результати (відкрита мережа Джексона). Модель складено з Петрі-об'єктів
 * трьох класів: Generator, Smo (використано чотири рази) і Fork (двічі).
 */
public final class QueueNetwork {

    static final double ARRIVAL = 1.0;
    static final double P_B = 0.7;
    static final double P_BACK = 0.2;
    static final double S_A = 0.6;
    static final double S_B = 1.0;
    static final double S_C = 1.5;
    static final double TIME = 100000;
    static final double WARMUP = 1000;
    static final int RUNS = 20;
    static final String[] NODES = {"A", "B", "C"};

    private QueueNetwork() {
    }

    /**
     * priorityB1 – пріоритет об'єкта «СМО B1» відносно «СМО B2», що мають спільну чергу; back – ймовірність
     * повернення вимоги з вузла B на вузол A.
     */
    static ObjectModel model(int priorityB1, double back) {
        ObjectModel m = new ObjectModel("Мережа масового обслуговування");
        Generator g = m.add(new Generator("Генератор", "Генератор", "Надходження", "Черга A",
                Delay.exp(ARRIVAL)), 0, 520);
        Smo a = m.add(new Smo("A", 1, S_A), 330, 520);
        Fork f1 = m.add(new Fork("1", "Обслужені A", new String[]{"Черга B1", "Черга C"},
                new String[]{"На B", "На C"}, new double[]{P_B, 1 - P_B}), 740, 520);
        Smo b1 = m.add(new Smo("B1", 1, S_B), 1080, 260);
        Smo b2 = m.add(new Smo("B2", 1, S_B), 1080, 520);
        Smo c = m.add(new Smo("C", 1, S_C), 1080, 800);
        Fork f2 = m.add(new Fork("2", "Обслужені B1", new String[]{"Черга A", "Вийшли з мережі"},
                new String[]{"Повтор на A", "Вихід з B"}, new double[]{back, 1 - back}), 1500, 260);
        b1.priority(priorityB1);
        m.drawAt("Повтор на A", 1630, 0);
        m.link(g, "Черга A", a, "Черга A");
        m.link(f1, "Обслужені A", a, "Обслужені A");
        m.link(f1, "Черга B1", b1, "Черга B1");
        m.link(f1, "Черга C", c, "Черга C");
        m.link(b2, "Черга B2", b1, "Черга B1");
        m.link(b2, "Обслужені B2", b1, "Обслужені B1");
        m.link(f2, "Обслужені B1", b1, "Обслужені B1");
        m.link(f2, "Черга A", a, "Черга A");
        return m;
    }

    /** Інтенсивності потоків вимог у вузлах з рівнянь балансу λ_i = λ_0i + Σ λ_j p_ji. */
    static Map<String, Double> rates(double back) {
        double lambda = 1 / ARRIVAL;
        double a = lambda / (1 - P_B * back);
        return Map.of("A", a, "B", P_B * a, "C", (1 - P_B) * a);
    }

    static int channels(String node) {
        return node.equals("B") ? 2 : 1;
    }

    static double service(String node) {
        return switch (node) {
            case "A" -> S_A;
            case "B" -> S_B;
            default -> S_C;
        };
    }

    /** Аналітичні характеристики вузлів мережі Джексона (кожен вузол – система M/M/c). */
    static Map<String, Object> analytic() {
        Map<String, Object> out = new LinkedHashMap<>();
        double total = 0;
        for (String n : NODES) {
            double lambda = rates(P_BACK).get(n);
            int c = channels(n);
            double s = service(n);
            double lq = Queueing.queueMMc(c, lambda, s);
            double l = lq + lambda * s;
            total += l;
            out.put(n, Json.obj("rate", lambda, "load", lambda * s / c, "queue", lq, "in_node", l,
                    "sojourn", l / lambda));
        }
        out.put("in_network", total);
        out.put("sojourn", total * ARRIVAL);
        return out;
    }

    /** Показники одного прогону. */
    record Run(Map<String, double[]> node, double inNetwork, double sojournLittle, double sojourn,
               double shareB, double shareBack, double shareB1, long events) {
    }

    static Run run(Collector s) {
        List<Double> sojourns = new ArrayList<>();
        Collector.OutAction exit = (t, born) -> {
            if (born >= WARMUP) {
                sojourns.add(t - born);
            }
        };
        s.onOut("Вихід з B", exit);
        s.onOut("Обслуговування C", exit);
        s.run(TIME);
        Map<String, double[]> node = new LinkedHashMap<>();
        double total = 0;
        for (String n : NODES) {
            String[] servers = n.equals("B") ? new String[]{"B1", "B2"} : new String[]{n};
            double busy = 0;
            double served = 0;
            for (String k : servers) {
                busy += s.meanBuffer("Обслуговування " + k);
                served += s.fired("Обслуговування " + k);
            }
            double queue = s.meanMark("Черга " + servers[0]);
            double rate = served / s.period();
            node.put(n, new double[]{rate, busy / channels(n), queue, queue + busy, (queue + busy) / rate});
            total += queue + busy;
        }
        double arrivals = s.fired("Надходження") / s.period();
        double toB = s.fired("На B");
        double back = s.fired("Повтор на A");
        double b1 = s.fired("Обслуговування B1");
        return new Run(node, total, total / arrivals,
                sojourns.stream().mapToDouble(x -> x).average().orElse(0),
                toB / (toB + s.fired("На C")), back / (back + s.fired("Вихід з B")),
                b1 / (b1 + s.fired("Обслуговування B2")), s.events());
    }

    static Map<String, Object> main(Output out) {
        out.println("ЗАВДАННЯ 1. Мережа масового обслуговування з Петрі-об'єктів (PetriObjModel.go)");
        out.printf("Надходження exp(%.1f); СМО A: 1 канал, exp(%.1f); розгалуження 1: %.1f на B, %.1f на C; "
                + "B: два об'єкти СМО B1, B2 зі спільною чергою, exp(%.1f); C: 1 канал, exp(%.1f); "
                + "після B з ймовірністю %.1f повтор на A%n%n", ARRIVAL, S_A, P_B, 1 - P_B, S_B, S_C, P_BACK);
        out.println("Трасування перших подій (у дужках – найближчий момент виходу маркера з переходу):\n");
        Collector traced = new Collector(model(0, P_BACK), 0);
        traced.trace(10, out::println);
        traced.run(5);

        Collector checked = new Collector(model(0, P_BACK), 0);
        long[] checks = {0};
        checked.afterEvent(s -> {
            Checks.check(s.mark("Генератор") + s.buffer("Надходження") == 1, "генератор");
            for (String k : new String[]{"A", "B1", "B2", "C"}) {
                Checks.check(s.mark("Вільні канали " + k) + s.buffer("Обслуговування " + k) == 1,
                        "канал " + k);
            }
            int inside = s.mark("Черга A") + s.mark("Черга B1") + s.mark("Черга C") + s.mark("Обслужені A")
                    + s.mark("Обслужені B1");
            for (String t : new String[]{"Обслуговування A", "Обслуговування B1", "Обслуговування B2",
                    "Обслуговування C", "На B", "На C", "Повтор на A", "Вихід з B"}) {
                inside += s.buffer(t);
            }
            Checks.check(s.fired("Надходження") == inside + s.mark("Вийшли з мережі") + s.mark("Обслужені C"),
                    "збереження вимог");
            Checks.check(s.tracksConsistently(), "мітки маркерів");
            checks[0]++;
        });
        checked.run(TIME);
        out.printf("Перевірка інваріантів після кожної з %d подій: порушень немає%n", checks[0]);
        out.printf("Середні значення бібліотеки (PetriP.getMean, PetriT.getMean, обчислені в "
                + "PetriObjModel.go) і власні статистики прогону без розгону: найбільша розбіжність %.2e%n",
                checked.libraryDifference());

        List<Run> runs = new ArrayList<>();
        for (int r = 0; r < RUNS; r++) {
            runs.add(run(new Collector(model(0, P_BACK), WARMUP)));
        }
        Map<String, Object> analytic = analytic();
        String[] metrics = {"інтенсивність потоку", "завантаження каналу", "середня черга",
                "середня кількість у вузлі", "середній час у вузлі"};
        String[] keys = {"rate", "load", "queue", "in_node", "sojourn"};
        out.printf("%nРЕЗУЛЬТАТИ (%d прогонів по %.0f, розгін %.0f, 95 %% довірчий інтервал) і теорія:%n",
                RUNS, TIME, WARMUP);
        Map<String, Object> nodes = new LinkedHashMap<>();
        for (String n : NODES) {
            Map<String, Object> rows = new LinkedHashMap<>();
            @SuppressWarnings("unchecked")
            Map<String, Object> th = (Map<String, Object>) analytic.get(n);
            out.printf("  вузол %s (M/M/%d):%n", n, channels(n));
            for (int i = 0; i < metrics.length; i++) {
                int k = i;
                Stats st = stats(runs, r -> r.node().get(n)[k]);
                double theory = (double) th.get(keys[i]);
                rows.put(keys[i], Json.obj("sim", st, "theory", theory));
                out.printf("    %-28s %-20s теорія %.4f%n", metrics[i], st, theory);
            }
            nodes.put(n, rows);
        }
        Stats inNet = stats(runs, Run::inNetwork);
        Stats little = stats(runs, Run::sojournLittle);
        Stats direct = stats(runs, Run::sojourn);
        out.printf("  середня кількість вимог у мережі     %-20s теорія %.4f%n", inNet,
                analytic.get("in_network"));
        out.printf("  час перебування в мережі (L / λ)     %-20s теорія %.4f%n", little,
                analytic.get("sojourn"));
        out.printf("  час перебування (за мітками вимог)   %-20s%n", direct);
        Stats shareB = stats(runs, Run::shareB);
        Stats shareBack = stats(runs, Run::shareBack);
        Stats shareB1 = stats(runs, Run::shareB1);
        out.printf("Частки маршрутів: на B %s (задано %.1f), повтор на A %s (задано %.1f)%n", shareB, P_B,
                shareBack, P_BACK);
        out.printf("Частка вимог вузла B, обслужених об'єктом СМО B1 (однакові пріоритети B1, B2): %s%n",
                shareB1);

        // перевага одного з двох об'єктів зі спільною чергою задається пріоритетом об'єкта
        List<Object> priority = new ArrayList<>();
        for (double back : new double[]{P_BACK, 0}) {
            List<Run> prior = new ArrayList<>();
            for (int r = 0; r < RUNS; r++) {
                prior.add(run(new Collector(model(1, back), WARMUP)));
            }
            Stats share = stats(prior, Run::shareB1);
            Stats load = stats(prior, r -> r.node().get("B")[1]);
            Stats queue = stats(prior, r -> r.node().get("B")[2]);
            double lambdaB = rates(back).get("B");
            double theory = Queueing.preferredShare(lambdaB, S_B);
            out.printf("Пріоритет об'єкта СМО B1 = 1, повтор на A %.1f (λ_B = %.4f): частка B1 %s "
                    + "(M/M/2 з пуассонівським потоком: %.4f), завантаження B %s (%.4f), черга B %s (%.4f)%n",
                    back, lambdaB, share, theory, load, lambdaB * S_B / 2, queue,
                    Queueing.queueMMc(2, lambdaB, S_B));
            priority.add(Json.obj("back", back, "rate_b", lambdaB, "share_b1", share, "share_theory", theory,
                    "load_b", load, "load_theory", lambdaB * S_B / 2, "queue_b", queue,
                    "queue_theory", Queueing.queueMMc(2, lambdaB, S_B)));
        }
        double events = runs.stream().mapToLong(Run::events).average().orElse(0);
        out.printf("Подій за прогін: %.0f%n", events);

        return Json.obj("nodes", nodes, "analytic", analytic, "in_network", inNet, "sojourn_little", little,
                "sojourn_direct", direct, "share_b", shareB, "share_back", shareBack, "share_b1", shareB1,
                "priority", priority,
                "check_events", checks[0], "library_difference", checked.libraryDifference(), "runs", RUNS,
                "events", events);
    }

    static Stats stats(List<Run> runs, ToDoubleFunction<Run> f) {
        return Stats.of(runs.stream().map(f::applyAsDouble).toList());
    }
}
