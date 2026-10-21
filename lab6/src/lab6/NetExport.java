package lab6;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.ObjectOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;

import ua.stetsenkoinna.graphnet.GraphArcIn;
import ua.stetsenkoinna.graphnet.GraphArcOut;
import ua.stetsenkoinna.graphnet.GraphCanvasModel;
import ua.stetsenkoinna.graphnet.GraphPetriNet;
import ua.stetsenkoinna.graphnet.GraphPetriObjModel;
import ua.stetsenkoinna.graphnet.GraphPetriPlace;
import ua.stetsenkoinna.graphnet.GraphPetriTransition;
import ua.stetsenkoinna.graphpresentation.FileUse;
import ua.stetsenkoinna.petriobj.ArcIn;
import ua.stetsenkoinna.petriobj.ArcOut;
import ua.stetsenkoinna.petriobj.PetriNet;
import ua.stetsenkoinna.petriobj.PetriP;
import ua.stetsenkoinna.petriobj.PetriT;
import ua.stetsenkoinna.pnml.PnmlModelGenerator;
import ua.stetsenkoinna.pnml.PnmlModelParser;
import ua.stetsenkoinna.theme.CanvasColor;
import ua.stetsenkoinna.theme.CanvasPalette;

/**
 * Збереження мережі Петрі засобами графічного редактора PetriObjModelPaint: графічна мережа
 * GraphPetriNet (як після «Save Graph net», файл .pns) та її зображення .png, документ .pnml
 * (як «Save As…») і java-метод (як «Save net as method»).
 */
public final class NetExport {

    private static final int MARGIN = 60;
    private static final int SCALE = 2;

    private NetExport() {
    }

    static void main(Path netsDir, Path results) throws Exception {
        export(Conveyor.net(), netsDir.resolve("conveyor"), "conveyor", results);
        export(Inventory.net(), netsDir.resolve("inventory"), "inventory", results);
        export(Servers.net(Servers.Config.base()), netsDir.resolve("servers"), "servers", results);
    }

    static void export(NetBuilder builder, Path dir, String file, Path results) throws Exception {
        Files.createDirectories(dir);
        PetriNet net = builder.build();
        GraphPetriNet graph = graphNet(builder, net);

        graph.createPetriNet(net.getName());
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(dir.resolve(file + ".pns")
                .toFile()))) {
            oos.writeObject(graph);
        }
        ImageIO.write(paint(graph), "png", dir.resolve(file + ".png").toFile());
        File pnml = dir.resolve(file + ".pnml").toFile();
        new PnmlModelGenerator().generate(new GraphCanvasModel(net.getName(), graph).toObjModel(), pnml);
        Output.write(dir.resolve(file + "_method.txt"), new FileUse().saveNetAsMethod(graph));
        Output.write(results.resolve("net_" + file + ".json"), Json.write(describe(graph.getPetriNet())));

        GraphPetriObjModel parsed = new PnmlModelParser().parse(pnml);
        PetriNet back = GraphPetriObjModel.createPetriSim(parsed.getObject(0)).getNet();
        boolean same = signature(back).equals(signature(graph.getPetriNet()));
        System.out.printf("%s: %d позицій, %d переходів, %d вхідних і %d вихідних дуг; "
                + "мережа, прочитана з .pnml, %s з вихідною%n", dir.resolve(file), net.getListP().length,
                net.getListT().length, net.getArcIn().length, net.getArcOut().length, same ? "збігається" : "НЕ збігається");
        if (!same) {
            throw new IllegalStateException("PNML " + pnml);
        }
    }

    /** Графічна мережа з координатами, заданими в NetBuilder (так само будує її редактор). */
    static GraphPetriNet graphNet(NetBuilder b, PetriNet net) {
        Map<Object, Object> graphOf = new IdentityHashMap<>();
        ArrayList<GraphPetriPlace> places = new ArrayList<>();
        ArrayList<GraphPetriTransition> transitions = new ArrayList<>();
        int id = 0;
        for (PetriP p : net.getListP()) {
            GraphPetriPlace g = new GraphPetriPlace(p, id++);
            g.setNewCoordinates(b.position(p));
            places.add(g);
            graphOf.put(p, g);
        }
        for (PetriT t : net.getListT()) {
            GraphPetriTransition g = new GraphPetriTransition(t, id++);
            g.setNewCoordinates(b.position(t));
            transitions.add(g);
            graphOf.put(t, g);
        }
        ArrayList<GraphArcIn> arcsIn = new ArrayList<>();
        for (ArcIn arc : net.getArcIn()) {
            GraphArcIn g = new GraphArcIn(arc);
            g.settingNewArc(places.get(arc.getNumP()));
            g.finishSettingNewArc(transitions.get(arc.getNumT()));
            g.setPetriElements();
            g.changeBorder();
            arcsIn.add(g);
        }
        ArrayList<GraphArcOut> arcsOut = new ArrayList<>();
        for (ArcOut arc : net.getArcOut()) {
            GraphArcOut g = new GraphArcOut(arc);
            g.settingNewArc(transitions.get(arc.getNumT()));
            g.finishSettingNewArc(places.get(arc.getNumP()));
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

    /** Зображення мережі так, як її малює полотно редактора (GraphPetriNet.paintGraphPetriNet). */
    static BufferedImage paint(GraphPetriNet graph) {
        Rectangle box = null;
        for (GraphPetriPlace p : graph.getGraphPetriPlaceList()) {
            box = union(box, p.getBounds());
        }
        for (GraphPetriTransition t : graph.getGraphPetriTransitionList()) {
            box = union(box, t.getBounds());
        }
        int width = box.width + 2 * MARGIN + 80;
        int height = box.height + 2 * MARGIN + 40;
        BufferedImage image = new BufferedImage(width * SCALE, height * SCALE, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        Color background = CanvasPalette.current().get(CanvasColor.CANVAS_BACKGROUND);
        g.setColor(background);
        g.fillRect(0, 0, image.getWidth(), image.getHeight());
        g.scale(SCALE, SCALE);
        g.translate(MARGIN + 40 - box.x, MARGIN + 20 - box.y);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        graph.paintGraphPetriNet(g, g);
        g.dispose();
        return image;
    }

    private static Rectangle union(Rectangle box, Rectangle r) {
        return box == null ? new Rectangle(r) : box.union(r);
    }

    /** Опис структури мережі у форматі, яким описано мережі практикуму 5. */
    static Map<String, Object> describe(PetriNet net) {
        List<Object> places = new ArrayList<>();
        for (PetriP p : net.getListP()) {
            places.add(Json.obj("name", p.getName(), "mark", p.getMark()));
        }
        List<Object> transitions = new ArrayList<>();
        for (PetriT t : net.getListT()) {
            List<Object> in = new ArrayList<>();
            List<Object> info = new ArrayList<>();
            List<Object> out = new ArrayList<>();
            for (ArcIn a : net.getArcIn()) {
                if (a.getNumT() == t.getNumber()) {
                    (a.getIsInf() ? info : in).add(Json.obj("place", net.getListP()[a.getNumP()].getName(),
                            "n", a.getQuantity()));
                }
            }
            for (ArcOut a : net.getArcOut()) {
                if (a.getNumT() == t.getNumber()) {
                    out.add(Json.obj("place", net.getListP()[a.getNumP()].getName(), "n", a.getQuantity()));
                }
            }
            transitions.add(Json.obj("name", t.getName(),
                    "distribution", t.getDistribution() == null ? "const" : t.getDistribution(),
                    "mean", t.getParameter(), "priority", t.getPriority(), "probability", t.getProbability(),
                    "inputs", in, "info_inputs", info, "outputs", out));
        }
        return Json.obj("name", net.getName(), "places", places, "transitions", transitions);
    }

    /** Склад мережі без урахування порядку елементів: позиції, переходи та дуги як рядки. */
    static List<String> signature(PetriNet net) {
        List<String> items = new ArrayList<>();
        PetriP[] p = net.getListP();
        PetriT[] t = net.getListT();
        for (PetriP place : p) {
            items.add("P " + place.getName() + " " + place.getMark());
        }
        for (PetriT tr : t) {
            items.add("T " + tr.getName() + " " + tr.getParameter() + " " + tr.getDistribution() + " "
                    + tr.getPriority() + " " + tr.getProbability());
        }
        for (ArcIn a : net.getArcIn()) {
            items.add("I " + p[a.getNumP()].getName() + " " + t[a.getNumT()].getName() + " " + a.getQuantity()
                    + " " + a.getIsInf());
        }
        for (ArcOut a : net.getArcOut()) {
            items.add("O " + t[a.getNumT()].getName() + " " + p[a.getNumP()].getName() + " "
                    + a.getQuantity());
        }
        items.sort(null);
        return items;
    }
}
