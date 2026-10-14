"""Завдання 2, крок 1: вимірювання часу сортування колекції з 1 000 000 об'єктів."""
import csv
import gc
import platform
import random
import sys
import time
from operator import attrgetter
from pathlib import Path

SIZE = 1_000_000
RUNS = 500
SEED = 2026
OUT = Path(__file__).resolve().parent / "data" / "sort_times.csv"


class Order:
    __slots__ = ("order_id", "amount")

    def __init__(self, order_id, amount):
        self.order_id = order_id
        self.amount = amount


def main():
    rng = random.Random(SEED)
    orders = [Order(i, rng.randint(0, 10 ** 9)) for i in range(SIZE)]
    key = attrgetter("amount")

    warmup = orders[:]
    warmup.sort(key=key)
    del warmup

    OUT.parent.mkdir(exist_ok=True)
    with open(OUT, "w", newline="") as f:
        writer = csv.writer(f)
        writer.writerow(["run", "seconds"])
        for run in range(1, RUNS + 1):
            collection = orders[:]
            rng.shuffle(collection)
            gc.collect()

            start = time.perf_counter()
            collection.sort(key=key)
            elapsed = time.perf_counter() - start

            writer.writerow([run, f"{elapsed:.7f}"])
            del collection
            if run % 50 == 0:
                f.flush()
                print(f"{run}/{RUNS}: {elapsed:.4f} с", flush=True)

    print(f"Python {sys.version.split()[0]}, {platform.platform()}, {platform.processor() or platform.machine()}")
    print(f"Збережено: {OUT}")


if __name__ == "__main__":
    main()
