import heapq
import itertools
import math

from elements import Process
from model import Model


class FastModel(Model):
    """Алгоритм імітації з календарем подій у вигляді двійкової купи.

    Момент найближчої події береться з вершини купи за O(1), а не пошуком серед усіх елементів;
    елемент, у якого змінився момент наступної події, додається в купу за O(log N). Статистику
    накопичує лише елемент, стан якого змінюється, – за час від попередньої зміни його стану.
    """

    def __init__(self, elements):
        super().__init__(elements)
        self.calendar = []
        self.planned = {}
        self.last_change = {}
        self.order = itertools.count()
        for e in elements:
            e.listener = self
            self.last_change[e] = 0.0
            self.after(e)

    def before(self, element):
        element.tcurr = self.tcurr
        element.do_statistics(self.tcurr - self.last_change[element])
        self.last_change[element] = self.tcurr

    def after(self, element):
        if element.tnext != math.inf and self.planned.get(element) != element.tnext:
            self.planned[element] = element.tnext
            heapq.heappush(self.calendar, (element.tnext, next(self.order), element))

    def simulate(self, time, warmup=0.0, trace_events=0):
        while self.calendar:
            t, _, element = self.calendar[0]
            if self.planned.get(element) != t:
                heapq.heappop(self.calendar)  # застарілий запис: момент події елемента вже змінився
                continue
            if t > time:
                break
            heapq.heappop(self.calendar)
            del self.planned[element]
            self.tcurr = t
            element.tcurr = t
            element.out_act()
            self.after(element)
            self.events += 1
        self.tcurr = time
        for e in self.elements:
            self.before(e)

    def results(self):
        out = super().results()
        processes = [e for e in self.elements if isinstance(e, Process)]
        out["mean_in_system"] = sum(p.queue_area + p.busy_area for p in processes) / self.tcurr
        return out
