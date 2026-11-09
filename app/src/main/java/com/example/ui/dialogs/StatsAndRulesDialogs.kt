package com.example.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.ui.GameViewModel
import com.example.data.MatchHistory
import com.example.ui.screens.Language
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Диалог персональной статистики игрока:
 * Отображает количество сыгранных, выигранных и проигранных партий,
 * а также подробную историю последних матчей с длительностью и счетом.
 */
@Composable
fun StatsDialog(
    viewModel: GameViewModel,
    onDismiss: () -> Unit
) {
    val matches by viewModel.matchHistory.collectAsState()
    val totalMatches = matches.size
    val humanWins = matches.count { it.winner == it.playerColor }
    val botWins = matches.count { it.winner != it.playerColor }
    val lang = viewModel.selectedLanguage

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = Language.get("stats_title", lang),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                            RoundedCornerShape(12.dp)
                        )
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(Language.get("stats_played", lang), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("$totalMatches", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(Language.get("stats_won", lang), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("$humanWins", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF43A047))
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(Language.get("stats_lost", lang), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("$botWins", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFFE53935))
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = Language.get("stats_history", lang),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Start
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .padding(top = 4.dp)
                ) {
                    if (matches.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                text = Language.get("stats_empty", lang),
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        }
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(matches) { match ->
                                MatchHistoryRow(match, lang)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { viewModel.clearStatsHistory() },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(Language.get("stats_clear", lang), fontSize = 12.sp)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(Language.get("close", lang), fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

/**
 * Строка элемента истории завершенной партии.
 */
@Composable
fun MatchHistoryRow(match: MatchHistory, lang: String) {
    val sdf = SimpleDateFormat("dd.MM.yy HH:mm", Locale.getDefault())
    val dateStr = sdf.format(Date(match.date))
    val youWon = match.winner == match.playerColor
    val winnerLabel = if (match.winner == "WHITE") Language.get("white", lang) else Language.get("black", lang)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp, horizontal = 2.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
            .padding(8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = "$winnerLabel ${Language.get("winner", lang)}" + if (match.isAgainstBot) (if (youWon) " (★)" else " (🤖)") else "",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = if (youWon) Color(0xFF43A047) else Color(0xFFE53935)
            )
            Text(
                text = "$dateStr • ${Language.get("time_label", lang)}: ${match.gameDurationSeconds}s",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = "${match.scorePlayer} : ${match.scoreOpponent}",
            fontSize = 13.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

/**
 * Диалог с правилами игры: Длинные нарды, Бешеные нарды (Mad), Короткие нарды.
 */
@Composable
fun RulesDialog(lang: String, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = Language.get("menu_rules", lang),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(12.dp))

                Box(modifier = Modifier.height(240.dp)) {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        item {
                            RuleItem(
                                title = Language.get("rule_1_title", lang),
                                desc = Language.get("rule_1_desc", lang)
                            )
                            RuleItem(
                                title = Language.get("rule_2_title", lang),
                                desc = Language.get("rule_2_desc", lang)
                            )
                            RuleItem(
                                title = Language.get("rule_3_title", lang),
                                desc = Language.get("rule_3_desc", lang)
                            )
                            RuleItem(
                                title = Language.get("rule_4_title", lang),
                                desc = Language.get("rule_4_desc", lang)
                            )
                            RuleItem(
                                title = Language.get("rule_5_title", lang),
                                desc = Language.get("rule_5_desc", lang)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(Language.get("close", lang))
                }
            }
        }
    }
}

/**
 * Элемент одного правила с заголовком и пояснением.
 */
@Composable
fun RuleItem(title: String, desc: String) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text(title, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(2.dp))
        Text(desc, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 14.sp)
    }
}

/**
 * Диалог таблицы рекордов и мирового рейтинга игроков.
 */
@Composable
fun LeaderboardDialog(lang: String, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = Language.get("menu_leaderboard", lang),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(12.dp))

                // Table Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 6.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(Language.get("leaderboard_rank", lang), fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.8f))
                    Text(Language.get("leaderboard_player", lang), fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(2.2f))
                    Text(Language.get("leaderboard_rating", lang), fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.2f), textAlign = TextAlign.End)
                    Text(Language.get("leaderboard_winrate", lang), fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
                }

                Spacer(modifier = Modifier.height(6.dp))

                Box(modifier = Modifier.height(260.dp)) {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(Language.worldLeaderboardList) { entry ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 5.dp, horizontal = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("#${entry.rank}", fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.8f))
                                Column(modifier = Modifier.weight(2.2f)) {
                                    Text(entry.name, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    Text(
                                        text = "${entry.countryCode} • ${entry.status}",
                                        fontSize = 9.sp,
                                        color = if (entry.status.contains("Online")) Color(0xFF43A047) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                    )
                                }
                                Text("${entry.rating}", fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.2f), textAlign = TextAlign.End, color = MaterialTheme.colorScheme.primary)
                                Text(entry.winRate, fontSize = 11.sp, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(Language.get("close", lang))
                }
            }
        }
    }
}
