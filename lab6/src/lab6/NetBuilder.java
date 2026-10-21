package lab6;

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
 * разом з координатами елементів для графічного зображення мережі.
 */
public final class NetBuilder {

    private final String name;
    private final List<PetriP> places = new ArrayList<>();
    private final List<PetriT> transitions = new ArrayList<>();
    private final List<ArcIn> arcsIn = new ArrayList<>();
    private final List<ArcOut> arcsOut = new ArrayList<>();
    private final Map<Object, Point2D> layout = new IdentityHashMap<>();

    public NetBuilder(String name) {
        this.name = name;
        // номери позицій, переходів і дуг бібліотека бере з глобальних лічильників
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

    /** Перехід зі сталою затримкою delay. */
    public PetriT transition(String transitionName, double delay, double x, double y) {
        PetriT t = new PetriT(transitionName, delay);
        transitions.add(t);
        layout.put(t, new Point2D.Double(x, y));
        return t;
    }

    /** Перехід з експоненційно розподіленою затримкою із середнім mean. */
    public PetriT expTransition(String transitionName, double mean, double x, double y) {
        PetriT t = transition(transitionName, mean, x, y);
        t.setDistribution("exp", mean);
        return t;
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

    /** Інформаційна дуга: перевіряє наявність k маркерів у позиції, але не забирає їх. */
    public void info(PetriP p, PetriT t, int k) {
        arcsIn.add(new ArcIn(p, t, k, true));
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

    public Point2D position(Object element) {
        return layout.get(element);
    }
}
