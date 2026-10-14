"""Завдання 3: модель CREATE -> PROCESS 1 -> PROCESS 2 -> PROCESS 3 -> DISPOSE (рисунок 2.1)."""
import json
import math
from pathlib import Path

import fun_rand
from elements import Create, Dispose, Process
from model import Model

RESULTS = Path(__file__).resolve().parent / "results"

BASE = {
    "create_mean": 2.0, "create_dist": "exp",
    "means": (1.0, 1.5, 1.2), "dists": ("exp", "exp", "exp"), "devs": (0.0, 0.0, 0.0),
    "maxqueues": (5, 5, 5), "channels": (1, 1, 1),
}


def build(params=None):
    p = {**BASE, **(params or {})}
    create = Create(p["create_mean"], distribution=p["create_dist"], name="CREATE")
    settings = zip(p["means"], p["dists"], p["devs"], p["maxqueues"], p["channels"])
    processes = [Process(mean, channels=ch, maxqueue=q, distribution=dist, delay_dev=dev,
                         name=f"PROCESS {i + 1}")
                 for i, (mean, dist, dev, q, ch) in enumerate(settings)]
    dispose = Dispose()
    create.set_next_element(processes[0])
    for current, following in zip(processes, processes[1:] + [dispose]):
        current.set_next_element(following)
    return Model([create, *processes, dispose])


def main():
    fun_rand.seed(3)
    model = build()
    print("Create: exp(2.0); PROCESS 1-3: exp(1.0), exp(1.5), exp(1.2); maxqueue = 5; час моделювання 1000")
    model.simulate(1000.0, trace_events=4)
    result = model.print_result()
    balance = result["created"] - result["disposed"] - result["failures"] - result["in_system_now"]
    print(f"Баланс вимог: створено - вийшло - відмови - у системі = {balance}")
    with open(RESULTS / "task3.json", "w", encoding="utf-8") as f:
        json.dump(result, f, ensure_ascii=False, indent=1, default=lambda v: None if math.isinf(v) else v)


if __name__ == "__main__":
    main()
