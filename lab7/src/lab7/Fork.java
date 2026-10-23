package lab7;

import ua.stetsenkoinna.petriobj.PetriP;
import ua.stetsenkoinna.petriobj.PetriT;

/**
 * Петрі-об'єкт «Розгалуження»: вимога з позиції входу миттєво направляється в одну з вихідних
 * позицій outputs[i] з ймовірністю probabilities[i] (конфлікт переходів з однаковим пріоритетом).
 */
public final class Fork extends PetriObject {

    public Fork(String label, String input, String[] outputs, String[] routes, double[] probabilities) {
        super("Розгалуження " + label);
        PetriP in = net.place(input, 0, 0, 0);
        double top = -150.0 * (outputs.length - 1) / 2;
        for (int i = 0; i < outputs.length; i++) {
            PetriP out = net.place(outputs[i], 0, 260, top + 150 * i);
            PetriT t = net.transition(routes[i], 0, 130, top + 150 * i);
            t.setProbability(Math.round(probabilities[i] * 1e9) / 1e9);
            net.arc(in, t);
            net.carry(t, out, in);
        }
    }

    @Override
    public String kind() {
        return "Розгалуження";
    }
}
