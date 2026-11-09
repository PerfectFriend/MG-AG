package com.example.ui.screens.board

import androidx.compose.ui.graphics.Color

/**
 * Цветовые спецификации тем оформления игрового поля «Шеш-Беш».
 *
 * Поддерживаемые стили:
 * - **wood** (орех, ясень, латунь) — классическая персидская доска.
 * - **marine** (морская волна, бирюза) — средиземноморский бриз.
 * - **malachite** (малахит, изумруд, золото) — уральский самоцвет.
 * - **cosmic** (глубокий космос, неон) — киберпанк / неоновая ночь.
 * - **psychedelic** (оранжевый Ван Гог, фиолетовый ультра) — яркая авторская палитра.
 * - **bw** (минималистичный графит / уголь) — шахматная строгость.
 *
 * @param themeId текстовый идентификатор темы.
 */
class BoardThemeSpecs(themeId: String) {
    val id = when (themeId.lowercase()) {
        "warm", "vintage" -> "wood"
        "water", "sapphire", "sea" -> "marine"
        "emerald" -> "malachite"
        "space" -> "cosmic"
        "rainbow", "cyberpunk" -> "psychedelic"
        else -> themeId.lowercase()
    }

    val boardBg: Color = when (id) {
        "marine" -> Color(0xFF071B2C)
        "malachite" -> Color(0xFF062315)
        "cosmic" -> Color(0xFF090618)
        "psychedelic" -> Color(0xFF160A2C)
        "bw" -> Color(0xFF121212)
        else -> Color(0xFF2B1B10) // Теплый орех
    }

    val boardBorder: Color = when (id) {
        "marine" -> Color(0xFF020E18)
        "malachite" -> Color(0xFF02130A)
        "cosmic" -> Color(0xFF150F30)
        "psychedelic" -> Color(0xFF330948)
        "bw" -> Color(0xFF2E2E2E)
        else -> Color(0xFF1B0E06) // Мореный дуб
    }

    val hingeColor: Color = when (id) {
        "marine" -> Color(0xFF00E5FF)
        "malachite" -> Color(0xFFFFD700)
        "cosmic" -> Color(0xFF00FFCC)
        "psychedelic" -> Color(0xFFFF007F)
        "bw" -> Color(0xFF888888)
        else -> Color(0xFFD4AF37) // Полированная латунь
    }

    val darkTriangle: Color = when (id) {
        "marine" -> Color(0xFF0B3356)
        "malachite" -> Color(0xFF0D3B24)
        "cosmic" -> Color(0xFF161033)
        "psychedelic" -> Color(0xFF270E52)
        "bw" -> Color(0xFF222222)
        else -> Color(0xFF3E2717) // Темное дерево
    }

    val lightTriangle: Color = when (id) {
        "marine" -> Color(0xFF26A69A)
        "malachite" -> Color(0xFF388E3C)
        "cosmic" -> Color(0xFF6A1B9A)
        "psychedelic" -> Color(0xFFF57C00) // Оранжевый Ван Гог
        "bw" -> Color(0xFFBBBBBB)
        else -> Color(0xFFB88E5E) // Светлый ясень
    }

    val pointNumberColor: Color = when (id) {
        "marine" -> Color(0xFF80DEEA)
        "malachite" -> Color(0xFFA5D6A7)
        "cosmic" -> Color(0xFF00FFCC)
        "psychedelic" -> Color(0xFFFFD54F)
        "bw" -> Color(0xFFCCCCCC)
        else -> Color(0xFFD3A373)
    }

    val barBg: Color = when (id) {
        "marine" -> Color(0xFF020E18)
        "malachite" -> Color(0xFF02130A)
        "cosmic" -> Color(0xFF150F30)
        "psychedelic" -> Color(0xFF330948)
        "bw" -> Color(0xFF202020)
        else -> Color(0xFF1B0E06)
    }
}
