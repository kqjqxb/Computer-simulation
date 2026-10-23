package lab7;

import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import ua.stetsenkoinna.petriobj.ArcIn;
import ua.stetsenkoinna.petriobj.ArcOut;
import ua.stetsenkoinna.petriobj.ExceptionInvalidTimeDelay;
import ua.stetsenkoinna.petriobj.PetriNet;
import ua.stetsenkoinna.petriobj.PetriP;
import ua.stetsenkoinna.petriobj.PetriT;

/**
 * Побудова мережі Петрі з класів бібліотеки PetriObjModelPaint (PetriP, PetriT, ArcIn, ArcOut)
 * разом з координатами елементів для графічного зображення і переліком дуг, що переносять
 * мітку маркера (момент його появи в моделі) з вхідної позиції у вихідну.
 */
public final class NetBuilder {

    /** Вихідна дуга переходу t у позицію to переносить мітку маркера, взятого з позиції from. */
    public record Carry(PetriT t, PetriP to, PetriP from) {
    }

    private final String name;
    private final List<PetriP> places = new ArrayList<>();
    private final List<PetriT> transitions = new ArrayList<>();
    private final List<ArcIn> arcsIn = new ArrayList<>();
    private final List<ArcOut> arcsOut = new ArrayList<>();
    private final List<Carry> carries = new ArrayList<>();
    private final Map<Object, Point2D> layout = new IdentityHashMap<>();

    public NetBuilder(String name) {
        this.name = name;
        // номери позицій і переходів бібліотека бере з глобальних лічильників, а дуги посилаються
        // на ці номери, тому кожна мережа нумерується з нуля
        PetriP.initNext();
        PetriT.initNext();
        ArcIn.initNext();
        ArcOut.initNext();
    }

    public PetriP place(String placeName, int mark, double x, double y) {
        PetriP p = new PetriP(placeName, mark);
        places.add(p);
        layout.put(p, new Point2D.Double(x, y));
        return p;
    }

    /** Перехід зі сталою затримкою delay і пріоритетом priority. */
    public PetriT transition(String transitionName, double delay, int priority, double x, double y) {
        PetriT t = new PetriT(transitionName, delay);
        t.setPriority(priority);
        transitions.add(t);
        layout.put(t, new Point2D.Double(x, y));
        return t;
    }

    public PetriT transition(String transitionName, double delay, double x, double y) {
        return transition(transitionName, delay, 0, x, y);
    }

    /** Перехід із затримкою delay (стала або випадкова) і пріоритетом priority. */
    public PetriT transition(String transitionName, Delay delay, int priority, double x, double y) {
        PetriT t = transition(transitionName, delay.mean(), priority, x, y);
        if (delay.distribution() != null) {
            t.setDistribution(delay.distribution(), delay.mean());
            t.setParamDeviation(delay.dev());
        }
        return t;
    }

    public PetriT transition(String transitionName, Delay delay, double x, double y) {
        return transition(transitionName, delay, 0, x, y);
    }

    public void arc(PetriP p, PetriT t, int k) {
        arcsIn.add(new ArcIn(p, t, k));
    }

    public void arc(PetriP p, PetriT t) {
        arc(p, t, 1);
    }

    public void arc(PetriT t, PetriP p, int k) {
        arcsOut.add(new ArcOut(t, p, k));
    }

    public void arc(PetriT t, PetriP p) {
        arc(t, p, 1);
    }

    /** Вихідна дуга t → to, що переносить у позицію to мітки маркерів, взятих переходом з позиції from. */
    public void carry(PetriT t, PetriP to, int k, PetriP from) {
        arc(t, to, k);
        carries.add(new Carry(t, to, from));
    }

    public void carry(PetriT t, PetriP to, PetriP from) {
        carry(t, to, 1, from);
    }

    public PetriNet build() {
        try {
            return new PetriNet(name, new ArrayList<>(places), new ArrayList<>(transitions),
                    new ArrayList<>(arcsIn), new ArrayList<>(arcsOut));
        } catch (ExceptionInvalidTimeDelay e) {
            throw new IllegalStateException(e);
        }
    }

    public String name() {
        return name;
    }

    public List<PetriP> places() {
        return places;
    }

    public List<PetriT> transitions() {
        return transitions;
    }

    public List<ArcIn> arcsIn() {
        return arcsIn;
    }

    public List<ArcOut> arcsOut() {
        return arcsOut;
    }

    public List<Carry> carries() {
        return carries;
    }

    public Point2D position(Object element) {
        return layout.get(element);
    }

    public void move(Object element, double x, double y) {
        layout.put(element, new Point2D.Double(x, y));
    }
}
