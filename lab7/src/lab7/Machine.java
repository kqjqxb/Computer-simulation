package lab7;

import ua.stetsenkoinna.petriobj.PetriP;
import ua.stetsenkoinna.petriobj.PetriT;

/**
 * Петрі-об'єкт «Верстат»: одночасно обробляє до slots деталей. Позиція «Вільні місця» – ресурс
 * верстата, який займають і звільняють роботи (місце резервується при призначенні робота, що везе
 * деталь на верстат, і звільняється, коли наступний робот захоплює оброблену деталь).
 */
public final class Machine extends PetriObject {

    public Machine(String label, int slots, Delay processing) {
        super("Верстат " + label);
        PetriP in = net.place("Деталь на " + label, 0, 0, 0);
        net.place("Вільні місця " + label, slots, 120, -110);
        PetriP done = net.place("Оброблена на " + label, 0, 240, 0);
        PetriT t = net.transition("Обробка на " + label, processing, 120, 0);
        net.arc(in, t);
        net.carry(t, done, in);
    }

    @Override
    public String kind() {
        return "Верстат";
    }
}
