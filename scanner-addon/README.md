# Storage Finder Scanner

Необязательный клиентский аддон для Storage Finder. Он по очереди обновляет ближайшие контейнеры через обычные,
проверяемые сервером взаимодействия. Аддон не читает инвентарь закрытых block entity, не увеличивает дистанцию
взаимодействия и не открывает хранилища сквозь стены.

## Использование

1. Установите JAR-файлы `storage-finder` и `storage-finder-scanner`.
2. Встаньте неподвижно рядом с видимыми сундуками, бочками или установленными шалкеровыми ящиками.
3. Нажмите **F8** для запуска. Повторное нажатие остановит сканирование.
4. Сканер ненадолго откроет и закроет каждое доступное хранилище, а основной Storage Finder штатно запишет меню.

Клавиша меняется в настройках управления Minecraft. Сканирование прекращается при движении более чем на 0,25 блока,
смене мира или открытии постороннего экрана. В приседе сканирование не запускается, чтобы случайно не поставить предмет
из руки. Двойной сундук обновляется один раз.

Аддон намеренно использует видимое движение камеры, консервативную дистанцию 4,5 блока и паузы между действиями.
Правила сервера всё равно могут считать любое автоматизированное взаимодействие макросом — перед использованием на
чужом сервере получите разрешение администрации.

## English

Optional client-side addon for Storage Finder. It refreshes nearby container entries by performing normal,
server-validated container interactions one at a time. It does not read closed block-entity inventories,
bypass interaction distance, interact through walls, or require a server-side mod.

## Usage

1. Install both `storage-finder` and `storage-finder-scanner` JARs.
2. Stand still near a group of visible chests, barrels, or placed shulker boxes.
3. Press **F8** to start. Press it again at any time to stop.
4. Let the scanner briefly open and close each reachable container. Storage Finder records the menus normally.

The key can be changed in Minecraft's Controls screen. The scan stops if the player moves more than 0.25 blocks,
changes worlds, or opens an unrelated screen. Sneaking prevents a scan from starting so the held item cannot be
placed accidentally. Double chests are refreshed once.

The addon deliberately uses visible camera movement, a conservative 4.5-block range, and delays between
interactions. Server rules may still classify any automated interaction as a macro; ask the server administration
before using it on a server you do not control.
