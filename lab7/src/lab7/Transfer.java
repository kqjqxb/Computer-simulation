package lab7;

import ua.stetsenkoinna.petriobj.PetriP;
import ua.stetsenkoinna.petriobj.PetriT;

/**
 * Петрі-об'єкт «Переміщення деталі» на шляху route: робот, що прибув до деталі, захоплює її (8 ± 1 с),
 * переміщується до пункту призначення (move с) і вивільняє деталь (8 ± 1 с). Захоплення звільняє місце
 * на верстаті, з якого забрано деталь (freed, якщо є); після вивільнення деталь потрапляє в позицію
 * result, а робот – у позицію robot (повертається або чекає на новий виклик залежно від закріплення).
 */
public final class Transfer extends PetriObject {

    public Transfer(String route, double move, Delay handle, String freed, String result, String robot) {
        super("Шлях " + route);
        PetriP atPart = net.place("Робот біля деталі (" + route + ")", 0, 0, 0);
        PetriP held = net.place("Деталь захоплена (" + route + ")", 0, 340, 0);
        PetriP delivered = net.place("Деталь доставлена (" + route + ")", 0, 680, 0);
        PetriP out = net.place(result, 0, 1020, 0);
        PetriP back = net.place(robot, 0, 1020, 140);
        PetriT grab = net.transition("Захоплення (" + route + ")", handle, 170, 0);
        PetriT go = net.transition("Переміщення (" + route + ")", Delay.fixed(move), 510, 0);
        PetriT release = net.transition("Вивільнення (" + route + ")", handle, 850, 0);
        net.arc(atPart, grab);
        net.carry(grab, held, atPart);
        if (freed != null) {
            net.arc(grab, net.place(freed, 0, 170, 140));
        }
        net.arc(held, go);
        net.carry(go, delivered, held);
        net.arc(delivered, release);
        net.carry(release, out, delivered);
        net.arc(release, back);
    }

    @Override
    public String kind() {
        return "Переміщення деталі";
    }
}
