"""Завдання 5: PROCESS з кількома ідентичними каналами обслуговування."""
import json
import math
from pathlib import Path

import fun_rand
from elements import Create, Dispose, Process
from model import Model
from queueing_theory import mmck
from runner import metric, replicate

RESULTS = Path(__file__).resolve().parent / "results"
CREATE_MEAN, PROCESS_MEAN = 1.0, 2.5


def build(channels, maxqueue):
    create = Create(CREATE_MEAN, name="CREATE")
    process = Process(PROCESS_MEAN, channels=channels, maxqueue=maxqueue, name="PROCESS")
    dispose = Dispose()
    create.set_next_element(process)
    process.set_next_element(dispose)
    return Model([create, process, dispose])


def main():
    fun_rand.seed(5)
    model = build(3, 3)
    print(f"Create: exp({CREATE_MEAN}), PROCESS: exp({PROCESS_MEAN}), 3 канали, maxqueue = 3")
    model.simulate(1000.0, trace_events=6)
    model.print_result()

    rows = []
    print(f"\nПОРІВНЯННЯ З ТЕОРІЄЮ M/M/c/K (λ = {1 / CREATE_MEAN}, "
          f"середній час обслуговування {PROCESS_MEAN}; 20 прогонів по 50000 одиниць часу)")
    for channels, maxqueue in [(1, 3), (2, 3), (3, 3), (4, 3), (5, 3), (3, math.inf), (4, math.inf)]:
        runs = replicate(lambda: build(channels, maxqueue), 50_000.0, range(1, 21))
        th = mmck(1 / CREATE_MEAN, PROCESS_MEAN, channels, maxqueue)
        row = {"channels": channels, "maxqueue": None if maxqueue == math.inf else maxqueue}
        line = [f"c = {channels}, maxqueue = {maxqueue}:"]
        for label, key in [("Pвідм", "failure_probability"), ("завант.", "mean_load"),
                           ("черга", "mean_queue")]:
            sim, ci = metric(runs, ["processes", "PROCESS", key])
            row[key] = {"sim": sim, "ci": ci, "theory": th[key]}
            line.append(f"{label} {sim:.4f} ± {ci:.4f} (теорія {th[key]:.4f})")
        rows.append(row)
        print("  ".join(line))

    with open(RESULTS / "task5.json", "w", encoding="utf-8") as f:
        json.dump(rows, f, ensure_ascii=False, indent=1)


if __name__ == "__main__":
    main()
