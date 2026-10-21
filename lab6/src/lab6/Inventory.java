package lab6;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToDoubleFunction;

import ua.stetsenkoinna.petriobj.PetriP;
import ua.stetsenkoinna.petriobj.PetriT;

/** Завдання 2: управління запасами холодильників з періодичним переглядом (задача 4 практикуму 5). */
public final class Inventory {

    static final double DEMAND = 0.2;
    static final double LOST = 0.8;
    static final double BACKORDER = 0.2;
    static final double REVIEW = 4.0;
    static final double LEAD = 3.0;
    static final int LEVEL = 72;
    static final int REORDER = 18;
    static final double TIME = 5000;
    static final double WARMUP = 52;
    static final int RUNS = 100;

    private Inventory() {
    }

    static NetBuilder net() {
        NetBuilder b = new NetBuilder("Inventory");
        PetriP generator = b.place("Генератор попиту", 1, -60, 240);
        PetriP customer = b.place("Покупець", 0, 280, 240);
        PetriP stock = b.place("Запас", LEVEL, 640, 240);
        PetriP sold = b.place("Продано", 0, 1000, 400);
        PetriP backlog = b.place("Відкладений попит", 0, 600, 400);
        PetriP lost = b.place("Втрачені продажі", 0, 600, 100);
        PetriP shortage = b.place("Нестача до рівня", 0, 220, 560);
        PetriP timer = b.place("Таймер перегляду", 1, -60, 840);
        PetriP review = b.place("Перегляд", 0, 280, 840);
        PetriP order = b.place("Формування замовлення", 0, 640, 840);

        PetriT arrival = b.expTransition("Надходження покупця", DEMAND, 120, 240);
        b.arc(generator, arrival);
        b.arc(arrival, generator);
        b.arc(arrival, customer);
        PetriT sale = b.transition("Продаж", 0, 430, 240);
        sale.setPriority(3);
        b.arc(customer, sale);
        b.arc(stock, sale);
        b.arc(sale, sold);
        b.arc(sale, shortage);
        PetriT leave = b.transition("Відмова від покупки", 0, 430, 100);
        leave.setPriority(1);
        leave.setProbability(LOST);
        b.arc(customer, leave);
        b.arc(leave, lost);
        PetriT wait = b.transition("Повторне замовлення", 0, 430, 400);
        wait.setPriority(1);
        wait.setProbability(BACKORDER);
        b.arc(customer, wait);
        b.arc(wait, backlog);
        b.arc(wait, shortage);
        PetriT issue = b.transition("Видача відкладеного", 0, 780, 400);
        issue.setPriority(4);
        b.arc(stock, issue);
        b.arc(backlog, issue);
        b.arc(issue, sold);

        PetriT tick = b.transition("Перегляд запасу", REVIEW, 120, 840);
        b.arc(timer, tick);
        b.arc(tick, timer);
        b.arc(tick, review);
        // поточний стан запасу ≤ 18  ⇔  нестача до контрольного рівня 72 ≥ 54
        PetriT decide = b.transition("Рішення замовити", 0, 430, 840);
        decide.setPriority(2);
        b.arc(review, decide);
        b.info(shortage, decide, LEVEL - REORDER);
        b.arc(decide, order);
        PetriT skip = b.transition("Без замовлення", 0, 430, 980);
        b.arc(review, skip);
        // кожен холодильник нестачі – окремий маркер; усі вони входять у перехід в один момент
        // і одночасно виходять через 3 тижні, тобто утворюють одну партію
        PetriT delivery = b.transition("Доставка", LEAD, 820, 620);
        delivery.setPriority(1);
        b.arc(shortage, delivery);
        b.info(order, delivery, 1);
        b.arc(delivery, stock);
        PetriT close = b.transition("Кінець замовлення", 0, 860, 840);
        b.arc(order, close);
        return b;
    }

    /** Поточний стан запасу: наявний запас + замовлені раніше − невдоволений попит. */
    static int position(Simulation s) {
        return s.mark("Запас") + s.buffer("Доставка") - s.mark("Відкладений попит");
    }

    record Run(double stock, double lostInterval, double lostRate, double lostShare, double backorderShare,
               double backorders, double stockout, double position, double orderSize, double orderInterval,
               double demandRate, double soldRate, List<Double> gaps) {
    }

    static Run run(Simulation s) {
        List<Double> lost = new ArrayList<>();
        int[] orders = {0};
        s.onOut("Відмова від покупки", t -> {
            if (t >= WARMUP) {
                lost.add(t);
            }
        });
        s.onOut("Рішення замовити", t -> {
            if (t >= WARMUP) {
                orders[0]++;
            }
        });
        Simulation.Watch empty = s.watch(x -> x.mark("Запас") == 0 ? 1 : 0);
        Simulation.Watch position = s.watch(Inventory::position);
        s.run(TIME);
        List<Double> gaps = new ArrayList<>();
        for (int i = 1; i < lost.size(); i++) {
            gaps.add(lost.get(i) - lost.get(i - 1));
        }
        double period = s.period();
        double demand = s.fired("Надходження покупця");
        double meanGap = gaps.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        return new Run(s.meanMark("Запас"), meanGap,
                lost.size() / period, lost.size() / demand, s.fired("Повторне замовлення") / demand,
                s.meanMark("Відкладений попит"), empty.mean(), position.mean(),
                (double) s.entered("Доставка") / Math.max(orders[0], 1), period / orders[0], demand / period,
                (s.fired("Продаж") + s.fired("Видача відкладеного")) / period, gaps);
    }

    static Map<String, Object> main(Output out) {
        out.println("ЗАВДАННЯ 2. Управління запасами (PetriSim)");
        out.printf("Попит exp(%.1f) тижня, перегляд кожні %.0f тижні, рівень %d, точка замовлення "
                + "%d, доставка %.0f тижні, "
                + "втрата продажу з ймовірністю %.1f%n%n", DEMAND, REVIEW, LEVEL, REORDER, LEAD, LOST);
        out.println("Трасування подій навколо першого замовлення:\n");
        List<String> lines = new ArrayList<>();
        Simulation traced = new Simulation(net().build(), 0);
        traced.trace(Integer.MAX_VALUE, lines::add);
        traced.run(30);
        int first = indexOf(lines, "вхід у переходи:   Рішення замовити", 0);
        int delivery = indexOf(lines, "вихід з переходів: Доставка", first);
        List<String> excerpt = new ArrayList<>(lines.subList(first - 1, first + 3));
        excerpt.add("...\n");
        excerpt.addAll(lines.subList(delivery - 1, delivery + 2));
        excerpt.forEach(out::println);

        Simulation checked = new Simulation(net().build(), 0);
        long[] checks = {0};
        checked.afterEvent(s -> {
            Checks.check(position(s) + s.buffer("Продаж") + s.mark("Нестача до рівня") == LEVEL,
                    "стан запасу + нестача = " + LEVEL);
            checks[0]++;
        });
        checked.run(TIME);
        double libraryDiff = Checks.libraryDifference(checked);
        out.printf("Перевірка інваріанта «стан запасу + нестача до рівня = %d» після кожної з %d "
                + "подій: порушень немає%n", LEVEL, checks[0]);
        out.printf("Середні значення бібліотеки і власні статистики прогону без розгону: найбільша "
                + "розбіжність %.2e%n", libraryDiff);

        List<Run> runs = new ArrayList<>();
        for (int r = 0; r < RUNS; r++) {
            runs.add(run(new Simulation(net().build(), WARMUP)));
        }
        Map<String, Stats> table = new LinkedHashMap<>();
        table.put("середня кількість холодильників у запасі", stats(runs, Run::stock));
        table.put("середній час між втраченими продажами, тижні", stats(runs, Run::lostInterval));
        table.put("втрачених продажів за тиждень", stats(runs, Run::lostRate));
        table.put("частка втрачених продажів", stats(runs, Run::lostShare));
        table.put("частка відкладеного попиту", stats(runs, Run::backorderShare));
        table.put("середній невдоволений попит", stats(runs, Run::backorders));
        table.put("частка часу без запасу", stats(runs, Run::stockout));
        table.put("середній поточний стан запасу", stats(runs, Run::position));
        table.put("середній розмір замовлення", stats(runs, Run::orderSize));
        table.put("середній інтервал між замовленнями, тижні", stats(runs, Run::orderInterval));
        out.printf("%nРЕЗУЛЬТАТИ (%d прогонів по %.0f тижнів, розгін %.0f тижнів, 95 %% довірчий "
                + "інтервал):%n", RUNS,
                TIME, WARMUP);
        table.forEach((k, v) -> out.printf("  %-46s %s%n", k, v));

        List<Double> gaps = runs.stream().flatMap(r -> r.gaps().stream()).sorted().toList();
        double mean = gaps.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double ss = gaps.stream().mapToDouble(g -> (g - mean) * (g - mean)).sum();
        double sd = Math.sqrt(ss / (gaps.size() - 1));
        double median = gaps.get(gaps.size() / 2);
        double shortShare = gaps.stream().filter(g -> g < REVIEW).count() / (double) gaps.size();
        out.printf("%nІнтервали між втраченими продажами: медіана %.4f, коефіцієнт варіації %.4f, "
                + "частка інтервалів до 4 тижнів %.4f%n", median, sd / mean, shortShare);
        double demand = stats(runs, Run::demandRate).mean();
        double sold = stats(runs, Run::soldRate).mean();
        double lost = table.get("втрачених продажів за тиждень").mean();
        out.printf("%nПеревірка: інтенсивність попиту %.4f (задано %.0f), продано %.4f + втрачено "
                + "%.4f = %.4f%n",
                demand, 1 / DEMAND, sold, lost, sold + lost);
        out.printf("1 / інтенсивність втрачених продажів = %.4f тижня%n", 1 / lost);
        return Json.obj("table", table, "gaps", Json.obj("median", median, "cv", sd / mean, "short_share",
                shortShare), "demand_rate", demand, "sold_rate", sold, "check_events", checks[0],
                "library_difference", libraryDiff, "runs", RUNS, "trace", String.join("\n", excerpt));
    }

    private static int indexOf(List<String> lines, String text, int from) {
        for (int i = from; i < lines.size(); i++) {
            if (lines.get(i).contains(text)) {
                return i;
            }
        }
        throw new IllegalStateException(text);
    }

    static Stats stats(List<Run> runs, ToDoubleFunction<Run> f) {
        return Stats.of(runs.stream().map(f::applyAsDouble).toList());
    }
}
