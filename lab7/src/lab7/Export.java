package lab7;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.geom.Point2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import javax.imageio.ImageIO;

import ua.stetsenkoinna.graphnet.GraphArcIn;
import ua.stetsenkoinna.graphnet.GraphArcOut;
import ua.stetsenkoinna.graphnet.GraphPetriNet;
import ua.stetsenkoinna.graphnet.GraphPetriObjModel;
import ua.stetsenkoinna.graphnet.GraphPetriObject;
import ua.stetsenkoinna.graphnet.GraphPetriPlace;
import ua.stetsenkoinna.graphnet.GraphPetriTransition;
import ua.stetsenkoinna.petriobj.ArcIn;
import ua.stetsenkoinna.petriobj.ArcOut;
import ua.stetsenkoinna.petriobj.PetriNet;
import ua.stetsenkoinna.petriobj.PetriObjLink;
import ua.stetsenkoinna.petriobj.PetriObjModel;
import ua.stetsenkoinna.petriobj.PetriP;
import ua.stetsenkoinna.petriobj.PetriSim;
import ua.stetsenkoinna.petriobj.PetriT;
import ua.stetsenkoinna.pnml.PnmlModelGenerator;
import ua.stetsenkoinna.pnml.PnmlModelParser;
import ua.stetsenkoinna.theme.CanvasColor;
import ua.stetsenkoinna.theme.CanvasPalette;

/**
 * Збереження Петрі-об'єктних моделей засобами PetriObjModelPaint: документ .pnml моделі (одна сторінка
 * на Петрі-об'єкт і перелік об'єднань позицій, як «Export PNML» редактора) з перевіркою зворотного
 * читання, зображення мережі кожного класу Петрі-об'єктів і мережі моделі з рамками об'єктів
 * (GraphPetriNet.paintGraphPetriNet), опис мережі моделі в JSON для порівняння з практикумом 5.
 */
public final class Export {

    private static final int MARGIN = 40;
    private static final int SCALE = 2;
    private static final Color FRAME_FILL = new Color(0xF2F5FA);
    private static final Color FRAME_BORDER = new Color(0x6F86A6);

    private Export() {
    }

    record Frame(Rectangle bounds, String title) {
    }

    static void main(Path nets, Path results) throws Exception {
        Map<String, Supplier<ObjectModel>> models = new LinkedHashMap<>();
        models.put("network", () -> QueueNetwork.model(0, QueueNetwork.P_BACK));
        models.put("fms_variant1", () -> Fms.model(1, Fms.ARRIVAL));
        models.put("fms_variant2", () -> Fms.model(2, Fms.ARRIVAL));
        models.put("minibus", () -> Minibus.model(1));
        models.put("conveyor", () -> Conveyor.model(true));
        Map<String, Integer> classes = new LinkedHashMap<>();
        for (Map.Entry<String, Supplier<ObjectModel>> e : models.entrySet()) {
            exportModel(e.getValue(), nets.resolve(e.getKey()), e.getKey(), results);
            ObjectModel m = e.getValue().get();
            for (PetriObject o : m.objects()) {
                String kind = o.getClass().getSimpleName();
                if (classes.merge(kind, 1, Integer::sum) == 1) {
                    Set<String> linked = interfacePlaces(o, m);
                    GraphPetriNet net = graphNet(o.builder(), o.builder().build(), Map.of(), linked);
                    File png = nets.resolve("objects").resolve(kind + ".png").toFile();
                    ImageIO.write(paint(net, List.of()), "png", png);
                }
            }
        }
        System.out.printf("Зображення мереж %d класів Петрі-об'єктів: %s%n", classes.size(),
                nets.resolve("objects"));
    }

    /** Позиції об'єкта, що беруть участь в об'єднаннях позицій моделі (рисуються сірими, як у редакторі). */
    private static Set<String> interfacePlaces(PetriObject o, ObjectModel m) {
        Set<String> names = new LinkedHashSet<>();
        for (ObjectModel.Link l : m.links()) {
            if (l.copy() == o) {
                names.add(l.copyPlace());
            }
            if (l.source() == o) {
                names.add(l.sourcePlace());
            }
        }
        return names;
    }

    static void exportModel(Supplier<ObjectModel> factory, Path dir, String file, Path results)
            throws Exception {
        Files.createDirectories(dir);
        Files.createDirectories(dir.getParent().resolve("objects"));
        ObjectModel m = factory.get();
        m.build();
        Output.write(results.resolve("net_" + file + ".json"), Json.write(describe(m)));
        ImageIO.write(paintModel(m), "png", dir.resolve(file + ".png").toFile());

        // документ моделі будується з мереж об'єктів до об'єднання позицій
        ObjectModel fresh = factory.get();
        GraphPetriObjModel graph = new GraphPetriObjModel(fresh.name());
        for (PetriObject o : fresh.objects()) {
            Point2D origin = fresh.origin(o);
            Map<Object, Point2D> at = new IdentityHashMap<>();
            for (PetriP p : o.builder().places()) {
                at.put(p, shift(o.builder().position(p), origin));
            }
            for (PetriT t : o.builder().transitions()) {
                at.put(t, shift(o.builder().position(t), origin));
            }
            GraphPetriNet net = graphNet(o.builder(), o.builder().build(), at, Set.of());
            Rectangle box = bounds(net);
            GraphPetriObject go = new GraphPetriObject(o.name(), net, o.priority(),
                    new Point(box.x - 60, box.y - 60), null);
            go.setSize(box.width + 120, box.height + 120);
            graph.addObject(go);
        }
        for (ObjectModel.Link l : fresh.links()) {
            graph.addLink(PetriObjLink.placeFusion(fresh.objects().indexOf(l.copy()),
                    l.copy().placeIndex(l.copyPlace()), fresh.objects().indexOf(l.source()),
                    l.source().placeIndex(l.sourcePlace())));
        }
        File pnml = dir.resolve(file + ".pnml").toFile();
        new PnmlModelGenerator().generate(graph, pnml);

        PetriObjModel back = new PnmlModelParser().parse(pnml).createPetriObjModel(file);
        boolean same = signature(back.getListObj()).equals(signature(m.model().getListObj()));
        System.out.printf("%s: %d Петрі-об'єктів, %d об'єднань позицій, мережа моделі – %d позицій і %d "
                + "переходів; модель, прочитана з .pnml, %s з вихідною%n", dir.resolve(file),
                m.objects().size(), m.links().size(), m.places().size(), m.transitions().size(),
                same ? "збігається" : "НЕ збігається");
        if (!same) {
            throw new IllegalStateException("PNML " + pnml);
        }
    }

    private static Point2D shift(Point2D p, Point2D origin) {
        return new Point2D.Double(p.getX() + origin.getX(), p.getY() + origin.getY());
    }

    /** Мережа моделі: кожна спільна позиція рисується один раз – там, де її розміщує об'єкт-джерело. */
    static BufferedImage paintModel(ObjectModel m) {
        NetBuilder flat = new NetBuilder(m.name());
        Map<PetriP, PetriP> places = new IdentityHashMap<>();
        Map<PetriT, PetriT> transitions = new IdentityHashMap<>();
        Map<Object, PetriObject> owner = new IdentityHashMap<>();
        for (PetriP p : m.places()) {
            PetriObject o = m.owner(p);
            Point2D at = m.drawAt(p.getName(), shift(o.builder().position(p), m.origin(o)));
            PetriP q = flat.place(p.getName(), p.getMark(), at.getX(), at.getY());
            places.put(p, q);
            owner.put(q, o);
        }
        for (PetriT t : m.transitions()) {
            PetriObject o = m.owner(t);
            Point2D at = m.drawAt(t.getName(), shift(o.builder().position(t), m.origin(o)));
            Delay d = new Delay(t.getDistribution(), t.getParameter(), t.getParamDeviation());
            PetriT u = flat.transition(t.getName(), d, t.getPriority(), at.getX(), at.getY());
            u.setProbability(t.getProbability());
            transitions.put(t, u);
            owner.put(u, o);
            for (ObjectModel.Arc a : m.inputs(t)) {
                if (a.info()) {
                    flat.arcsIn().add(new ArcIn(places.get(a.place()), u, a.k(), true));
                } else {
                    flat.arc(places.get(a.place()), u, a.k());
                }
            }
            for (ObjectModel.Arc a : m.outputs(t)) {
                flat.arc(u, places.get(a.place()), a.k());
            }
        }
        Set<String> shared = new LinkedHashSet<>();
        for (ObjectModel.Link l : m.links()) {
            shared.add(l.sourcePlace());
        }
        GraphPetriNet graph = graphNet(flat, flat.build(), Map.of(), shared);
        Map<PetriObject, Rectangle> boxes = new LinkedHashMap<>();
        for (GraphPetriPlace g : graph.getGraphPetriPlaceList()) {
            boxes.merge(owner.get(g.getPetriPlace()), g.getBounds(), Rectangle::union);
        }
        for (GraphPetriTransition g : graph.getGraphPetriTransitionList()) {
            boxes.merge(owner.get(g.getPetriTransition()), g.getBounds(), Rectangle::union);
        }
        List<Frame> frames = new ArrayList<>();
        for (PetriObject o : m.objects()) {
            Rectangle r = new Rectangle(boxes.get(o));
            r.grow(46, 0);
            r.y -= 44;
            r.height += 44 + 56;
            frames.add(new Frame(r, o.name() + (o.priority() != 0 ? ", пріоритет " + o.priority() : "")));
        }
        return paint(graph, frames);
    }

    /** Графічна мережа з координатами елементів (так само будує її редактор). */
    static GraphPetriNet graphNet(NetBuilder b, PetriNet net, Map<Object, Point2D> at, Set<String> linked) {
        Map<PetriP, GraphPetriPlace> placeOf = new IdentityHashMap<>();
        Map<PetriT, GraphPetriTransition> transitionOf = new IdentityHashMap<>();
        ArrayList<GraphPetriPlace> places = new ArrayList<>();
        ArrayList<GraphPetriTransition> transitions = new ArrayList<>();
        int id = 0;
        for (PetriP p : net.getListP()) {
            GraphPetriPlace g = new GraphPetriPlace(p, id++);
            g.setNewCoordinates(at.getOrDefault(p, b.position(p)));
            g.setLinkedToAnotherPlace(linked.contains(p.getName()));
            places.add(g);
            placeOf.put(p, g);
        }
        for (PetriT t : net.getListT()) {
            GraphPetriTransition g = new GraphPetriTransition(t, id++);
            g.setNewCoordinates(at.getOrDefault(t, b.position(t)));
            transitions.add(g);
            transitionOf.put(t, g);
        }
        ArrayList<GraphArcIn> arcsIn = new ArrayList<>();
        for (ArcIn arc : net.getArcIn()) {
            GraphArcIn g = new GraphArcIn(arc);
            g.settingNewArc(placeOf.get(net.getListP()[arc.getNumP()]));
            g.finishSettingNewArc(transitionOf.get(net.getListT()[arc.getNumT()]));
            g.setPetriElements();
            g.changeBorder();
            arcsIn.add(g);
        }
        ArrayList<GraphArcOut> arcsOut = new ArrayList<>();
        for (ArcOut arc : net.getArcOut()) {
            GraphArcOut g = new GraphArcOut(arc);
            g.settingNewArc(transitionOf.get(net.getListT()[arc.getNumT()]));
            g.finishSettingNewArc(placeOf.get(net.getListP()[arc.getNumP()]));
            g.setPetriElements();
            g.changeBorder();
            arcsOut.add(g);
            // зустрічні дуги між тими самими елементами редактор розводить паралельно
            for (GraphArcIn in : arcsIn) {
                if (in.getBeginElement() == g.getEndElement() && in.getEndElement() == g.getBeginElement()) {
                    in.twoArcs(g);
                }
            }
        }
        return new GraphPetriNet(net, places, transitions, arcsIn, arcsOut);
    }

    private static Rectangle bounds(GraphPetriNet graph) {
        Rectangle box = null;
        for (GraphPetriPlace p : graph.getGraphPetriPlaceList()) {
            box = box == null ? new Rectangle(p.getBounds()) : box.union(p.getBounds());
        }
        for (GraphPetriTransition t : graph.getGraphPetriTransitionList()) {
            box = box == null ? new Rectangle(t.getBounds()) : box.union(t.getBounds());
        }
        return box;
    }

    /** Зображення мережі так, як її малює полотно редактора, з рамками Петрі-об'єктів під нею. */
    static BufferedImage paint(GraphPetriNet graph, List<Frame> frames) {
        Rectangle box = bounds(graph);
        box = new Rectangle(box.x - 70, box.y - 40, box.width + 140, box.height + 100);
        for (Frame f : frames) {
            box = box.union(f.bounds());
        }
        int width = box.width + 2 * MARGIN;
        int height = box.height + 2 * MARGIN;
        BufferedImage image = new BufferedImage(width * SCALE, height * SCALE, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(CanvasPalette.current().get(CanvasColor.CANVAS_BACKGROUND));
        g.fillRect(0, 0, image.getWidth(), image.getHeight());
        g.scale(SCALE, SCALE);
        g.translate(MARGIN - box.x, MARGIN - box.y);
        for (Frame f : frames) {
            Rectangle r = f.bounds();
            RoundRectangle2D shape = new RoundRectangle2D.Double(r.x, r.y, r.width, r.height, 18, 18);
            g.setColor(FRAME_FILL);
            g.fill(shape);
            g.setColor(FRAME_BORDER);
            g.setStroke(new BasicStroke(1.4f));
            g.draw(shape);
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
            g.drawString(f.title(), r.x + 10, r.y + 17);
        }
        g.setStroke(new BasicStroke(1f));
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        graph.paintGraphPetriNet(g, g);
        g.dispose();
        return image;
    }

    /** Опис мережі моделі (після об'єднання позицій), Петрі-об'єктів і зв'язків. */
    static Map<String, Object> describe(ObjectModel m) {
        List<Object> places = new ArrayList<>();
        for (PetriP p : m.places()) {
            places.add(Json.obj("name", p.getName(), "mark", p.getMark(), "object", m.owner(p).name()));
        }
        List<Object> transitions = new ArrayList<>();
        for (PetriT t : m.transitions()) {
            List<Object> in = new ArrayList<>();
            List<Object> info = new ArrayList<>();
            List<Object> out = new ArrayList<>();
            for (ObjectModel.Arc a : m.inputs(t)) {
                (a.info() ? info : in).add(Json.obj("place", a.place().getName(), "n", a.k()));
            }
            for (ObjectModel.Arc a : m.outputs(t)) {
                Map<String, Object> arc = Json.obj("place", a.place().getName(), "n", a.k());
                PetriP from = m.carries(t).get(a.place());
                if (from != null) {
                    arc.put("carry", from.getName());
                }
                out.add(arc);
            }
            transitions.add(Json.obj("name", t.getName(), "object", m.owner(t).name(),
                    "distribution", t.getDistribution() == null ? "const" : t.getDistribution(),
                    "mean", t.getParameter(), "deviation", t.getParamDeviation(), "priority", t.getPriority(),
                    "probability", t.getProbability(), "inputs", in, "info_inputs", info, "outputs", out));
        }
        List<Object> objects = new ArrayList<>();
        for (PetriObject o : m.objects()) {
            objects.add(Json.obj("name", o.name(), "class", o.kind(), "priority", o.priority(),
                    "places", o.builder().places().stream().map(PetriP::getName).toList(),
                    "transitions", o.builder().transitions().stream().map(PetriT::getName).toList()));
        }
        List<Object> links = new ArrayList<>();
        for (ObjectModel.Link l : m.links()) {
            links.add(Json.obj("copy", l.copy().name(), "copy_place", l.copyPlace(),
                    "source", l.source().name(), "source_place", l.sourcePlace()));
        }
        return Json.obj("name", m.name(), "objects", objects, "links", links, "places", places,
                "transitions", transitions);
    }

    /** Склад мережі моделі без урахування порядку: позиції, переходи, дуги (позиції – екземпляри PetriP). */
    static List<String> signature(List<PetriSim> sims) {
        Set<PetriP> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        List<String> items = new ArrayList<>();
        for (PetriSim s : sims) {
            PetriP[] p = s.getNet().getListP();
            PetriT[] t = s.getNet().getListT();
            for (PetriP place : p) {
                if (seen.add(place)) {
                    items.add("P " + place.getName() + " " + place.getMark());
                }
            }
            for (PetriT tr : t) {
                items.add("T " + tr.getName() + " " + tr.getParameter() + " " + tr.getDistribution() + " "
                        + tr.getParamDeviation() + " " + tr.getPriority() + " " + tr.getProbability());
            }
            for (ArcIn a : s.getNet().getArcIn()) {
                items.add("I " + p[a.getNumP()].getName() + " " + t[a.getNumT()].getName() + " "
                        + a.getQuantity() + " " + a.getIsInf());
            }
            for (ArcOut a : s.getNet().getArcOut()) {
                items.add("O " + t[a.getNumT()].getName() + " " + p[a.getNumP()].getName() + " "
                        + a.getQuantity());
            }
        }
        items.sort(null);
        return items;
    }
}
