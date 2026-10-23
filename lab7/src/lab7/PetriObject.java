package lab7;

import ua.stetsenkoinna.petriobj.PetriP;
import ua.stetsenkoinna.petriobj.PetriSim;
import ua.stetsenkoinna.petriobj.PetriT;

/**
 * Петрі-об'єкт: об'єкт класу, полем якого є мережа Петрі, що описує його динаміку. Підклас будує мережу
 * в конструкторі через поле net; позиції, які об'єкт ділить з іншими об'єктами моделі, адресуються за
 * іменем. Імітатор PetriSim створюється один раз – під час збирання моделі.
 */
public abstract class PetriObject {

    private final String name;
    protected final NetBuilder net;
    private int priority;
    private PetriSim sim;

    protected PetriObject(String name) {
        this.name = name;
        this.net = new NetBuilder(name);
    }

    public String name() {
        return name;
    }

    /** Назва класу Петрі-об'єкта для опису моделі. */
    public abstract String kind();

    public int priority() {
        return priority;
    }

    /** Пріоритет об'єкта: об'єкти з вищим пріоритетом першими виконують вхід маркерів у переходи. */
    public PetriObject priority(int value) {
        if (sim != null) {
            throw new IllegalStateException("пріоритет задається до збирання моделі");
        }
        priority = value;
        return this;
    }

    public NetBuilder builder() {
        return net;
    }

    public PetriP place(String placeName) {
        return net.places().get(placeIndex(placeName));
    }

    public boolean hasPlace(String placeName) {
        return net.places().stream().anyMatch(p -> p.getName().equals(placeName));
    }

    public int placeIndex(String placeName) {
        for (int i = 0; i < net.places().size(); i++) {
            if (net.places().get(i).getName().equals(placeName)) {
                return i;
            }
        }
        throw new IllegalArgumentException(name + ": немає позиції «" + placeName + "»");
    }

    public PetriT transition(String transitionName) {
        return net.transitions().stream().filter(t -> t.getName().equals(transitionName)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException(name + ": немає переходу «"
                        + transitionName + "»"));
    }

    PetriSim sim() {
        if (sim == null) {
            sim = new PetriSim(net.build());
            sim.setName(name);
            sim.setPriority(priority);
        }
        return sim;
    }
}
