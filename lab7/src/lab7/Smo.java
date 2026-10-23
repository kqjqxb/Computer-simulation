package lab7;

import ua.stetsenkoinna.petriobj.PetriP;
import ua.stetsenkoinna.petriobj.PetriT;

/**
 * Петрі-об'єкт «СМО»: система масового обслуговування з channels каналами, експоненційним часом
 * обслуговування і необмеженою чергою. Позиція «Черга X» – вхід об'єкта, «Обслужені X» – вихід.
 */
public final class Smo extends PetriObject {

    public Smo(String label, int channels, double mean) {
        super("СМО " + label);
        PetriP queue = net.place("Черга " + label, 0, 0, 0);
        PetriP free = net.place("Вільні канали " + label, channels, 340, 95);
        PetriP done = net.place("Обслужені " + label, 0, 300, 0);
        PetriT service = net.transition("Обслуговування " + label, Delay.exp(mean), 150, 0);
        net.arc(queue, service);
        net.arc(free, service);
        net.arc(service, free);
        net.carry(service, done, queue);
    }

    @Override
    public String kind() {
        return "СМО";
    }
}
