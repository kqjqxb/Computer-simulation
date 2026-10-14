"""Завдання 1: перевірка універсального алгоритму імітації на моделях з відомим аналітичним розв'язком."""
import json
from pathlib import Path

import fun_rand
from elements import Create, Dispose, Process
from item import Item
from model import Model
from queueing_theory import erlang_b, mmck, priority_waits
from runner import metric, replicate

RESULTS = Path(__file__).resolve().parent / "results"
TIME, WARMUP, SEEDS = 50_000.0, 1_000.0, range(1, 21)


def run(build):
    return replicate(build, TIME, SEEDS, warmup=WARMUP)


def multichannel():
    """Багатоканальний пристрій з обмеженою чергою: M/M/3/3."""
    def build():
        create = Create(1.0, name="CREATE")
        process = Process(2.5, channels=3, maxqueue=3, name="P")
        dispose = Dispose()
        create.add_route(process)
        process.add_route(dispose)
        return Model([create, process, dispose])

    runs = run(build)
    th = mmck(1.0, 2.5, 3, 3)
    failure = [r["processes"]["P"]["failure"] / r["processes"]["P"]["arrivals"] for r in runs]
    rows = [("ймовірність відмови", sum(failure) / len(failure), None, th["failure_probability"])]
    for label, key in [("завантаження каналу", "mean_load"), ("середня черга", "mean_queue")]:
        rows.append((label, *metric(runs, ["processes", "P", key]), th[key]))
    return "Багатоканальний пристрій: M/M/3/3, λ = 1, τ = 2,5", rows


def priority_routing():
    """Вибір маршруту за пріоритетом: основний пристрій, а якщо він зайнятий – резервний
    (система з відмовами)."""
    def build():
        create = Create(1.0, name="CREATE", routing="priority")
        main = Process(1.0, maxqueue=0, name="MAIN")
        reserve = Process(1.0, maxqueue=0, name="RESERVE")
        dispose = Dispose()
        create.add_route(main, priority=2).add_route(reserve, priority=1)
        main.add_route(dispose)
        reserve.add_route(dispose)
        return Model([create, main, reserve, dispose])

    runs = run(build)
    b1, b2 = erlang_b(1, 1.0), erlang_b(2, 1.0)
    lost = [r["failures"] / r["created"] for r in runs]
    rows = [("завантаження основного", *metric(runs, ["processes", "MAIN", "mean_load"]), 1.0 * (1 - b1)),
            ("завантаження резервного", *metric(runs, ["processes", "RESERVE", "mean_load"]),
             1.0 * (b1 - b2)),
            ("ймовірність втрати", sum(lost) / len(lost), None, b2)]
    return "Маршрут за пріоритетом: два пристрої без черги, λ = 1, τ = 1 (формула Ерланга B)", rows


def probability_routing():
    """Вибір маршруту за ймовірністю: розщеплення пуассонівського потоку на три системи M/M/1."""
    shares = (0.2, 0.3, 0.5)

    def build():
        create = Create(1.0, name="CREATE")
        dispose = Dispose()
        nodes = [Process(1.0, name=f"P{i + 1}") for i in range(3)]
        for node, share in zip(nodes, shares):
            create.add_route(node, probability=share)
            node.add_route(dispose)
        return Model([create, *nodes, dispose])

    runs = run(build)
    rows = []
    for i, share in enumerate(shares):
        th = mmck(share, 1.0)
        arrivals = [r["processes"][f"P{i + 1}"]["arrivals"] / r["created"] for r in runs]
        rows.append((f"P{i + 1}: частка вимог", sum(arrivals) / len(arrivals), None, share))
        rows.append((f"P{i + 1}: середня черга", *metric(runs, ["processes", f"P{i + 1}", "mean_queue"]),
                     th["mean_queue"]))
    return "Маршрут за ймовірністю 0,2 / 0,3 / 0,5, λ = 1, τ = 1", rows


def priority_queue():
    """Пріоритетний вибір з черги: M/M/1 з двома класами вимог і відносним пріоритетом класу 1."""
    rate, share1 = 0.8, 0.3

    def build():
        create = Create(1 / rate, name="CREATE",
                        item_factory=lambda t: Item(t, 1 if fun_rand.uniform01() < share1 else 2))
        process = Process(1.0, queue_key=lambda item: item.kind, name="P")
        dispose = Dispose()
        create.add_route(process)
        process.add_route(dispose)
        return Model([create, process, dispose])

    runs = run(build)
    th = priority_waits([rate * share1, rate * (1 - share1)], [1.0, 1.0], [2.0, 2.0])
    rows = [(f"очікування класу {k}", *metric(runs, ["processes", "P", "wait_by_kind", k]), th[k - 1])
            for k in (1, 2)]
    return "Пріоритетна черга: два класи, λ = 0,8, τ = 1 (формула Кобхема)", rows


def main():
    fun_rand.seed(1)
    out = []
    print(f"ПЕРЕВІРКА УНІВЕРСАЛЬНОГО АЛГОРИТМУ: {len(SEEDS)} прогонів по {TIME:.0f} одиниць часу, "
          f"розгін {WARMUP:.0f}")
    for check in (multichannel, priority_routing, probability_routing, priority_queue):
        title, rows = check()
        print(f"\n{title}")
        for label, sim, ci, theory in rows:
            interval = f" ± {ci:.4f}" if ci is not None else ""
            print(f"  {label:26} імітація = {sim:.4f}{interval}, теорія = {theory:.4f}")
        out.append({"title": title,
                    "rows": [{"metric": m, "sim": s, "ci": c, "theory": t} for m, s, c, t in rows]})
    with open(RESULTS / "task1.json", "w", encoding="utf-8") as f:
        json.dump(out, f, ensure_ascii=False, indent=1)


if __name__ == "__main__":
    main()
