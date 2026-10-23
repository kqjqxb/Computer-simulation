package lab7;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.function.ToDoubleFunction;

import ua.stetsenkoinna.petriobj.FiringPhase;
import ua.stetsenkoinna.petriobj.PetriNet;
import ua.stetsenkoinna.petriobj.PetriObjModel;
import ua.stetsenkoinna.petriobj.PetriP;
import ua.stetsenkoinna.petriobj.PetriSim;
import ua.stetsenkoinna.petriobj.PetriT;
import ua.stetsenkoinna.petriobj.SimulationStatisticCollector;

/**
 * Збирач статистики для методу PetriObjModel.go(). Імітацію виконує сама бібліотека, а збирач через
 * інтерфейс SimulationStatisticCollector отримує: виклик flush(t) на початку кожної ітерації циклу
 * go() – стан моделі після події в момент t і входу маркерів у переходи всіх об'єктів, який не
 * змінюється до наступної події; повідомлення про кожен вхід маркерів у перехід і вихід з нього.
 * З цього обчислюються середні за часом значення з урахуванням періоду розгону, кількості входів і
 * виходів, мітки маркерів (Tracker), трасування подій і перевірки після кожної події.
 */
public final class Collector implements SimulationStatisticCollector {

    /** Дія під час виходу маркера з переходу: момент виходу і мітка маркера (NaN, якщо її немає). */
    public interface OutAction {
        void accept(double time, double stamp);
    }

    /** Середнє за часом довільної функції стану моделі та її розподіл за часом. */
    public static final class Watch {
        private final ToDoubleFunction<Collector> fn;
        private double value;
        private double area;
        private double span;
        private final Map<Double, Double> histogram = new TreeMap<>();

        private Watch(ToDoubleFunction<Collector> fn) {
            this.fn = fn;
        }

        public double mean() {
            return span > 0 ? area / span : Double.NaN;
        }

        public Map<Double, Double> distribution() {
            Map<Double, Double> d = new TreeMap<>();
            histogram.forEach((v, dt) -> d.put(v, dt / span));
            return d;
        }
    }

    private final ObjectModel model;
    private final double warmup;
    private final PetriP[] places;
    private final PetriT[] transitions;
    private final Map<PetriP, Integer> placeIndex = new IdentityHashMap<>();
    private final Map<PetriT, Integer> transitionIndex = new IdentityHashMap<>();
    private final int[] mark;
    private final int[] buffer;
    private final double[] markArea;
    private final double[] bufferArea;
    private final int[] entered;
    private final int[] fired;
    private final List<Watch> watches = new ArrayList<>();
    private final List<Consumer<Collector>> afterEvent = new ArrayList<>();
    private final Map<PetriT, List<OutAction>> outActions = new IdentityHashMap<>();
    private final Tracker tracker;
    private final List<String> inLog = new ArrayList<>();
    private final List<String> outLog = new ArrayList<>();
    private double end;
    private double last;
    private long events = -1;
    private int traceEvents;
    private Consumer<String> traceOut = s -> { };

    public Collector(ObjectModel model, double warmup) {
        this.model = model;
        this.warmup = warmup;
        model.build();
        places = model.places().toArray(PetriP[]::new);
        transitions = model.transitions().toArray(PetriT[]::new);
        for (int i = 0; i < places.length; i++) {
            placeIndex.put(places[i], i);
        }
        for (int i = 0; i < transitions.length; i++) {
            transitionIndex.put(transitions[i], i);
        }
        mark = new int[places.length];
        buffer = new int[transitions.length];
        markArea = new double[places.length];
        bufferArea = new double[transitions.length];
        entered = new int[transitions.length];
        fired = new int[transitions.length];
        tracker = new Tracker(model);
    }

    /** Імітація моделі методом PetriObjModel.go() бібліотеки до моменту time. */
    public Collector run(double time) {
        end = time;
        PetriObjModel m = model.model();
        m.setStatisticCollector(this);
        m.go(time);
        return this;
    }

    @Override
    public boolean shouldCollect(double currentTime) {
        return true;
    }

    @Override
    public void onTimeStep(double currentTime, PetriNet net, int petriObjId) {
        // стан моделі зчитується один раз за ітерацію – у flush
    }

    @Override
    public void flush(double currentTime) {
        integrate(currentTime);
        for (int i = 0; i < places.length; i++) {
            mark[i] = places[i].getMark();
        }
        for (int i = 0; i < transitions.length; i++) {
            buffer[i] = transitions[i].getBuffer();
        }
        for (Watch w : watches) {
            w.value = w.fn.applyAsDouble(this);
        }
        events++;
        for (Consumer<Collector> hook : afterEvent) {
            hook.accept(this);
        }
        if (events < traceEvents) {
            traceOut.accept(state(currentTime));
        }
        inLog.clear();
        outLog.clear();
    }

    /** Накопичення інтегралів за інтервал [last, t), протягом якого стан моделі не змінювався. */
    private void integrate(double t) {
        double from = Math.max(last, warmup);
        if (t > from) {
            double dt = t - from;
            for (int i = 0; i < places.length; i++) {
                markArea[i] += mark[i] * dt;
            }
            for (int i = 0; i < transitions.length; i++) {
                bufferArea[i] += buffer[i] * dt;
            }
            for (Watch w : watches) {
                w.area += w.value * dt;
                w.span += dt;
                w.histogram.merge(w.value, dt, Double::sum);
            }
        }
        last = Math.max(last, t);
    }

    @Override
    public void onFiringPhase(double time, FiringPhase phase, PetriT t, Iterable<PetriSim> objects) {
        switch (phase) {
            case AFTER_ACT_IN -> {
                tracker.in(t);
                if (time >= warmup) {
                    entered[transitionIndex.get(t)]++;
                }
                if (events + 1 < traceEvents) {
                    inLog.add(label(t));
                }
            }
            case BEFORE_ACT_OUT -> {
                double stamp = tracker.out(t, time);
                for (OutAction a : outActions.getOrDefault(t, List.of())) {
                    a.accept(time, stamp);
                }
            }
            case AFTER_ACT_OUT -> {
                if (time >= warmup) {
                    fired[transitionIndex.get(t)]++;
                }
                if (events + 1 < traceEvents) {
                    outLog.add(label(t));
                }
            }
            default -> {
            }
        }
    }

    @Override
    public void onSimulationEnd(double simulationEndTime, Iterable<PetriSim> objects) {
        // go() повідомляє момент наступної, вже не виконаної події, тому інтервал завершується в end
        integrate(end);
    }

    @Override
    public void shutdown() {
    }

    private String label(PetriT t) {
        return model.owner(t).name() + ": " + t.getName();
    }

    private String state(double t) {
        StringBuilder sb = new StringBuilder(String.format(Locale.ROOT, "t = %.4f%n", t));
        if (!outLog.isEmpty()) {
            sb.append("  вихід: ").append(grouped(outLog)).append('\n');
        }
        if (!inLog.isEmpty()) {
            sb.append("  вхід:  ").append(grouped(inLog)).append('\n');
        }
        List<String> marks = new ArrayList<>();
        for (PetriP p : places) {
            if (p.getMark() > 0) {
                marks.add(p.getName() + "=" + p.getMark());
            }
        }
        List<String> busy = new ArrayList<>();
        for (PetriT tr : transitions) {
            if (tr.getBuffer() > 0) {
                busy.add(String.format(Locale.ROOT, "%s=%d (%.3f)", tr.getName(), tr.getBuffer(),
                        tr.getMinTime()));
            }
        }
        sb.append("  маркування: ").append(String.join(", ", marks)).append('\n');
        sb.append("  у переходах (найближчий вихід): ").append(String.join(", ", busy)).append('\n');
        return sb.toString();
    }

    private static String grouped(List<String> names) {
        List<String> parts = new ArrayList<>();
        for (int i = 0; i < names.size(); ) {
            int j = i;
            while (j < names.size() && names.get(j).equals(names.get(i))) {
                j++;
            }
            parts.add(j - i == 1 ? names.get(i) : names.get(i) + " ×" + (j - i));
            i = j;
        }
        return String.join(", ", parts);
    }

    public void trace(int count, Consumer<String> out) {
        traceEvents = count;
        traceOut = out;
    }

    public void afterEvent(Consumer<Collector> hook) {
        afterEvent.add(hook);
    }

    public void onOut(String transition, OutAction action) {
        outActions.computeIfAbsent(model.transition(transition), k -> new ArrayList<>()).add(action);
    }

    public Watch watch(ToDoubleFunction<Collector> fn) {
        Watch w = new Watch(fn);
        watches.add(w);
        return w;
    }

    public int mark(String place) {
        return model.place(place).getMark();
    }

    public int buffer(String transition) {
        return model.transition(transition).getBuffer();
    }

    public double meanMark(String place) {
        return markArea[placeIndex.get(model.place(place))] / period();
    }

    public double meanBuffer(String transition) {
        return bufferArea[transitionIndex.get(model.transition(transition))] / period();
    }

    public int fired(String transition) {
        return fired[transitionIndex.get(model.transition(transition))];
    }

    public int entered(String transition) {
        return entered[transitionIndex.get(model.transition(transition))];
    }

    /**
     * Найбільша розбіжність між середніми, які обчислює бібліотека в PetriObjModel.go()
     * (PetriP.getMean, PetriT.getMean), і власними; має сенс для прогону без розгону.
     */
    public double libraryDifference() {
        double max = 0;
        for (int i = 0; i < places.length; i++) {
            max = Math.max(max, Math.abs(markArea[i] / period() - places[i].getMean()));
        }
        for (int i = 0; i < transitions.length; i++) {
            max = Math.max(max, Math.abs(bufferArea[i] / period() - transitions[i].getMean()));
        }
        return max;
    }

    public boolean tracksConsistently() {
        return tracker.consistent();
    }

    public ObjectModel model() {
        return model;
    }

    /** Поточний модельний час (момент останньої події). */
    public double time() {
        return last;
    }

    /** Тривалість збору статистики (без розгону). */
    public double period() {
        return end - warmup;
    }

    /** Кількість подій – ітерацій циклу go(), у кожній з яких вихід маркерів виконує один об'єкт. */
    public long events() {
        return events;
    }
}
