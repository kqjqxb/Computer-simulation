package lab7;

import ua.stetsenkoinna.petriobj.PetriP;
import ua.stetsenkoinna.petriobj.PetriT;

/**
 * Петрі-об'єкт «Закріплений робот» (варіант 1): робот k обслуговує лише свій шлях route. Вільний робот
 * призначається на деталь з позиції request, якщо на верстаті призначення є вільне місце (slot, якщо
 * є; місце резервується), а після вивільнення деталі повертається на початок шляху за back с.
 */
public final class AssignedRobot extends PetriObject {

    public AssignedRobot(int k, String route, double back, String request, String slot) {
        super("Робот " + k);
        PetriP req = net.place(request, 0, 0, 0);
        PetriP free = net.place("Робот " + k + " вільний", 1, 0, 330);
        PetriP returning = net.place("Робот " + k + " повертається", 0, 330, 330);
        PetriP atPart = net.place("Робот біля деталі (" + route + ")", 0, 400, 0);
        PetriT assign = net.transition("Призначення робота " + k, 0, 140, 170);
        PetriT ret = net.transition("Повернення робота " + k, back, 170, 330);
        net.arc(req, assign);
        if (slot != null) {
            net.arc(net.place(slot, 0, 0, 170), assign);
        }
        net.arc(free, assign);
        net.carry(assign, atPart, req);
        net.arc(returning, ret);
        net.arc(ret, free);
    }

    @Override
    public String kind() {
        return "Закріплений робот";
    }
}
