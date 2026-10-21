package lab6;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.ToDoubleFunction;

import ua.stetsenkoinna.petriobj.FiringPhase;
import ua.stetsenkoinna.petriobj.PetriNet;
import ua.stetsenkoinna.petriobj.PetriP;
import ua.stetsenkoinna.petriobj.PetriSim;
import ua.stetsenkoinna.petriobj.PetriT;

/**
 * Імітація мережі Петрі алгоритмом класу PetriSim: input() – вхід маркерів у переходи з
 * розв'язанням конфліктів, getTimeMin() – момент найближчої події, output() – вихід маркерів з
 * усіх переходів, час виходу яких дорівнює поточному моменту. Порядок викликів той самий, що в
 * PetriObjModel.go() для одного Петрі-об'єкта. Додатково збираються статистики з урахуванням
 * періоду розгону, підраховуються входи й виходи переходів і виконується трасування подій.
 */
public final class Simulation {

    /** Середнє за часом довільної функції стану мережі та її розподіл за часом. */
    public static final class Watch {
        private final ToDoubleFunction<Simulation> fn;
        private double area;
        private double span;
        private final Map<Double, Double> histogram = new TreeMap<>();

        private Watch(ToDoubleFunction<Simulation> fn) {
            this.fn = fn;
        }

        private void add(Simulation s, double dt) {
            double v = fn.applyAsDouble(s);
            area += v * dt;
            span += dt;
            histogram.merge(v, dt, Double::sum);
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

    private final PetriSim sim;
    private final PetriP[] places;
    private final PetriT[] transitions;
    private final Map<String, Integer> placeIndex = new HashMap<>();
    private final Map<String, Integer> transitionIndex = new HashMap<>();
    private final double warmup;
    private final double[] markArea;
    private final double[] bufferArea;
    private final int[] entered;
    private final int[] fired;
    private final List<Watch> watches = new ArrayList<>();
    private final List<Consumer<Simulation>> afterEvent = new ArrayList<>();
    private final Map<PetriT, List<DoubleConsumer>> outActions = new HashMap<>();
    private final List<String> inLog = new ArrayList<>();
    private final List<String> outLog = new ArrayList<>();
    private double time;
    private long events;
    private int traceEvents;
    private Consumer<String> traceOut = s -> { };

    public Simulation(PetriNet net, double warmup) {
        this.sim = new PetriSim(net);
        this.places = net.getListP();
        this.transitions = net.getListT();
        this.warmup = warmup;
        for (int i = 0; i < places.length; i++) {
            placeIndex.put(places[i].getName(), i);
        }
        for (int i = 0; i < transitions.length; i++) {
            transitionIndex.put(transitions[i].getName(), i);
        }
        markArea = new double[places.length];
        bufferArea = new double[transitions.length];
        entered = new int[transitions.length];
        fired = new int[transitions.length];
        sim.setFiringListener(this::onFiring);
    }

    private void onFiring(FiringPhase phase, PetriT t, double now) {
        int i = transitionIndex.get(t.getName());
        if (phase == FiringPhase.AFTER_ACT_IN) {
            if (now >= warmup) {
                entered[i]++;
            }
            if (events < traceEvents) {
                inLog.add(t.getName());
            }
        } else if (phase == FiringPhase.AFTER_ACT_OUT) {
            if (now >= warmup) {
                fired[i]++;
            }
            if (events < traceEvents) {
                outLog.add(t.getName());
            }
            for (DoubleConsumer action : outActions.getOrDefault(t, List.of())) {
                action.accept(now);
            }
        }
    }

    public void run(double simulationTime) {
        sim.setSimulationTime(simulationTime);
        sim.setTimeCurr(0);
        time = 0;
        sim.input();
        finishEvent();
        while (true) {
            double next = sim.getTimeMin();
            advance(Math.min(next, simulationTime), simulationTime);
            if (next > simulationTime) {
                break;
            }
            sim.setTimeCurr(next);
            sim.output();
            sim.input();
            events++;
            finishEvent();
        }
    }

    private void finishEvent() {
        for (Consumer<Simulation> hook : afterEvent) {
            hook.accept(this);
        }
        if (events < traceEvents) {
            traceOut.accept(state());
        }
        inLog.clear();
        outLog.clear();
    }

    /** Статистика за інтервал [time, to): маркування між подіями не змінюється. */
    private void advance(double to, double simulationTime) {
        // середні значення бібліотеки – так само, як у PetriObjModel.go()
        if (to > 0) {
            double weight = to < simulationTime ? (to - time) / to : (simulationTime - time) / simulationTime;
            sim.doStatistics(weight);
        }
        double from = Math.max(time, warmup);
        if (to > from) {
            double dt = to - from;
            for (int i = 0; i < places.length; i++) {
                markArea[i] += places[i].getMark() * dt;
            }
            for (int i = 0; i < transitions.length; i++) {
                bufferArea[i] += transitions[i].getBuffer() * dt;
            }
            for (Watch w : watches) {
                w.add(this, dt);
            }
        }
        time = to;
    }

    private String state() {
        StringBuilder sb = new StringBuilder(String.format(Locale.ROOT, "t = %.4f%n", time));
        if (!outLog.isEmpty()) {
            sb.append("  вихід з переходів: ").append(grouped(outLog)).append('\n');
        }
        if (!inLog.isEmpty()) {
            sb.append("  вхід у переходи:   ").append(grouped(inLog)).append('\n');
        }
        List<String> marks = new ArrayList<>();
        for (PetriP p : places) {
            if (p.getMark() > 0) {
                marks.add(p.getName() + "=" + p.getMark());
            }
        }
        List<String> busy = new ArrayList<>();
        for (PetriT t : transitions) {
            if (t.getBuffer() > 0) {
                busy.add(String.format(Locale.ROOT, "%s=%d (вихід %.3f)", t.getName(), t.getBuffer(),
                        t.getMinTime()));
            }
        }
        sb.append("  маркування: ").append(String.join(", ", marks)).append('\n');
        sb.append("  у переходах: ").append(String.join(", ", busy)).append('\n');
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

    public void afterEvent(Consumer<Simulation> hook) {
        afterEvent.add(hook);
    }

    /** Дія, що виконується при кожному виході маркера з переходу (аргумент – момент виходу). */
    public void onOut(String transition, DoubleConsumer action) {
        outActions.computeIfAbsent(transitions[transitionIndex.get(transition)], k -> new ArrayList<>())
                .add(action);
    }

    public Watch watch(ToDoubleFunction<Simulation> fn) {
        Watch w = new Watch(fn);
        watches.add(w);
        return w;
    }

    public int mark(String place) {
        return places[placeIndex.get(place)].getMark();
    }

    public int buffer(String transition) {
        return transitions[transitionIndex.get(transition)].getBuffer();
    }

    public double meanMark(String place) {
        return markArea[placeIndex.get(place)] / period();
    }

    public double meanBuffer(String transition) {
        return bufferArea[transitionIndex.get(transition)] / period();
    }

    /** Середнє маркування, обчислене самою бібліотекою (PetriP.getMean, без розгону). */
    public double libraryMeanMark(String place) {
        return places[placeIndex.get(place)].getMean();
    }

    public double libraryMeanBuffer(String transition) {
        return transitions[transitionIndex.get(transition)].getMean();
    }

    public int fired(String transition) {
        return fired[transitionIndex.get(transition)];
    }

    public int entered(String transition) {
        return entered[transitionIndex.get(transition)];
    }

    public double time() {
        return time;
    }

    /** Тривалість збору статистики (без розгону). */
    public double period() {
        return time - warmup;
    }

    public long events() {
        return events;
    }

    public PetriP[] places() {
        return places;
    }

    public PetriT[] transitions() {
        return transitions;
    }
}
