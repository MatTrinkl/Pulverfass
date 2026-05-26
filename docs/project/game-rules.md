## Spielregeln  


### Ziel des Spiels

Ziel des Spiels ist es, alle gegnerischen Spieler zu besiegen und sämtliche Gebiete der Spielkarte zu besitzen.


### Spieldauer

Das Spiel ist so ausgelegt, dass eine Partie circa 30 Minuten dauert.


### Spielaufbau

- Spielkarte (Map)

Die Spielkarte besteht aus insgesamt 24 Gebieten auf 6 Kontinenten.

Die Gebiete sind wie folgt aufgeteilt:
Australien: 2 Gebiete
Nordamerika: 4 Gebiete
Südamerika: 3 Gebiete
Afrika: 4 Gebiete
Europa: 5 Gebiete
Asien: 6 Gebiete

Jedes Gebiet gehört genau einem Spieler.  
Gebiete sind über Grenzen oder See-Verbindungen miteinander verbunden.  
Bewegungen und Angriffe sind nur zwischen verbundenen Gebieten möglich.  
In jedem Gebiet muss zu jedem Zeitpunkt mindestens eine Einheit platziert sein.


- Spieler

Drei bis sechs Spieler können pro Spiel antreten.  
Jeder Spieler kontrolliert eine eigene Armee.  
 Jeder Spieler durchläuft pro Spielzug 4 Phasen.


- Einheiten

Einheiten bezeichnen die Armee-Truppen und werden als Anzahl von Truppen dargestellt.
Alle Einheiten sind gleichwertig.


- Spielvorbereitung

Alle Gebiete werden zufällig, möglichst gleichmäßig auf die Spieler verteilt.  
Jeder Spieler platziert die erhaltenen Einheiten auf seinen Gebieten.
Die Menge der erhaltenen Einheiten hängt von der Spieleranzahl ab.  
Jedes Gebiet bekommt zumindest eine Einheit.  
Die Zugreihenfolge der Spieler wird durch Zufall festgelegt.

- Anzahl der Einheiten beim Start

Die Anzahl der zugewiesenen Truppeneinheiten hängt von der Anzahl der Spieler ab.  
Anzahl Spieler – Anzahl Truppen pro Spieler    
3 – 35  
4 – 30    
5 – 25    
6 – 20



### Spielablauf

Ein Spielzug besteht aus 4 Phasen, die in dieser Reihenfolge durchgeführt werden müssen:  ￼
1. Verstärkungen erhalten und platzieren
2. Angriffsphase (Befreiungsaktionen)
3. Truppenbewegung
4. Gebietskarte ziehen

**Phase 1: Verstärkung**

Zu Beginn eines Zuges erhält der Spieler neue Einheiten:

- Verstärkung durch Gebiete

Die Anzahl der Bonustruppen pro Gebiet errechnet sich mit der Formel: kontrollierte Gebiete ÷ 3, das Ergebnis wird abgerundet.
Ergibt die Anzahl weniger als drei Truppen, bekommt der Spieler trotzdem drei Einheiten.

- Verstärkung durch Spielkarten

Zusätzlich kann der Spieler seine gültigen Spielkarten-Sets gegen Verstärkungen eintauschen (siehe Spielkarten).
Hat ein Spieler mehr als 4 Karten und kann damit einen gültigen Satz bilden, muss er das tun und diesen gegen Truppen tauschen.


- Verstärkung durch Kontinente

Wer alle Gebiete eines Kontinents besitzt, erhält in jeder Verstärkungsphase folgende Bonus-Einheiten:  
Nordamerika: 2  
Europa: 2  
Asien: 3  
Südamerika: 1  
Afrika: 2  
Australien: 1


- Platzierung

Alle erhaltenen Einheiten müssen sofort auf die eigenen Gebiete verteilt werden.  Die Platzierung darf der Spieler frei bestimmen, wobei in jedem Gebiet zumindest eine Einheit stationiert sein muss.


**Phase 2: Angriff**

Ein Spieler kann in der Angriffsphase jedes Spielzugs beliebig viele Angriffe durchführen.

- Voraussetzungen für den Angriff

Das Zielgebiet muss an das angreifende Gebiet angrenzend sein (über Grenze oder See-Verbindung).  
Im angreifenden Gebiet müssen mindestens zwei Einheiten stehen.￼

- Angriffsdurchführung

Der Angreifer kann entscheiden wie viele der im angreifenden Gebiet stationierten Truppen er für den Angriff verwendet.  
Der Verteidiger nutzt immer alle im angegriffenen Gebiet stationierten Truppen zur Verteidigung.   
Das Kampfergebnis wird ueber eine deterministische, Risk-aehnliche Wuerfelregel bestimmt. Die technische Referenz dafuer ist `docs/architecture/battle-resolution.md`.   
Das Ergebnis eines Kampfes ist der Verlust von Truppen auf beiden Seiten sowie gegebenenfalls die Eroberung des Gebiets.


- Gebiet erobern

Sobald alle verteidigenden Einheiten besiegt sind, übernimmt der Angreifer das Gebiet und muss mindestens die Anzahl an Einheiten, die er für den Angriff verwendet hat, ins Gebiet verschieben.  
Der Angreifer darf zusätzlich weitere Einheiten in das eroberte Gebiet verschieben, sofern diese aus dem angreifenden Gebiet stammen.  
￼
- Spieler eliminieren

Ein Spieler ist vollständig besiegt, wenn er kein Gebiet mehr besitzt.  
Er scheidet dann aus dem Spiel aus.  
Seine Spielkarten gehen an den Angreifer, der sein letztes Gebiet erobert hat.


**Phase 3: Truppenbewegung**

Ein Spieler darf einmal pro Zug Truppen von genau einem eigenen Gebiet in genau ein anderes eigenes Gebiet verschieben.  
Es dürfen dabei beliebig viele Truppen verschoben werden, solange mindestens eine Einheit im Ursprungsgebiet bleibt.  Die Gebiete müssen über eine zusammenhängende Verbindung eigener Gebiete oder eine See-Verbindung erreichbar sein.


**Phase 4: Spielkarte ziehen**

Ein Spieler zieht genau eine zufällige Karte, wenn er in dem Spielzug mindestens ein Gebiet erobert hat.  
Auch wenn mehrere Gebiete erobert wurden, zieht er trotzdem nur eine Karte.



### Spielkarten

- Arten von Karten

Es gibt Spielkarten mit jeweils einem von drei Symbolen (A,B,C) und Jokerkarten.


- Ziehen von Karten

Das Ziehen der Spielkarten erfolgt am Ende des Spielzuges. Siehe Spiel-Phase 4.

- Einsetzen von Karten

Spielkarten können zu Beginn der Verstärkungsphase (Phase 1) gegen zusätzliche Verstärkungen eingetauscht werden. Dazu müssen sie in gültigen Sätzen abgegeben werden.
Besitzt ein Spieler zu Beginn seiner Verstärkungsphase fünf oder mehr Karten, muss er zu Beginn der Verstärkungsphase mindestens einen gültigen Kartensatz eintauschen.
Der Wert eingetauschter Kartensätze steigt im Verlauf des Spiels an. Der erste eingetauschte Satz bringt zwei Verstärkungseinheiten, der zweite vier, der dritte sechs, der vierte acht, und jeder weitere Satz zehn Einheiten.


- Gültige Kartensätze

Ein Satz besteht aus genau 3 Karten.  
Gültig sind:  
3x Symbol A  
3x Symbol B  
3x Symbol C    
1x Symbol A + 1x Symbol B + 1x Symbol C

Ein Joker kann als beliebige Karte eingesetzt werden, um einen Satz zu vervollständigen.

- Weitergabe der Spielkarten nach Ausscheiden aus dem Spiel.

Wenn ein Spieler vollständig besiegt wird, erhält der Angreifer alle Gebietskarten des besiegten Spielers.
Hat der Angreifer danach mehr als vier Karten, muss er in der nächsten Verstärkungsphase so viele wie möglich eintauschen, bis er wieder höchstens vier Karten hat oder keinen gültigen Kartensatz mehr hat.


### Spielende

Das Spiel endet, wenn ein Spieler alle Gebiete kontrolliert.
Dieser Spieler gewinnt das Spiel. 
