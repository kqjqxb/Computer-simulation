package lab7;

import ua.stetsenkoinna.petriobj.PetriP;
import ua.stetsenkoinna.petriobj.PetriT;

/**
 * Петрі-об'єкт «Пристрій» конвеєрної системи. Деталь біля вільного пристрою знімається з конвеєра й
 * обробляється (пріоритет 1), інакше рухається конвеєром до позиції next за move хв (пріоритет 0).
 */
public final class Device extends PetriObject {

    public Device(int i, Delay service, String moveName, double move, String next) {
        super("Пристрій П" + i);
        PetriP part = net.place("Деталь біля П" + i, 0, 0, 0);
        PetriP free = net.place("П" + i + " вільний", 1, 240, -140);
        PetriP done = net.place("Оброблені деталі", 0, 240, -280);
        PetriP nextPart = net.place(next, 0, 240, 0);
        PetriT serve = net.transition("Обробка П" + i, service, 1, 120, -140);
        PetriT go = net.transition(moveName, move, 0, 120, 0);
        net.arc(part, serve);
        net.arc(free, serve);
        net.arc(serve, free);
        net.carry(serve, done, part);
        net.arc(part, go);
        net.carry(go, nextPart, part);
    }

    @Override
    public String kind() {
        return "Пристрій";
    }
}
