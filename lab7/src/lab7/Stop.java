package lab7;

import ua.stetsenkoinna.petriobj.PetriP;
import ua.stetsenkoinna.petriobj.PetriT;

/**
 * Петрі-об'єкт «Зупинка»: пасажир, якого не забрав мікроавтобус, стає в чергу, якщо в ній є місце
 * (не більше limit осіб), інакше йде до іншого маршруту. Посадку виконують об'єкти «Мікроавтобус», що
 * мають вищий пріоритет, тому до черги потрапляє лише пасажир, який не зміг сісти.
 */
public final class Stop extends PetriObject {

    public Stop(int s, int limit) {
        super("Зупинка З" + s);
        PetriP passenger = net.place("Пасажир на З" + s, 0, 0, 0);
        PetriP queue = net.place("Черга на З" + s, 0, 240, 0);
        PetriP room = net.place("Місця в черзі З" + s, limit, 120, -130);
        PetriP left = net.place("Пішли з З" + s, 0, 240, 150);
        PetriT join = net.transition("Стати в чергу на З" + s, 0, 2, 120, 0);
        PetriT leave = net.transition("Відмова на З" + s, 0, 1, 120, 150);
        net.arc(passenger, join);
        net.arc(room, join);
        net.carry(join, queue, passenger);
        net.arc(passenger, leave);
        net.arc(leave, left);
    }

    @Override
    public String kind() {
        return "Зупинка";
    }
}
