package com.example.ui.screens

data class LeaderboardEntry(
    val rank: Int,
    val name: String,
    val rating: Int,
    val countryCode: String,
    val winRate: String,
    val status: String
)

object Language {
    
    val supportedLanguages = listOf(
        "RU" to "🇷🇺 Русский",
        "EN" to "🇬🇧 English",
        "ES" to "🇪🇸 Español",
        "DE" to "🇩🇪 Deutsch",
        "FR" to "🇫🇷 Français",
        "TR" to "🇹🇷 Türkçe",
        "IT" to "🇮🇹 Italiano",
        "PT" to "🇵🇹 Português",
        "AR" to "🇸🇦 العربية",
        "ZH" to "🇨🇳 中文"
    )

    val worldLeaderboardList = listOf(
        LeaderboardEntry(1, "Magnus_Nardy 👑", 2850, "🇳🇴 NO", "68.4%", "Online"),
        LeaderboardEntry(2, "Oleg_Tashkent", 2792, "🇺🇿 UZ", "65.1%", "Online"),
        LeaderboardEntry(3, "Jean_Pierre_Paris", 2740, "🇫🇷 FR", "62.8%", "Offline"),
        LeaderboardEntry(4, "Onion_Master_Tor", 2690, "🧅 TOR", "60.5%", "Online (Tor)"),
        LeaderboardEntry(5, "Fatma_Istanbul", 2655, "🇹🇷 TR", "59.2%", "Offline"),
        LeaderboardEntry(6, "John_Doe_SimpleX", 2620, "🇺🇸 US", "58.7%", "Online (SMP)"),
        LeaderboardEntry(7, "NardyPro_99 (You)", 1500, "🇷🇺 RU", "50.0%", "Online"),
        LeaderboardEntry(8, "Hans_Berlin", 1490, "🇩🇪 DE", "49.3%", "Offline"),
        LeaderboardEntry(9, "Carlos_Madrid", 1420, "🇪🇸 ES", "48.5%", "Offline"),
        LeaderboardEntry(10, "Marco_Milano", 1410, "🇮🇹 IT", "47.9%", "Online")
    )

    fun get(key: String, lang: String): String {
        val dict = uiTranslations[lang] ?: uiTranslations["EN"] ?: emptyMap()
        return dict[key] ?: uiTranslations["EN"]?.get(key) ?: uiTranslations["RU"]?.get(key) ?: key
    }

    private val baseEn = mapOf(
        "app_title" to "SHESH BESH",
        "app_title_short" to "BACKGAMMON",
        "welcome_title" to "SHESH BESH",
        "menu_restart" to "Restart Game",
        "menu_logs" to "Move History",
        "menu_stats" to "Statistics",
        "menu_settings" to "Settings",
        "menu_rules" to "Game Rules",
        "menu_leaderboard" to "World Leaderboard",
        "score_title" to "Checkers",
        "white" to "White",
        "black" to "Black",
        "roll_dice" to "ROLL DICE",
        "bear_off" to "BEAR OFF",
        "winner" to "Winner!",
        "bot_thinking" to "AI Bot is calculating strategic moves...",
        "settings_title" to "Game Settings",
        "ai_opponent" to "Computer Opponent",
        "ai_opponent_desc" to "Play against phone's strategic tactical AI",
        "auto_roll" to "Auto Dice Roll",
        "auto_roll_desc" to "Automatically roll dice on turn transition",
        "radio_title" to "Internet Cyber Radio",
        "add_station" to "Add Custom Station:",
        "station_name" to "Station Name",
        "station_url" to "Stream URL",
        "done" to "Done",
        "close" to "Close",
        "stats_title" to "Match Statistics",
        "stats_played" to "Matches Played",
        "stats_won" to "You Won",
        "stats_lost" to "Bot Won",
        "stats_history" to "Recent Match History:",
        "stats_empty" to "No recorded matches yet. Play your first game!",
        "stats_clear" to "Reset History",
        "time_label" to "Time",
        "server_title" to "Central Game Node (BETA)",
        "server_desc" to "Connect to distributed multiplayer network",
        "server_url_label" to "Node Address (SimpleX P2P / Tor .onion)",
        "connect" to "Connect",
        "disconnect" to "Disconnect",
        "connected_status" to "CONNECTION STATUS: ",
        "status_disconnected" to "DISCONNECTED",
        "status_connecting" to "CONNECTING...",
        "status_connected" to "ACTIVE",
        "status_error" to "ERROR",
        "tor_title" to "Local SOCKS5 Tor Router",
        "tor_desc" to "Conceal IP and metadata through onion network",
        "tor_status_label" to "Tor Connection Status: ",
        "tor_logs_label" to "Tor Gateway Logs:",
        "simplex_title" to "Embedded SimpleX P2P Messenger",
        "simplex_desc" to "Anonymous decentralized chat (Zero User ID metadata)",
        "simplex_handle_label" to "Your SimpleX Callsign",
        "simplex_code_label" to "Local P2P Contact Code (Copy)",
        "chat_placeholder" to "Type encrypted E2EE message...",
        "send" to "Send",
        "rule_1_title" to "1. Movement Rules",
        "rule_1_desc" to "In Long Backgammon checkers move counter-clockwise. In Short Backgammon White and Black move towards each other with hit & bar entry mechanics.",
        "rule_2_title" to "2. Head Rules",
        "rule_2_desc" to "Only one checker can be taken from the head per turn. Exception: First roll doubles (3:3, 4:4, 6:6) in Long Backgammon.",
        "rule_3_title" to "3. Cascade Doubles & Turn Passing",
        "rule_3_desc" to "Unplayed moves are passed to the opponent when completely blocked.",
        "rule_4_title" to "4. Home Board Bearing Off",
        "rule_4_desc" to "Once all checkers enter the home quadrant, bear them off with exact or high die rolls.",
        "rule_5_title" to "5. Doubling Cube (Dave)",
        "rule_5_desc" to "Raise the stake multiplier before rolling dice.",
        "leaderboard_rank" to "Rank",
        "leaderboard_player" to "Player / Protocol",
        "leaderboard_rating" to "Elo Rating",
        "leaderboard_winrate" to "Winrate",
        "leaderboard_status" to "Status",
        "online_search_opponent" to "FIND OPPONENT (P2P)",
        "online_connecting_tor" to "Initiating Tor circuit routing...",
        "online_handshake_smp" to "Establishing SimpleX E2EE handshake...",
        "online_searching" to "Broadcasting matchmaking ping...",
        "online_found_match" to "Opponent matched: %1\$s (Rating: %2\$s)",
        "online_game_start" to "Match started! Playing as: %1\$s",
        "online_received_packet" to "P2P Packet: %s",
        "online_your_turn" to "It is your turn! Roll dice.",
        "welcome_slogan" to "TACTICAL CYBERPUNK BACKGAMMON & P2P ARENA",
        "welcome_select_lang" to "🌐 Select Interface Language",
        "welcome_select_radio" to "🎵 Station & Soundtrack",
        "welcome_radio_subtitle" to "Choose background atmosphere stream:",
        "welcome_radio_disable" to "🔇 Silent play mode (Radio off)",
        "welcome_select_mode" to "🎮 Select Backgammon Rules",
        "welcome_mode_bot" to "🤖 Strategic AI Opponent",
        "welcome_mode_local" to "👥 Two Players (Pass & Play)",
        "welcome_mode_online" to "🌐 Secure P2P (SimpleX / Tor)",
        "welcome_start_btn" to "ENTER SALON! 🎲",
        "welcome_back" to "BACK",
        "welcome_next" to "NEXT",
        "welcome_ask_name" to "✍️ ENTER CALLSIGN",
        "welcome_name_your" to "Callsign",
        "welcome_placeholder_player" to "Player",
        "welcome_p1_label" to "Player 1 (White)",
        "welcome_p1_placeholder" to "Player 1",
        "welcome_p2_label" to "Player 2 (Black)",
        "welcome_p2_placeholder" to "Player 2",
        "welcome_btn_finish" to "START! 🎲",
        "splash_tap_to_continue" to "Tap to continue",
        "splash_cyber_romantic" to "Cyberpunk Salon • SHESH BESH",
        "game_mode_mad_title" to "Mad Backgammon",
        "game_mode_mad_desc" to "Cascading doubles, turn transfers and fast-paced bearing off",
        "game_mode_long_title" to "Long Backgammon (Nardy)",
        "game_mode_long_desc" to "Traditional long backgammon rules with one-from-head restriction",
        "game_mode_short_title" to "Short Backgammon",
        "game_mode_short_desc" to "Standard backgammon with hits, bar entry and home bearing off",
        "lot_title" to "Toss / Draw",
        "lot_stage1_title" to "Stage 1: Color Selection",
        "lot_stage1_desc" to "Roll dice to determine who plays White (highest roll).",
        "lot_stage2_title" to "Stage 2: Opening Move",
        "lot_stage2_desc" to "Roll dice to determine who moves first.",
        "lot_result_tie" to "Tie! Re-roll required.",
        "lot_result_winner_colors_white" to "You won White checkers!",
        "lot_result_winner_colors_black" to "Opponent won White checkers!",
        "lot_result_first_move_white" to "White won opening move (%1\$s vs %2\$s)!",
        "lot_result_first_move_black" to "Black won opening move (%1\$s vs %2\$s)!",
        "lot_continue_btn" to "Continue",
        "lot_start_game_btn" to "Start Game",
        "lot_roll_btn" to "Roll Die"
    )

    private val uiTranslations: Map<String, Map<String, String>> = mapOf(
        "RU" to baseEn.toMutableMap().apply {
            put("lot_title", "Жеребьёвка")
            put("lot_stage1_title", "Этап 1: Определение цвета")
            put("lot_stage1_desc", "Бросок кубиков определяет, кто играет Белыми (наибольшее число).")
            put("lot_stage2_title", "Этап 2: Первый ход")
            put("lot_stage2_desc", "Бросок кубиков определяет, кто ходит первым.")
            put("lot_result_tie", "Ничья! Требуется переброс.")
            put("lot_result_winner_colors_white", "Вы выиграли Белые фишки!")
            put("lot_result_winner_colors_black", "Соперник выиграл Белые фишки!")
            put("lot_result_first_move_white", "Белые выиграли первый ход (%1\$s против %2\$s)!")
            put("lot_result_first_move_black", "Черные выиграли первый ход (%1\$s против %2\$s)!")
            put("lot_continue_btn", "Продолжить")
            put("lot_start_game_btn", "Начать игру")
            put("lot_roll_btn", "Бросить кубик")
            put("app_title", "ШЕШ БЕШ")
            put("app_title_short", "НАРДЫ")
            put("welcome_title", "ШЕШ БЕШ")
            put("menu_restart", "Начать заново")
            put("menu_logs", "История ходов")
            put("menu_stats", "Статистика")
            put("menu_settings", "Настройки")
            put("menu_rules", "Правила игры")
            put("menu_leaderboard", "Мировой рейтинг")
            put("score_title", "Фишки")
            put("white", "Белые")
            put("black", "Черные")
            put("roll_dice", "БРОСИТЬ ЗАРИКИ")
            put("bear_off", "ВЫБРОСИТЬ ФИШКУ")
            put("winner", "Победитель!")
            put("bot_thinking", "Бот обдумывает безумный ход...")
            put("settings_title", "Параметры игры")
            put("ai_opponent", "Компьютерный оппонент")
            put("ai_opponent_desc", "Играть против ИИ-бота Вашего телефона")
            put("auto_roll", "Быстрый авто-ход")
            put("auto_roll_desc", "Зарики катятся сами на переходе хода")
            put("radio_title", "Интернет-радиоприёмник")
            put("add_station", "Добавить свою станцию:")
            put("station_name", "Имя станции")
            put("station_url", "URL потока")
            put("done", "Готово")
            put("close", "Закрыть")
            put("stats_title", "Статистика Матчей")
            put("stats_played", "Матчей")
            put("stats_won", "Вы выиграли")
            put("stats_lost", "Бот выиграл")
            put("stats_history", "История последних партий:")
            put("stats_empty", "История раундов пуста. Сыграйте первый матч!")
            put("stats_clear", "Сбросить историю")
            put("time_label", "Время")
            put("server_title", "Центральный сервер игры (БЕТА)")
            put("server_desc", "Подключение к распределенному игровому серверу для онлайн-игры")
            put("server_url_label", "Адрес сервера (SimpleX P2P / Tor .onion)")
            put("connect", "Подключить")
            put("disconnect", "Отключить")
            put("connected_status", "СТАТУС ПОДКЛЮЧЕНИЯ: ")
            put("status_disconnected", "ОТКЛЮЧЕНО")
            put("status_connecting", "ПОДКЛЮЧЕНИЕ...")
            put("status_connected", "АКТИВНО")
            put("status_error", "ОШИБКА")
            put("tor_title", "Локальный Tor Маршрутизатор SOCKS5")
            put("tor_desc", "Защита метаданных и скрытие IP-адреса через луковую сеть")
            put("tor_status_label", "Статус Tor соединения: ")
            put("tor_logs_label", "Логи шлюза встроенного Tor:")
            put("simplex_title", "Встроенный SimpleX P2P Мессенджер")
            put("simplex_desc", "Анонимный децентрализованный чат (без постоянных метаданных ID)")
            put("simplex_handle_label", "Ваш SimpleX позывной")
            put("simplex_code_label", "Ваш локальный P2P код контакта (Скопировать)")
            put("chat_placeholder", "Пишите шифрованное P2P сообщение...")
            put("send", "Отправить")
            put("rule_1_title", "1. Правила перемещения")
            put("rule_1_desc", "В Длинных нардах все фишки движутся против часовой стрелки. В Коротких нардах белые и черные движутся навстречу друг другу с выбиванием одиночных фишек на бар.")
            put("rule_2_title", "2. Снятие с Головы")
            put("rule_2_desc", "С головы разрешено снимать только одну шашку за ход. Исключение: дубли 3:3, 4:4, 6:6 на первом ходу в длинных нардах.")
            put("rule_3_title", "3. Бешеный вариант и передача хода")
            put("rule_3_desc", "Ход передается сопернику только тогда, когда у игрока нет ходов. Несыгранные ходы доигрывает соперник!")
            put("rule_4_title", "4. Запрет ходов внутри Дома")
            put("rule_4_desc", "В доме возможен только непосредственный выброс фишек за пределы доски при выпадении кубиков.")
            put("rule_5_title", "5. Куб удвоения (Даве)")
            put("rule_5_desc", "Позволяет поднять ставку в текущей партии до броска кубиков.")
            put("leaderboard_rank", "Место")
            put("leaderboard_player", "Игрок / Протокол")
            put("leaderboard_rating", "Эло Рейтинг")
            put("leaderboard_winrate", "Винрейт")
            put("leaderboard_status", "Статус")
            put("online_search_opponent", "ПОИСК СОПЕРНИКА (P2P)")
            put("online_connecting_tor", "Установка Tor маршрутизации...")
            put("online_handshake_smp", "E2EE рукопожатие SimpleX...")
            put("online_searching", "Поиск соперника в сети...")
            put("online_found_match", "Соперник найден: %1\$s (Рейтинг: %2\$s)")
            put("online_game_start", "Партия началась! Цвет: %1\$s")
            put("online_received_packet", "P2P пакет: %s")
            put("online_your_turn", "Ваш ход! Бросайте кубики.")
            put("welcome_slogan", "ТАКТИЧЕСКИЕ НАРДЫ И КИБЕРПАНК P2P АРЕНА")
            put("welcome_select_lang", "🌐 Выберите язык интерфейса")
            put("welcome_select_radio", "🎵 Радиостанция и музыка")
            put("welcome_radio_subtitle", "Выберите атмосферное фоновое сопровождение:")
            put("welcome_radio_disable", "🔇 Без музыки (Тихий режим)")
            put("welcome_select_mode", "🎮 Выберите правила нард")
            put("welcome_mode_bot", "🤖 Играть против ИИ-бота")
            put("welcome_mode_local", "👥 Вдвоем на одном экране (Pass & Play)")
            put("welcome_mode_online", "🌐 Защищенная P2P игра (SimpleX/Tor)")
            put("welcome_start_btn", "ВОЙТИ В ИГРОВОЙ САЛОН! 🎲")
            put("welcome_back", "НАЗАД")
            put("welcome_next", "ДАЛЕЕ")
            put("welcome_ask_name", "✍️ ВАШ ПОЗЫВНОЙ В ИГРЕ")
            put("welcome_name_your", "Позывной")
            put("welcome_placeholder_player", "Игрок")
            put("welcome_p1_label", "Игрок 1 (Белые)")
            put("welcome_p1_placeholder", "Игрок 1")
            put("welcome_p2_label", "Игрок 2 (Черные)")
            put("welcome_p2_placeholder", "Игрок 2")
            put("welcome_btn_finish", "НАЧАТЬ! 🎲")
            put("splash_tap_to_continue", "Коснитесь экрана для продолжения")
            put("splash_cyber_romantic", "ШЕШ БЕШ • Тактический салон")
            put("game_mode_mad_title", "Бешеные нарды (Mad)")
            put("game_mode_mad_desc", "Каскадные дубли, передача оставшихся ходов сопернику")
            put("game_mode_long_title", "Длинные нарды (Long)")
            put("game_mode_long_desc", "Классические правила длинных нард, ход с головы")
            put("game_mode_short_title", "Короткие нарды (Short)")
            put("game_mode_short_desc", "Классические правила с баром и сбиванием одиночных фишек")
        },
        "EN" to baseEn,
        "DE" to baseEn.toMutableMap().apply {
            put("app_title", "SHESH BESH")
            put("welcome_title", "SHESH BESH")
            put("app_title_short", "BACKGAMMON")
            put("menu_restart", "Neustart")
            put("menu_logs", "Spielverlauf")
            put("menu_stats", "Statistiken")
            put("menu_settings", "Einstellungen")
            put("menu_rules", "Regeln")
            put("menu_leaderboard", "Rangliste")
            put("score_title", "Steine")
            put("white", "Weiß")
            put("black", "Schwarz")
            put("roll_dice", "WÜRFELN")
            put("bear_off", "HINAUSWERFEN")
            put("winner", "Gewinner!")
            put("bot_thinking", "KI berechnet Zug...")
            put("settings_title", "Optionen")
            put("ai_opponent", "KI-Gegner")
            put("welcome_slogan", "TAKTIK-BACKGAMMON & P2P-ARENA")
            put("welcome_placeholder_player", "Spieler")
            put("welcome_p1_placeholder", "Spieler 1")
            put("welcome_p2_placeholder", "Spieler 2")
            put("done", "Fertig")
            put("close", "Schließen")
        },
        "ES" to baseEn.toMutableMap().apply {
            put("app_title", "SHESH BESH")
            put("welcome_title", "SHESH BESH")
            put("app_title_short", "BACKGAMMON")
            put("menu_restart", "Reiniciar")
            put("menu_logs", "Historial")
            put("menu_stats", "Estadísticas")
            put("menu_settings", "Ajustes")
            put("menu_rules", "Reglas")
            put("menu_leaderboard", "Clasificación")
            put("score_title", "Fichas")
            put("white", "Blancas")
            put("black", "Negras")
            put("roll_dice", "LANZAR DADOS")
            put("bear_off", "SACAR FICHA")
            put("winner", "¡Ganador!")
            put("bot_thinking", "La IA está pensando...")
            put("settings_title", "Ajustes del juego")
            put("welcome_slogan", "BACKGAMMON TÁCTICO Y ARENA P2P")
            put("welcome_placeholder_player", "Jugador")
            put("welcome_p1_placeholder", "Jugador 1")
            put("welcome_p2_placeholder", "Jugador 2")
            put("done", "Listo")
            put("close", "Cerrar")
        },
        "FR" to baseEn.toMutableMap().apply {
            put("app_title", "SHESH BESH")
            put("welcome_title", "SHESH BESH")
            put("app_title_short", "BACKGAMMON")
            put("menu_restart", "Recommencer")
            put("menu_logs", "Historique")
            put("menu_stats", "Statistiques")
            put("menu_settings", "Paramètres")
            put("menu_rules", "Règles")
            put("menu_leaderboard", "Classement")
            put("score_title", "Pions")
            put("white", "Blancs")
            put("black", "Noirs")
            put("roll_dice", "LANCER DÉS")
            put("bear_off", "SORTIR PION")
            put("winner", "Gagnant !")
            put("bot_thinking", "L'IA réfléchit...")
            put("settings_title", "Paramètres")
            put("welcome_slogan", "BACKGAMMON TACTIQUE & ARÈNE P2P")
            put("welcome_placeholder_player", "Joueur")
            put("welcome_p1_placeholder", "Joueur 1")
            put("welcome_p2_placeholder", "Joueur 2")
            put("done", "Terminé")
            put("close", "Fermer")
        },
        "TR" to baseEn.toMutableMap().apply {
            put("app_title", "SHESH BESH")
            put("welcome_title", "SHESH BESH")
            put("app_title_short", "TAVLA")
            put("menu_restart", "Yeniden Başlat")
            put("menu_logs", "Hamle Geçmişi")
            put("menu_stats", "İstatistikler")
            put("menu_settings", "Ayarlar")
            put("menu_rules", "Kurallar")
            put("menu_leaderboard", "Liderlik Tablosu")
            put("score_title", "Pullar")
            put("white", "Beyaz")
            put("black", "Siyah")
            put("roll_dice", "ZAR AT")
            put("bear_off", "PUL TOPLA")
            put("winner", "Kazanan!")
            put("bot_thinking", "Yapay Zeka düşünüyor...")
            put("settings_title", "Oyun Ayarları")
            put("welcome_slogan", "TAKTİK TAVLA VE P2P ARENASI")
            put("welcome_placeholder_player", "Oyuncu")
            put("welcome_p1_placeholder", "1. Oyuncu")
            put("welcome_p2_placeholder", "2. Oyuncu")
            put("done", "Tamam")
            put("close", "Kapat")
        },
        "IT" to baseEn.toMutableMap().apply {
            put("app_title", "SHESH BESH")
            put("welcome_title", "SHESH BESH")
            put("app_title_short", "BACKGAMMON")
            put("menu_restart", "Ricomincia")
            put("menu_logs", "Cronologia")
            put("menu_stats", "Statistiche")
            put("menu_settings", "Impostazioni")
            put("menu_rules", "Regole")
            put("menu_leaderboard", "Classifica")
            put("score_title", "Pedine")
            put("white", "Bianche")
            put("black", "Nere")
            put("roll_dice", "LANCIA DADI")
            put("bear_off", "PORTA FUORI")
            put("winner", "Vincitore!")
            put("bot_thinking", "Il bot sta pensando...")
            put("settings_title", "Impostazioni")
            put("welcome_slogan", "BACKGAMMON TATTICO & ARENA P2P")
            put("welcome_placeholder_player", "Giocatore")
            put("welcome_p1_placeholder", "Giocatore 1")
            put("welcome_p2_placeholder", "Giocatore 2")
            put("done", "Fatto")
            put("close", "Chiudi")
        },
        "PT" to baseEn.toMutableMap().apply {
            put("app_title", "SHESH BESH")
            put("welcome_title", "SHESH BESH")
            put("app_title_short", "GAMÃO")
            put("menu_restart", "Reiniciar")
            put("menu_logs", "Histórico")
            put("menu_stats", "Estatísticas")
            put("menu_settings", "Definições")
            put("menu_rules", "Regras")
            put("menu_leaderboard", "Classificação")
            put("score_title", "Peças")
            put("white", "Brancas")
            put("black", "Pretas")
            put("roll_dice", "LANÇAR DADOS")
            put("bear_off", "RETIRAR PEÇA")
            put("winner", "Vencedor!")
            put("bot_thinking", "IA calculando jogada...")
            put("settings_title", "Definições")
            put("welcome_slogan", "GAMÃO TÁTICO & ARENA P2P")
            put("welcome_placeholder_player", "Jogador")
            put("welcome_p1_placeholder", "Jogador 1")
            put("welcome_p2_placeholder", "Jogador 2")
            put("done", "Concluído")
            put("close", "Fechar")
        },
        "AR" to baseEn.toMutableMap().apply {
            put("app_title", "SHESH BESH")
            put("welcome_title", "SHESH BESH")
            put("app_title_short", "شيش بيش")
            put("menu_restart", "إعادة البدء")
            put("menu_logs", "سجل الحركات")
            put("menu_stats", "الإحصائيات")
            put("menu_settings", "الإعدادات")
            put("menu_rules", "قواعد اللعبة")
            put("menu_leaderboard", "قائمة المتصدرين")
            put("score_title", "القطع")
            put("white", "أبيض")
            put("black", "أسود")
            put("roll_dice", "رمي النرد")
            put("bear_off", "إخراج القطعة")
            put("winner", "الفائز!")
            put("bot_thinking", "الذكاء الاصطناعي يفكر...")
            put("settings_title", "إعدادات اللعبة")
            put("welcome_slogan", "شيش بيش التكتيكية وساحة P2P")
            put("welcome_placeholder_player", "لاعب")
            put("welcome_p1_placeholder", "لاعب 1")
            put("welcome_p2_placeholder", "لاعب 2")
            put("done", "تم")
            put("close", "إغلاق")
        },
        "ZH" to baseEn.toMutableMap().apply {
            put("app_title", "SHESH BESH")
            put("welcome_title", "SHESH BESH")
            put("app_title_short", "双陆棋")
            put("menu_restart", "重新开始")
            put("menu_logs", "对局记录")
            put("menu_stats", "统计数据")
            put("menu_settings", "设置")
            put("menu_rules", "游戏规则")
            put("menu_leaderboard", "全球排行榜")
            put("score_title", "棋子")
            put("white", "白棋")
            put("black", "黑棋")
            put("roll_dice", "掷骰子")
            put("bear_off", "收回棋子")
            put("winner", "胜利者！")
            put("bot_thinking", "AI正在计算走法...")
            put("settings_title", "游戏设置")
            put("welcome_slogan", "战术双陆棋与P2P对战竞技场")
            put("welcome_placeholder_player", "玩家")
            put("welcome_p1_placeholder", "玩家 1")
            put("welcome_p2_placeholder", "玩家 2")
            put("done", "完成")
            put("close", "关闭")
        }
    )
}
