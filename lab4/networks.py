"""Мережі масового обслуговування з N системами різної структури."""
import math

from elements import Create, Dispose, Process
from model import Model

ARRIVAL_MEAN, SERVICE_MEAN = 1.0, 0.3
FEEDBACK = 0.5


def _assemble(nodes, create, dispose, engine):
    return engine([create, *nodes, dispose])


def chain(n, engine=Model):
    """N послідовно з'єднаних систем: кожна вимога проходить усі N систем."""
    create, dispose = Create(ARRIVAL_MEAN, name="CREATE"), Dispose()
    nodes = [Process(SERVICE_MEAN, name=f"P{i + 1}") for i in range(n)]
    create.add_route(nodes[0])
    for current, following in zip(nodes, nodes[1:] + [dispose]):
        current.add_route(following)
    return _assemble(nodes, create, dispose, engine)


def tree(n, engine=Model):
    """Бінарне дерево з N = 2^d - 1 систем: після кожної системи вимога з ймовірністю 0,5 йде в одну
    з двох наступних, після листа – виходить з моделі."""
    create, dispose = Create(ARRIVAL_MEAN, name="CREATE"), Dispose()
    nodes = [Process(SERVICE_MEAN, name=f"P{i + 1}") for i in range(n)]
    create.add_route(nodes[0])
    for i, node in enumerate(nodes):
        children = [j for j in (2 * i + 1, 2 * i + 2) if j < n]
        if children:
            for j in children:
                node.add_route(nodes[j], probability=1 / len(children))
        else:
            node.add_route(dispose)
    return _assemble(nodes, create, dispose, engine)


def parallel(n, engine=Model):
    """N паралельних систем: вимога з однаковою ймовірністю потрапляє в одну з них і виходить."""
    create, dispose = Create(ARRIVAL_MEAN, name="CREATE"), Dispose()
    nodes = [Process(SERVICE_MEAN, name=f"P{i + 1}") for i in range(n)]
    for node in nodes:
        create.add_route(node, probability=1 / n)
        node.add_route(dispose)
    return _assemble(nodes, create, dispose, engine)


def feedback(n, engine=Model):
    """Ланцюг з N систем з поверненням: після останньої системи вимога з ймовірністю 0,5
    повертається в першу."""
    create, dispose = Create(ARRIVAL_MEAN, name="CREATE"), Dispose()
    nodes = [Process(SERVICE_MEAN, name=f"P{i + 1}") for i in range(n)]
    create.add_route(nodes[0])
    for current, following in zip(nodes, nodes[1:]):
        current.add_route(following)
    nodes[-1].add_route(dispose, probability=1 - FEEDBACK)
    nodes[-1].add_route(nodes[0], probability=FEEDBACK)
    return _assemble(nodes, create, dispose, engine)


def path_length(structure, n):
    """Середня кількість систем, які проходить одна вимога."""
    return {
        "chain": n,
        "tree": math.log2(n + 1),
        "parallel": 1,
        "feedback": n / (1 - FEEDBACK),
    }[structure]


def sojourn_time(structure, n):
    """Середній час проходження вимоги через мережу в сталому режимі (кожна система – M/M/1)."""
    rho = SERVICE_MEAN / ARRIVAL_MEAN
    if structure == "feedback":
        rho /= 1 - FEEDBACK
    return path_length(structure, n) * SERVICE_MEAN / (1 - rho)


STRUCTURES = {"chain": chain, "tree": tree, "parallel": parallel, "feedback": feedback}
TITLES = {"chain": "послідовний ланцюг", "tree": "бінарне дерево", "parallel": "паралельні гілки",
          "feedback": "ланцюг з поверненням"}
