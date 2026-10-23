package lab7;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import ua.stetsenkoinna.petriobj.PetriP;
import ua.stetsenkoinna.petriobj.PetriT;

/**
 * Мітки маркерів. Маркери бібліотеки неіменовані, тому для вимірювання часу між подіями з однією
 * вимогою (деталлю, пасажиром) мітка – момент появи вимоги в моделі – зберігається поза мережею:
 * для кожної позиції, через яку проходить вимога, – черга міток у порядку надходження маркерів, для
 * кожного переходу – мітки маркерів у його каналах у тому самому порядку, що й моменти виходу в масиві
 * PetriT.getTimeOut(). Під час виходу маркера бібліотека повідомляє номер каналу (PetriT.getNum()),
 * тож мітки переходять разом з маркерами так само, як у мережі практикуму 5.
 */
final class Tracker {

    private final ObjectModel model;
    private final Map<PetriP, ArrayDeque<Double>> queues = new IdentityHashMap<>();
    private final Map<PetriT, List<double[][]>> channels = new IdentityHashMap<>();

    Tracker(ObjectModel model) {
        this.model = model;
        Set<PetriP> tracked = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
        for (PetriT t : model.transitions()) {
            for (Map.Entry<PetriP, PetriP> c : model.carries(t).entrySet()) {
                tracked.add(c.getKey());
                tracked.add(c.getValue());
            }
        }
        for (PetriP p : tracked) {
            ArrayDeque<Double> q = new ArrayDeque<>();
            for (int i = 0; i < p.getMark(); i++) {
                q.add(0.0);
            }
            queues.put(p, q);
        }
        for (PetriT t : model.transitions()) {
            channels.put(t, new ArrayList<>());
        }
    }

    /** Вхід маркера в перехід t: мітки забираються з черг вхідних позицій. */
    void in(PetriT t) {
        List<ObjectModel.Arc> in = model.inputs(t);
        double[][] taken = new double[in.size()][];
        for (int i = 0; i < in.size(); i++) {
            ObjectModel.Arc a = in.get(i);
            ArrayDeque<Double> q = queues.get(a.place());
            if (q != null && !a.info()) {
                taken[i] = new double[a.k()];
                for (int j = 0; j < a.k(); j++) {
                    taken[i][j] = q.removeFirst();
                }
            }
        }
        channels.get(t).add(taken);
    }

    /**
     * Вихід маркера з каналу t.getNum() переходу t у момент time: мітки переносяться у вихідні позиції
     * (або створюються нові з міткою time). Повертає мітку першої відстежуваної вхідної позиції.
     */
    double out(PetriT t, double time) {
        List<double[][]> ch = channels.get(t);
        double[][] taken = ch.remove(t.getNum());
        List<ObjectModel.Arc> in = model.inputs(t);
        Map<PetriP, PetriP> carry = model.carries(t);
        for (ObjectModel.Arc a : model.outputs(t)) {
            ArrayDeque<Double> q = queues.get(a.place());
            if (q == null) {
                continue;
            }
            double[] moved = null;
            PetriP from = carry.get(a.place());
            for (int i = 0; i < in.size() && from != null; i++) {
                if (in.get(i).place() == from) {
                    moved = taken[i];
                }
            }
            for (int j = 0; j < a.k(); j++) {
                q.add(moved != null && j < moved.length ? moved[j] : time);
            }
        }
        for (double[] stamps : taken) {
            if (stamps != null && stamps.length > 0) {
                return stamps[0];
            }
        }
        return Double.NaN;
    }

    /** Узгодженість з бібліотекою: міток стільки, скільки маркерів у позиціях і каналах переходів. */
    boolean consistent() {
        for (Map.Entry<PetriP, ArrayDeque<Double>> e : queues.entrySet()) {
            if (e.getKey().getMark() != e.getValue().size()) {
                return false;
            }
        }
        for (Map.Entry<PetriT, List<double[][]>> e : channels.entrySet()) {
            if (e.getKey().getBuffer() != e.getValue().size()) {
                return false;
            }
        }
        return true;
    }
}
