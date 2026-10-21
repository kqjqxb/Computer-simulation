package lab6;

import ua.stetsenkoinna.petriobj.PetriP;
import ua.stetsenkoinna.petriobj.PetriT;

/** Допоміжні перевірки моделей. */
final class Checks {

    private Checks() {
    }

    static void check(boolean ok, String what) {
        if (!ok) {
            throw new IllegalStateException("порушено інваріант: " + what);
        }
    }

    /**
     * Найбільша розбіжність між середніми, які обчислює бібліотека (PetriP.getMean, PetriT.getMean),
     * і власними статистиками Simulation; має сенс для прогону без розгону.
     */
    static double libraryDifference(Simulation s) {
        double max = 0;
        for (PetriP p : s.places()) {
            max = Math.max(max, Math.abs(s.meanMark(p.getName()) - s.libraryMeanMark(p.getName())));
        }
        for (PetriT t : s.transitions()) {
            max = Math.max(max, Math.abs(s.meanBuffer(t.getName()) - s.libraryMeanBuffer(t.getName())));
        }
        return max;
    }
}
