# Комп'ютерний практикум №7

Розробка моделей на основі технології Петрі-об'єктного моделювання.
Моделі реалізовано на Java з бібліотекою [PetriObjModelPaint](https://github.com/StetsenkoInna/PetriObjModelPaint)
(release 2.3.0): кожен Петрі-об'єкт – це `PetriSim` зі своєю мережею Петрі, модель – `PetriObjModel`, об'єкти
з'єднуються об'єднанням позицій (`linkObjectsCombiningPlaces`), імітацію виконує метод `PetriObjModel.go()`.

| Файл | Призначення |
|---|---|
| `src/lab7/NetBuilder.java`, `Delay.java` | побудова мережі Петрі об'єкта з координатами елементів |
| `src/lab7/PetriObject.java`, `ObjectModel.java` | Петрі-об'єкт і Петрі-об'єктна модель зі зв'язками між об'єктами |
| `src/lab7/Collector.java`, `Tracker.java` | статистика з розгоном, трасування, мітки маркерів через `SimulationStatisticCollector` |
| `src/lab7/Generator.java`, `Smo.java`, `Fork.java` | класи Петрі-об'єктів мережі масового обслуговування |
| `src/lab7/Machine.java`, `Transfer.java`, `AssignedRobot.java`, `RobotPool.java` | класи Петрі-об'єктів ГВС |
| `src/lab7/Stop.java`, `Bus.java` | класи Петрі-об'єктів маршруту мікроавтобусів |
| `src/lab7/Device.java` | клас Петрі-об'єкта пристрою конвеєра |
| `src/lab7/QueueNetwork.java` | завдання 1: тестування алгоритму на мережі масового обслуговування |
| `src/lab7/Fms.java` | завдання 2: гнучка виробнича система (задача 2 ЛР5), два варіанти закріплення роботів |
| `src/lab7/Minibus.java` | завдання 3: мікроавтобуси (задача 3 ЛР5) |
| `src/lab7/Conveyor.java` | завдання 4: конвеєрна система (задача 1 ЛР5), до якої записано рівняння |
| `src/lab7/Export.java` | документи .pnml моделей, зображення мереж об'єктів і моделей, опис мереж у JSON |
| `src/lab7/Queueing.java`, `Checks.java`, `Stats.java`, `Json.java`, `Output.java`, `Main.java` | допоміжні класи |
| `compare_lab5.py` | порівняння мереж і результатів з практикумом 5 |
| `plots.py` | графіки |
| `nets/` | моделі у форматі .pnml (відкриваються в редакторі), зображення моделей і класів Петрі-об'єктів |
| `results/` | вивід програм |
| `ІП-33_Ломакін_ЛР7.pdf` | звіт |

Потрібна Java 23+; `run.sh` завантажує бібліотеку в `lib/`, компілює і запускає всі моделі (≈ 1 хв):

```bash
cd lab7
./run.sh                 # усі завдання, збереження моделей, порівняння з ЛР5, графіки
./run.sh minibus         # окреме завдання: network | fms | minibus | conveyor | export
```
