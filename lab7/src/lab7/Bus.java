package lab7;

import ua.stetsenkoinna.petriobj.PetriP;
import ua.stetsenkoinna.petriobj.PetriT;

/**
 * Петрі-об'єкт «Мікроавтобус» з n місцями, що курсує між зупинками З1 і З2. На зупинці автобус приймає
 * пасажирів – спершу з черги (пріоритет queuePriority), потім тих, хто щойно підійшов (directPriority);
 * заповнений автобус вирушає в рейс (trip), на кінцевій зупинці пасажири виходять (unload), і автобус
 * знову стає на посадку. Позиції пасажирів і черг належать об'єктам «Зупинка».
 */
public final class Bus extends PetriObject {

    private static final double WIDTH = 1240;

    public Bus(String b, int n, int start, Delay trip, Delay unload, int queuePriority, int directPriority) {
        super("Мікроавтобус " + b);
        PetriP[] at = new PetriP[3];
        PetriP[] seats = new PetriP[3];
        PetriP[] inside = new PetriP[3];
        PetriP[] arrived = new PetriP[3];
        for (int s = 1; s <= 2; s++) {
            at[s] = net.place(b + " на З" + s, s == start ? 1 : 0, x(s, 290), 200);
            seats[s] = net.place("Вільні місця " + b + " на З" + s, s == start ? n : 0, x(s, 290), -200);
            inside[s] = net.place("Пасажири в " + b + " на З" + s, 0, x(s, 290), 0);
            arrived[s] = net.place(b + " прибув на З" + s, 0, x(s, 560), -100);
            PetriP passenger = net.place("Пасажир на З" + s, 0, x(s, 0), -80);
            PetriP queue = net.place("Черга на З" + s, 0, x(s, 0), 80);
            PetriP room = net.place("Місця в черзі З" + s, 0, x(s, 0), 200);
            String suffix = " в " + b + " на З" + s;
            PetriT fromQueue = net.transition("Посадка з черги" + suffix, 0, queuePriority, x(s, 130), 80);
            PetriT direct = net.transition("Посадка" + suffix, 0, directPriority, x(s, 130), -80);
            net.arc(queue, fromQueue);
            net.arc(seats[s], fromQueue);
            net.carry(fromQueue, inside[s], queue);
            net.arc(fromQueue, room);
            net.arc(passenger, direct);
            net.arc(seats[s], direct);
            net.carry(direct, inside[s], passenger);
        }
        PetriP carried = net.place("Перевезені пасажири", 0, WIDTH / 2, 330);
        for (int s = 1; s <= 2; s++) {
            int t = 3 - s;
            PetriT ride = net.transition("Рейс " + b + " З" + s + "→З" + t, trip, x(s, 450), 100);
            PetriT off = net.transition("Висадка " + b + " на З" + t, unload, x(t, 450), -100);
            net.arc(at[s], ride);
            net.arc(inside[s], ride, n);
            net.arc(ride, arrived[t]);
            net.carry(ride, carried, n, inside[s]);
            net.arc(arrived[t], off);
            net.arc(off, at[t]);
            net.arc(off, seats[t], n);
        }
    }

    /** Координата елемента: частина мережі для зупинки З2 – дзеркальне відображення частини для З1. */
    private static double x(int stop, double x) {
        return stop == 1 ? x : WIDTH - x;
    }

    @Override
    public String kind() {
        return "Мікроавтобус";
    }
}
