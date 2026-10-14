"""Завдання 1-2: модель обслуговування одним пристроєм та середнє завантаження пристрою."""
import json
from pathlib import Path

import fun_rand
from elements import Create, Dispose, Process
from model import Model
from queueing_theory import mmck
from runner import metric, replicate

RESULTS = Path(__file__).resolve().parent / "results"
CREATE_MEAN, PROCESS_MEAN, MAXQUEUE = 2.0, 1.0, 5


def build():
    create = Create(CREATE_MEAN, name="CREATOR")
    process = Process(PROCESS_MEAN, maxqueue=MAXQUEUE, name="PROCESSOR")
    dispose = Dispose()
    create.set_next_element(process)
    process.set_next_element(dispose)
    return Model([create, process, dispose])


def main():
    fun_rand.seed(1)
    model = build()
    print(f"Create: exp({CREATE_MEAN}), Process: exp({PROCESS_MEAN}), maxqueue = {MAXQUEUE}, "
          "час моделювання 1000")
    model.simulate(1000.0, trace_events=6)
    single = model.print_result()

    theory = mmck(1 / CREATE_MEAN, PROCESS_MEAN, 1, MAXQUEUE)
    runs = replicate(build, 100_000.0, range(1, 11))
    print("\nПорівняння з теорією M/M/1/5 (10 прогонів по 100000 одиниць часу, 95 % довірчий інтервал):")
    rows = []
    for label, key in [("ймовірність відмови", "failure_probability"),
                       ("середня довжина черги", "mean_queue"),
                       ("середнє завантаження", "mean_load")]:
        mean, half = metric(runs, ["processes", "PROCESSOR", key])
        rows.append({"metric": label, "sim": mean, "ci": half, "theory": theory[key]})
        print(f"{label:24} імітація = {mean:.4f} ± {half:.4f}, теорія = {theory[key]:.4f}")

    with open(RESULTS / "task1.json", "w", encoding="utf-8") as f:
        json.dump({"single": single, "comparison": rows}, f, ensure_ascii=False, indent=1)


if __name__ == "__main__":
    main()
