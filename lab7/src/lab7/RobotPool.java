package lab7;

import java.util.List;
import java.util.Map;

import ua.stetsenkoinna.petriobj.PetriP;
import ua.stetsenkoinna.petriobj.PetriT;

/**
 * Петрі-об'єкт «Парк роботів» (варіант 2): будь-який вільний робот може виконати переміщення на будь-якому
 * шляху. Позиція «Вільний робот: L» містить вільних роботів, що стоять у точці L. Виклик робота з точки L
 * на шлях з початком O триває стільки, скільки холостий хід з L до O, і має пріоритет 100 − відстань,
 * тому деталь отримує найближчий вільний робот. Усі виклики – переходи одного об'єкта, тож конфлікт за
 * робота між шляхами розв'язується за пріоритетами так само, як у мережі практикуму 5.
 */
public final class RobotPool extends PetriObject {

    /** Шлях переміщення: початок, позиція із запитом, позиція місць на верстаті призначення (або null). */
    public record Route(String name, String origin, String request, String slot) {
    }

    /** band – відстань між рядами шляхів на рисунку: виклики кожного шляху стоять у своєму ряду. */
    public RobotPool(Map<String, Double> position, Map<String, Integer> robots, List<Route> routes,
            double band) {
        super("Парк роботів");
        Map<String, PetriP> free = new java.util.LinkedHashMap<>();
        int i = 0;
        for (String loc : position.keySet()) {
            free.put(loc, net.place("Вільний робот: " + loc, robots.getOrDefault(loc, 0), 340 + 200 * (i % 2),
                    band / 2 + band * (i / 2)));
            i++;
        }
        int r = 0;
        for (Route route : routes) {
            double y = band * r++;
            PetriP req = net.place(route.request(), 0, -160, y);
            PetriP slot = route.slot() == null ? null : net.place(route.slot(), 0, -280, y + 150);
            PetriP atPart = net.place("Робот біля деталі (" + route.name() + ")", 0, 600, y);
            int j = 0;
            for (Map.Entry<String, PetriP> e : free.entrySet()) {
                double d = Math.abs(position.get(e.getKey()) - position.get(route.origin()));
                PetriT call = net.transition("Виклик робота: " + e.getKey() + " → " + route.name(), d,
                        (int) (100 - d), 220, y + (j++ - 1.5) * 125);
                net.arc(req, call);
                if (slot != null) {
                    net.arc(slot, call);
                }
                net.arc(e.getValue(), call);
                net.carry(call, atPart, req);
            }
        }
    }

    @Override
    public String kind() {
        return "Парк роботів";
    }
}
