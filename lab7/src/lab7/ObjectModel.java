package lab7;

import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import ua.stetsenkoinna.petriobj.ArcIn;
import ua.stetsenkoinna.petriobj.ArcOut;
import ua.stetsenkoinna.petriobj.PetriNet;
import ua.stetsenkoinna.petriobj.PetriObjModel;
import ua.stetsenkoinna.petriobj.PetriP;
import ua.stetsenkoinna.petriobj.PetriSim;
import ua.stetsenkoinna.petriobj.PetriT;

/**
 * Петрі-об'єктна модель: список Петрі-об'єктів і зв'язки між ними. Зв'язок – об'єднання позицій
 * (PetriObjModel.linkObjectsCombiningPlaces): позиція-копія одного об'єкта стає тим самим екземпляром
 * PetriP, що й позиція-джерело іншого. Після збирання модель надає доступ до елементів за іменами і
 * опис мережі, яку утворює об'єднання мереж усіх об'єктів.
 */
public final class ObjectModel {

    /** Зв'язок: позиція copyPlace об'єкта copy збігається з позицією sourcePlace об'єкта source. */
    public record Link(PetriObject copy, String copyPlace, PetriObject source, String sourcePlace) {
    }

    /** Дуга мережі моделі після об'єднання позицій. */
    public record Arc(PetriP place, PetriT transition, int k, boolean info) {
    }

    private final String name;
    private final List<PetriObject> objects = new ArrayList<>();
    private final Map<PetriObject, Point2D> origins = new IdentityHashMap<>();
    private final List<Link> links = new ArrayList<>();
    private final Map<String, Point2D> drawAt = new LinkedHashMap<>();
    private PetriObjModel model;
    private final Map<String, PetriP> places = new LinkedHashMap<>();
    private final Map<String, PetriT> transitions = new LinkedHashMap<>();
    private final Map<PetriT, PetriObject> transitionOwner = new IdentityHashMap<>();
    private final Map<PetriP, PetriObject> placeOwner = new IdentityHashMap<>();
    private final Map<PetriT, List<Arc>> inputs = new IdentityHashMap<>();
    private final Map<PetriT, List<Arc>> outputs = new IdentityHashMap<>();
    private final Map<PetriT, Map<PetriP, PetriP>> carries = new IdentityHashMap<>();

    public ObjectModel(String name) {
        this.name = name;
    }

    /** Додає об'єкт; (x, y) – зсув його мережі на загальному зображенні моделі. */
    public <O extends PetriObject> O add(O object, double x, double y) {
        objects.add(object);
        origins.put(object, new Point2D.Double(x, y));
        return object;
    }

    public void link(PetriObject copy, String copyPlace, PetriObject source, String sourcePlace) {
        source.placeIndex(sourcePlace);
        if (copy.place(copyPlace).getMark() != 0) {
            // маркування позиції-копії втрачається, початкове маркування задає джерело
            throw new IllegalArgumentException("позиція-копія з маркерами: " + copyPlace);
        }
        for (Link l : links) {
            if (l.copy() == source && l.copyPlace().equals(sourcePlace)) {
                throw new IllegalArgumentException("ланцюжок об'єднань: " + sourcePlace);
            }
        }
        links.add(new Link(copy, copyPlace, source, sourcePlace));
    }

    /**
     * Об'єднує з позицією placeName об'єкта source однойменні позиції всіх інших об'єктів моделі
     * (у кожному об'єкті позиція спільного маркування має те саме ім'я, що й у джерела).
     */
    public void share(PetriObject source, String... placeNames) {
        for (String placeName : placeNames) {
            boolean linked = false;
            for (PetriObject o : objects) {
                if (o != source && o.hasPlace(placeName)) {
                    link(o, placeName, source, placeName);
                    linked = true;
                }
            }
            if (!linked) {
                throw new IllegalArgumentException("позицію «" + placeName
                        + "» не використовує жоден інший об'єкт");
            }
        }
    }

    /** Збирає PetriObjModel: створює імітатори об'єктів і об'єднує позиції. */
    public PetriObjModel build() {
        if (model != null) {
            return model;
        }
        ArrayList<PetriSim> sims = new ArrayList<>();
        for (PetriObject o : objects) {
            sims.add(o.sim());
        }
        model = new PetriObjModel(sims);
        model.setIsProtokol(false);
        for (Link l : links) {
            model.linkObjectsCombiningPlaces(objects.indexOf(l.copy()), l.copy().placeIndex(l.copyPlace()),
                    objects.indexOf(l.source()), l.source().placeIndex(l.sourcePlace()));
        }
        index();
        return model;
    }

    private void index() {
        for (PetriObject o : objects) {
            for (PetriP p : o.sim().getNet().getListP()) {
                if (o.builder().places().contains(p)) {
                    put(places, p.getName(), p);
                    placeOwner.put(p, o);
                }
            }
        }
        for (PetriObject o : objects) {
            PetriNet net = o.sim().getNet();
            PetriP[] p = net.getListP();
            PetriT[] t = net.getListT();
            for (PetriT tr : t) {
                put(transitions, tr.getName(), tr);
                transitionOwner.put(tr, o);
                inputs.put(tr, new ArrayList<>());
                outputs.put(tr, new ArrayList<>());
                carries.put(tr, new IdentityHashMap<>());
            }
            for (ArcIn a : net.getArcIn()) {
                inputs.get(t[a.getNumT()]).add(new Arc(p[a.getNumP()], t[a.getNumT()], a.getQuantity(),
                        a.getIsInf()));
            }
            for (ArcOut a : net.getArcOut()) {
                outputs.get(t[a.getNumT()]).add(new Arc(p[a.getNumP()], t[a.getNumT()],
                        a.getQuantity(), false));
            }
            // мітки переносяться між позиціями моделі, тому позиції-копії замінюються джерелами
            for (NetBuilder.Carry c : o.builder().carries()) {
                carries.get(c.t()).put(resolve(o, c.to()), resolve(o, c.from()));
            }
        }
    }

    private static PetriP resolve(PetriObject o, PetriP local) {
        return o.sim().getNet().getListP()[o.builder().places().indexOf(local)];
    }

    private static <T> void put(Map<String, T> map, String key, T value) {
        if (map.put(key, value) != null) {
            throw new IllegalStateException("неоднозначне ім'я елемента моделі: " + key);
        }
    }

    public String name() {
        return name;
    }

    public List<PetriObject> objects() {
        return objects;
    }

    public List<Link> links() {
        return links;
    }

    /** Інше місце елемента на загальному зображенні моделі (абсолютні координати). */
    public void drawAt(String element, double x, double y) {
        drawAt.put(element, new Point2D.Double(x, y));
    }

    public Point2D drawAt(String element, Point2D fallback) {
        return drawAt.getOrDefault(element, fallback);
    }

    public Point2D origin(PetriObject o) {
        return origins.get(o);
    }

    public PetriP place(String placeName) {
        PetriP p = places.get(placeName);
        if (p == null) {
            throw new IllegalArgumentException("немає позиції «" + placeName + "»");
        }
        return p;
    }

    public PetriT transition(String transitionName) {
        PetriT t = transitions.get(transitionName);
        if (t == null) {
            throw new IllegalArgumentException("немає переходу «" + transitionName + "»");
        }
        return t;
    }

    /** Позиції моделі без копій (кожна спільна позиція – один раз, під іменем джерела). */
    public List<PetriP> places() {
        return new ArrayList<>(places.values());
    }

    public List<PetriT> transitions() {
        return new ArrayList<>(transitions.values());
    }

    public PetriObject owner(PetriT t) {
        return transitionOwner.get(t);
    }

    public PetriObject owner(PetriP p) {
        return placeOwner.get(p);
    }

    public List<Arc> inputs(PetriT t) {
        return inputs.get(t);
    }

    public List<Arc> outputs(PetriT t) {
        return outputs.get(t);
    }

    /** Для переходу t: вихідна позиція → вхідна позиція, з якої переноситься мітка маркера. */
    public Map<PetriP, PetriP> carries(PetriT t) {
        return carries.get(t);
    }

    public PetriObjModel model() {
        return model;
    }
}
