"""Завдання 3: два мікроавтобуси на маршруті приміського сполучення."""
import json
import statistics
from pathlib import Path

import fun_rand
from petri import PetriNet, unif
from runner import fmt, replicate, summary

RESULTS = Path(__file__).resolve().parent / "results"
STOPS = (1, 2)
BUSES = {"A": unif(20, 5), "B": unif(30, 5)}  # час проходження маршруту, хв
ARRIVAL, UNLOAD = unif(0.5, 0.2), unif(5, 1)
QUEUE_LIMIT, FARE = 30, 20
DAY, SEEDS = 600.0, range(1, 51)  # 10 годин роботи
START = {"A": 1, "B": 2}  # на початку дня A стоїть на зупинці 1, B – на зупинці 2
SEATS = range(1, 26)
SEATS_NOTE = ("опис наведено для n = 1; n – кількість місць: кратність дуг «Пасажири в … → Рейс …», "
              "«Рейс … → Перевезені пасажири», «Висадка … → Вільні місця …» і початкове маркування "
              "«Вільні місця A на З1», «Вільні місця B на З2»")


def other(s):
    return 3 - s


def build(n):
    net = PetriNet(f"Маршрут мікроавтобусів, n = {n}")
    for s in STOPS:
        net.place(f"Генератор З{s}", 1)
        net.place(f"Пасажир на З{s}")
        net.place(f"Черга на З{s}")
        net.place(f"Місця в черзі З{s}", QUEUE_LIMIT)
        net.place(f"Пішли з З{s}")
    for b in BUSES:
        for s in STOPS:
            here = START[b] == s
            net.place(f"{b} на З{s}", int(here))
            net.place(f"Вільні місця {b} на З{s}", n if here else 0)
            net.place(f"Пасажири в {b} на З{s}")
            net.place(f"{b} прибув на З{s}")
    net.place("Перевезені пасажири")

    for s in STOPS:
        passenger, queue, room = f"Пасажир на З{s}", f"Черга на З{s}", f"Місця в черзі З{s}"
        net.transition(f"Прихід на З{s}", ARRIVAL, inputs={f"Генератор З{s}": 1},
                       outputs={f"Генератор З{s}": 1, passenger: 1})
        # пасажир сідає в B, лише коли на зупинці немає A: пріоритет посадки в A вищий
        for b, bonus in (("A", 1), ("B", 0)):
            seats, inside = f"Вільні місця {b} на З{s}", f"Пасажири в {b} на З{s}"
            net.transition(f"Посадка з черги в {b} на З{s}", inputs={queue: 1, seats: 1},
                           outputs={inside: 1, room: 1}, carry={inside: queue}, priority=5 + bonus)
            net.transition(f"Посадка в {b} на З{s}", inputs={passenger: 1, seats: 1}, outputs={inside: 1},
                           carry={inside: passenger}, priority=3 + bonus)
        net.transition(f"Стати в чергу на З{s}", inputs={passenger: 1, room: 1}, outputs={queue: 1},
                       carry={queue: passenger}, priority=2)
        net.transition(f"Відмова на З{s}", inputs={passenger: 1}, outputs={f"Пішли з З{s}": 1}, priority=1)
    for b, trip in BUSES.items():
        for s in STOPS:
            t = other(s)
            net.transition(f"Рейс {b} З{s}→З{t}", trip,
                           inputs={f"{b} на З{s}": 1, f"Пасажири в {b} на З{s}": n},
                           outputs={f"{b} прибув на З{t}": 1, "Перевезені пасажири": n},
                           carry={"Перевезені пасажири": f"Пасажири в {b} на З{s}"})
            net.transition(f"Висадка {b} на З{t}", UNLOAD, inputs={f"{b} прибув на З{t}": 1},
                           outputs={f"{b} на З{t}": 1, f"Вільні місця {b} на З{t}": n})
    return net


def stable(net):
    """У переходах немає маркерів, що виходять у поточний момент (миттєві переходи завершені)."""
    return all(tr.next_time > net.time for tr in net.transitions.values())


class Invariants:
    def __init__(self, n):
        self.n, self.checked = n, 0

    def __call__(self, net):
        if not stable(net):
            return
        tr = net.transitions
        for b in BUSES:
            where = [net.mark(f"{b} на З{s}") + net.mark(f"{b} прибув на З{s}")
                     + len(tr[f"Рейс {b} З{s}→З{other(s)}"].buffer) + len(tr[f"Висадка {b} на З{s}"].buffer)
                     for s in STOPS]
            assert sum(where) == 1
            for s in STOPS:
                seats = net.mark(f"Вільні місця {b} на З{s}") + net.mark(f"Пасажири в {b} на З{s}")
                assert seats == self.n * net.mark(f"{b} на З{s}")
        for s in STOPS:
            assert net.mark(f"Черга на З{s}") + net.mark(f"Місця в черзі З{s}") == QUEUE_LIMIT
            if net.mark(f"Черга на З{s}"):
                assert not any(net.mark(f"{b} на З{s}") for b in BUSES)
            arrived = tr[f"Прихід на З{s}"].fired
            boarded = sum(tr[f"{k} {b} на З{s}"].fired for b in BUSES
                          for k in ("Посадка з черги в", "Посадка в"))
            assert arrived == boarded + tr[f"Відмова на З{s}"].fired + net.mark(f"Черга на З{s}")
        self.checked += 1


def run(n, time=DAY, hooks=(), trace=0, out=print):
    net = build(n)
    waits, queued = [], []

    def from_queue(t, consumed):
        w = t - next(iter(consumed.values()))[0].born
        waits.append(w)
        queued.append(w)

    for s in STOPS:
        for b in BUSES:
            net.transitions[f"Посадка з черги в {b} на З{s}"].on_out = from_queue
            net.transitions[f"Посадка в {b} на З{s}"].on_out = lambda t, consumed: waits.append(0.0)
    r = net.simulate(time, hooks=hooks, trace=trace, out=out)
    tr = r["transitions"]
    boarded = len(waits)
    lost = sum(tr[f"Відмова на З{s}"]["fired"] for s in STOPS)
    arrived = sum(tr[f"Прихід на З{s}"]["fired"] for s in STOPS)
    joined = sum(tr[f"Стати в чергу на З{s}"]["fired"] for s in STOPS)
    return {
        "wait": statistics.fmean(waits),
        "wait_queued": statistics.fmean(queued) if queued else 0.0,
        "queue": statistics.fmean(r["places"][f"Черга на З{s}"]["mean"] for s in STOPS),
        "boarded": boarded, "lost": lost, "arrived": arrived, "joined": joined,
        "revenue": FARE * (boarded - lost),
        "income": FARE * boarded, "loss": FARE * lost,
        "trips": {b: sum(tr[f"Рейс {b} З{s}→З{other(s)}"]["entered"] for s in STOPS) for b in BUSES},
        "time": r["time"],
    }


def main():
    with open(RESULTS / "net3_minibus.json", "w", encoding="utf-8") as f:
        json.dump(build(1).describe() | {"parameter": SEATS_NOTE}, f, ensure_ascii=False, indent=1)
    print(f"Мікроавтобуси: пасажири кожні {ARRIVAL} хв на кожну з двох кінцевих зупинок, "
          f"черга до {QUEUE_LIMIT}, "
          f"рейс A {BUSES['A']} хв, B {BUSES['B']} хв, висадка {UNLOAD} хв, день {DAY:.0f} хв\n")
    print("Трасування перших подій (n = 3):\n")
    fun_rand.seed(1)
    run(3, trace=14, out=print)

    fun_rand.seed(100)
    check = Invariants(10)
    run(10, hooks=[check])
    print(f"Перевірка інваріантів (n = 10): {check.checked} стійких станів, порушень немає")

    # довгий прогін для перевірки формули Літтла та пропускної здатності
    fun_rand.seed(200)
    long_time = 100 * DAY
    lr = run(25, time=long_time)
    lam = lr["joined"] / long_time / len(STOPS)
    print(f"Довгий прогін {long_time:.0f} хв, n = 25: середня черга на зупинці {lr['queue']:.4f}, "
          f"λ_черги · W_черги = {lam:.4f} · {lr['wait_queued']:.4f} = {lam * lr['wait_queued']:.4f}")
    cap = 25 * (1 / (2 * 20 + 2 * 5) + 1 / (2 * 30 + 2 * 5))
    print(f"Перевезено за хв в одному напрямку {lr['boarded'] / long_time / 2:.4f}; "
          f"оцінка при постійній черзі "
          f"n·(1/50 + 1/70) = {cap:.4f}; рейсів A {lr['trips']['A']}, B {lr['trips']['B']} "
          f"(оцінка {long_time / 25:.0f} і {long_time / 35:.0f})")

    sweep = []
    print(f"\nРЕЗУЛЬТАТИ ({len(SEEDS)} днів для кожного n, 95 % довірчий інтервал):")
    print(f"  {'n':>2} {'очікування, хв':>18} {'серед тих, хто чекав':>22} {'перевезено':>10} "
          f"{'пішли':>8} {'виручка, грн':>20}")
    for n in SEATS:
        runs = replicate(lambda: run(n), SEEDS)
        row = {"n": n, **{k: summary(r[k] for r in runs) for k in
                          ("wait", "wait_queued", "queue", "boarded", "lost", "revenue", "income", "loss")},
               "trips_A": statistics.fmean(r["trips"]["A"] for r in runs),
               "trips_B": statistics.fmean(r["trips"]["B"] for r in runs)}
        sweep.append(row)
        print(f"  {n:2d} {fmt(row['wait'], 2):>18} {fmt(row['wait_queued'], 2):>22} "
              f"{row['boarded'][0]:10.1f} {row['lost'][0]:8.1f} {fmt(row['revenue'], 0):>20}")
    best = min(sweep, key=lambda r: r["wait"][0])
    print(f"\nНайменший середній час очікування при n = {best['n']}: {fmt(best['wait'], 3)} хв")
    print(f"Виручка за день при n = {best['n']}: дохід {best['income'][0]:.0f}, "
          f"втрати {best['loss'][0]:.0f}, "
          f"виручка {fmt(best['revenue'], 0)} грн")

    with open(RESULTS / "task3.json", "w", encoding="utf-8") as f:
        json.dump({"sweep": sweep, "best": best["n"], "check_states": check.checked,
                   "little": {"queue": lr["queue"], "rate": lam, "wait": lr["wait_queued"]},
                   "capacity": {"sim": lr["boarded"] / long_time / 2, "estimate": cap, "trips": lr["trips"],
                                "time": long_time}}, f, ensure_ascii=False, indent=1)


if __name__ == "__main__":
    main()
