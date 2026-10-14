"""Завдання 2: банк для автомобілістів з двома віконцями та під'їзними смугами."""
import json
import statistics
from pathlib import Path

import fun_rand
from elements import Create, Dispose, Process
from item import Item
from model import Model
from queueing_theory import mmck
from runner import replicate, summary

RESULTS = Path(__file__).resolve().parent / "results"
ARRIVAL_MEAN, SERVICE_MEAN = 0.5, 0.3
LANE_QUEUE = 3  # місць очікування на смузі, крім автомобіля біля вікна
TIME, SEEDS = 1000.0, range(1, 21)


def cars(lane):
    return len(lane.queue) + lane.busy


def choose_lane(item, routes):
    """Коротша смуга; за рівної кількості автомобілів – перша."""
    first, second = routes
    return second if cars(second.target) < cars(first.target) else first


class LaneSwitch:
    """Останній автомобіль переїжджає на іншу смугу, якщо на його смузі щонайменше
    на два автомобілі більше."""

    def __init__(self, lanes):
        self.lanes = lanes
        self.count = 0

    def __call__(self, model):
        a, b = self.lanes
        for source, target in ((a, b), (b, a)):
            while source.queue and cars(source) - cars(target) >= 2:
                target.accept(source.queue.pop())
                self.count += 1


def build():
    cashiers = [Process(SERVICE_MEAN, maxqueue=LANE_QUEUE, name=f"Касир {i}") for i in (1, 2)]
    create = Create(ARRIVAL_MEAN, first_time=0.1, name="Прибуття", routing=choose_lane)
    bank_exit = Dispose("Від'їзд", group=lambda item: "клієнти")
    for cashier in cashiers:
        create.add_route(cashier)
        cashier.add_route(bank_exit)
        # початкові умови: касир зайнятий, обслуговування ~ N(1; 0.3), у черзі два автомобілі
        cashier.in_service.append((max(0.0, fun_rand.norm(1.0, 0.3)), Item(0.0)))
        cashier.queue.extend(Item(0.0) for _ in range(2))
        cashier.tnext = cashier.in_service[0][0]
        cashier.state = 1
    switch = LaneSwitch(cashiers)
    model = Model([create, *cashiers, bank_exit], hooks=[switch])
    model.lane_switch = switch
    return model


def collect(model):
    r = model.results()
    cashiers = model.elements[1:3]
    arrivals = r["created"]
    return {
        "load": [r["processes"][c.name]["mean_load"] for c in cashiers],
        "mean_in_bank": r["mean_in_system"],
        "exit_interval": r["mean_exit_interval"],
        "time_in_bank": r["mean_time_in_system"],
        "queues": [r["processes"][c.name]["mean_queue"] for c in cashiers],
        "refused_percent": 100 * r["failures"] / arrivals,
        "lane_switches": model.lane_switch.count,
        "created": arrivals, "served": r["disposed"],
    }


def main():
    fun_rand.seed(2)
    model = build()
    print(f"Банк: прибуття exp({ARRIVAL_MEAN}), обслуговування exp({SERVICE_MEAN}), на смузі до {LANE_QUEUE} "
          f"автомобілів у черзі, час моделювання {TIME:.0f}")
    model.simulate(TIME, trace_events=4)
    one = collect(model)

    runs = replicate(build, TIME, SEEDS, collect=collect)

    def stat(key, index=None):
        return summary([r[key] if index is None else r[key][index] for r in runs])

    table = [
        ("1) завантаження касира 1", stat("load", 0)),
        ("1) завантаження касира 2", stat("load", 1)),
        ("2) середнє число клієнтів у банку", stat("mean_in_bank")),
        ("3) середній інтервал між від'їздами", stat("exit_interval")),
        ("4) середній час перебування в банку", stat("time_in_bank")),
        ("5) середня черга на смузі 1", stat("queues", 0)),
        ("5) середня черга на смузі 2", stat("queues", 1)),
        ("6) відсоток клієнтів, яким відмовлено", stat("refused_percent")),
        ("7) число змін під'їзних смуг", stat("lane_switches")),
    ]
    print(f"\nОдин прогін: {json.dumps(one, ensure_ascii=False)}")
    print(f"\nРЕЗУЛЬТАТИ ({len(SEEDS)} прогонів по {TIME:.0f} од. часу, 95 % довірчий інтервал):")
    for label, (mean, ci) in table:
        print(f"  {label:40} {mean:9.4f} ± {ci:.4f}")

    rate = statistics.mean(r["created"] / TIME for r in runs)
    served_rate = statistics.mean(r["served"] / TIME for r in runs)
    little = served_rate * table[4][1][0]
    print(f"\nПеревірка за формулою Літтла: λ_обсл · W = {served_rate:.4f} · {table[4][1][0]:.4f} = "
          f"{little:.4f}, "
          f"середнє число в банку = {table[2][1][0]:.4f}")
    print(f"Інтенсивність прибуття {rate:.4f} (задано {1 / ARRIVAL_MEAN}); 1 / інтервал між від'їздами = "
          f"{1 / table[3][1][0]:.4f}, інтенсивність обслужених = {served_rate:.4f}")
    approx = mmck(1 / ARRIVAL_MEAN, SERVICE_MEAN, 2, 2 * LANE_QUEUE)
    print(f"Наближення M/M/2/8 зі спільною чергою: завантаження {approx['mean_load']:.4f}, "
          f"відмови {100 * approx['failure_probability']:.4f} %, середнє число в системі "
          f"{approx['mean_in_system']:.4f}")

    with open(RESULTS / "task2.json", "w", encoding="utf-8") as f:
        json.dump({"single": one, "table": [{"metric": m, "mean": v[0], "ci": v[1]} for m, v in table],
                   "little": {"served_rate": served_rate, "time": table[4][1][0], "product": little,
                              "mean_in_bank": table[2][1][0]},
                   "arrival_rate": rate, "approx": approx}, f, ensure_ascii=False, indent=1)


if __name__ == "__main__":
    main()
