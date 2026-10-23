package lab7;

import ua.stetsenkoinna.petriobj.PetriP;
import ua.stetsenkoinna.petriobj.PetriT;

/**
 * Петрі-об'єкт «Генератор»: перехід із затримкою, що повертає маркер у свою вхідну позицію, створює
 * вимогу в позиції output через інтервали delay. Початкове маркування позиції генератора 0 вимикає
 * генератор (режим необмеженого надходження, коли вимоги заздалегідь лежать у позиції output).
 */
public final class Generator extends PetriObject {

    public Generator(String name, String source, String transition, String output, Delay delay,
                     int sourceMark, int outputMark) {
        super(name);
        PetriP gen = net.place(source, sourceMark, 0, 0);
        PetriP out = net.place(output, outputMark, 260, 0);
        PetriT t = net.transition(transition, delay, 130, 0);
        net.arc(gen, t);
        net.arc(t, gen);
        net.arc(t, out);
    }

    public Generator(String name, String source, String transition, String output, Delay delay) {
        this(name, source, transition, output, delay, 1, 0);
    }

    @Override
    public String kind() {
        return "Генератор";
    }
}
