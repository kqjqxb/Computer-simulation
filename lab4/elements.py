import math
from collections import defaultdict

from element import Element
from item import Item


class Create(Element):
    def __init__(self, delay, first_time=0.0, item_factory=None, **kwargs):
        super().__init__(delay=delay, **kwargs)
        self.tnext = first_time
        self.item_factory = item_factory or (lambda t: Item(t))

    def out_act(self):
        super().out_act()
        item = self.item_factory(self.tcurr)
        self.tnext = self.tcurr + self.get_delay(item)
        self._after_change()
        self.send(item)


class Process(Element):
    """Багатоканальний пристрій з чергою обмеженої довжини.

    channels може бути math.inf – тоді елемент моделює затримку без обмеження кількості вимог
    (наприклад, переміщення). queue_key(item) задає пріоритет вибору з черги: першою обслуговується
    вимога з найменшим ключем, за рівних ключів – та, що прийшла раніше.
    """

    def __init__(self, delay, channels=1, maxqueue=math.inf, queue_key=None, record_arrivals=False, **kwargs):
        super().__init__(delay=delay, **kwargs)
        self.channels = channels
        self.maxqueue = maxqueue
        self.queue_key = queue_key
        self.record_arrivals = record_arrivals
        self.in_service = []
        self.queue = []
        self.reset_statistics()

    def reset_statistics(self):
        super().reset_statistics()
        self.failure = 0
        self.arrivals = 0
        self.queue_area = 0.0
        self.busy_area = 0.0
        self.wait = defaultdict(lambda: [0.0, 0])
        self.arrival_times = []

    @property
    def busy(self):
        return len(self.in_service)

    def can_accept(self):
        return self.busy < self.channels or len(self.queue) < self.maxqueue

    def _refresh(self):
        self.tnext = min((t for t, _ in self.in_service), default=math.inf)
        self.state = self.busy
        self._after_change()

    def _start(self, item):
        record = self.wait[item.kind]
        record[0] += self.tcurr - item.t_queue
        record[1] += 1
        self.in_service.append((self.tcurr + self.get_delay(item), item))

    def accept(self, item):
        """Ставить вимогу на обслуговування або в чергу без перевірки обмеження черги (переведення вимог)."""
        item.t_queue = self.tcurr
        if self.busy < self.channels:
            self._start(item)
        else:
            self.queue.append(item)
        self._refresh()

    def in_act(self, item):
        self._before_change()
        if self.on_enter:
            self.on_enter(item)
        self.arrivals += 1
        if self.record_arrivals:
            self.arrival_times.append(self.tcurr)
        if self.can_accept():
            self.accept(item)
        else:
            self.failure += 1

    def _take_from_queue(self):
        if self.queue_key is None:
            return self.queue.pop(0)
        index = min(range(len(self.queue)), key=lambda i: (self.queue_key(self.queue[i]), i))
        return self.queue.pop(index)

    def out_act(self):
        self._before_change()
        finished = [item for t, item in self.in_service if t == self.tcurr]
        self.in_service = [(t, item) for t, item in self.in_service if t != self.tcurr]
        for _ in finished:
            self.quantity += 1
            if self.queue and self.busy < self.channels:
                self._start(self._take_from_queue())
        self._refresh()
        for item in finished:
            self.send(item)

    def do_statistics(self, delta):
        self.queue_area += len(self.queue) * delta
        self.busy_area += self.busy * delta

    def mean_queue(self, total_time):
        return self.queue_area / total_time

    def mean_load(self, total_time):
        """Частка часу зайнятості каналів; для необмеженої кількості каналів – середня кількість зайнятих."""
        channels = 1 if self.channels == math.inf else self.channels
        return self.busy_area / (total_time * channels)

    def mean_wait(self, kind=None):
        records = [self.wait[kind]] if kind is not None else list(self.wait.values())
        total, count = sum(r[0] for r in records), sum(r[1] for r in records)
        return total / count if count else 0.0

    def print_info(self):
        print(f"{self.name} state = {self.state} queue = {len(self.queue)} quantity = {self.quantity} "
              f"failure = {self.failure} tnext = {self.tnext:.4f}")


class Dispose(Element):
    """Вихід з моделі: фіксує моменти виходу й час перебування вимог за групами group(item)."""

    def __init__(self, name="DISPOSE", group=lambda item: item.kind):
        super().__init__(name=name)
        self.group = group
        self.stats_start = 0.0
        self.reset_statistics()

    def reset_statistics(self):
        super().reset_statistics()
        self.exit_times = []
        self.times = defaultdict(list)

    def in_act(self, item):
        self.quantity += 1
        self.exit_times.append(self.tcurr)
        if item.t_create >= self.stats_start:
            self.times[self.group(item)].append(self.tcurr - item.t_create)

    def all_times(self):
        return [t for values in self.times.values() for t in values]
