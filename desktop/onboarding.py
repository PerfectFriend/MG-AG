"""
MadGammon Desktop — Onboarding & Security Vault Wizard
Full duplication of Android onboarding experience:
1. Language selection & Plausible Deniability intro
2. Master PIN & Duress PIN setup
3. BIP-39 HD 12-Word seed generation & offline backup
4. Cryptographic Identity derivation (Ed25519 + X25519 + Fingerprint)
5. 210,000 PBKDF2-HMAC-SHA256 vault container encryption
"""
import os
import sys
import time
from typing import Optional

from PyQt6.QtWidgets import (
    QDialog, QWidget, QVBoxLayout, QHBoxLayout, QLabel,
    QPushButton, QLineEdit, QStackedWidget, QFrame,
    QGridLayout, QCheckBox, QMessageBox, QProgressBar, QApplication
)
from PyQt6.QtGui import QFont, QColor, QIcon, QClipboard
from PyQt6.QtCore import Qt, QTimer, pyqtSignal

from crypto_vault import Bip39Vault, IdentityManager, VaultStorage
from logger import get_logger

log = get_logger("madgammon.onboarding")

C_BG = QColor(10, 14, 20)
C_PANEL = QColor(18, 24, 34)
C_CYAN = QColor(0, 229, 255)
C_GREEN = QColor(0, 255, 128)
C_AMBER = QColor(255, 185, 45)
C_RED = QColor(255, 51, 102)
C_TEXT_PRIMARY = QColor(240, 245, 255)
C_TEXT_MUTED = QColor(140, 155, 175)


class OnboardingWizardDialog(QDialog):
    onboarding_completed = pyqtSignal(dict)  # returns identity & config

    def __init__(self, parent=None):
        super().__init__(parent)
        self.setWindowTitle("🛡️ MadGammon — Первичная инициализация безопасности")
        self.resize(640, 560)
        self.setModal(True)

        self.lang = "RU"
        self.master_pin = ""
        self.duress_pin = ""
        self.mnemonic = Bip39Vault.generate_mnemonic(12)
        self.identity = IdentityManager.derive_identity(self.mnemonic)

        self.setStyleSheet(f"""
            QDialog {{ background: {C_BG.name()}; color: {C_TEXT_PRIMARY.name()}; }}
            QFrame {{ background: {C_PANEL.name()}; border-radius: 8px; }}
            QLabel {{ color: {C_TEXT_PRIMARY.name()}; }}
            QLineEdit {{
                background: #121822; border: 1.5px solid #2a3c54; border-radius: 6px;
                color: {C_CYAN.name()}; font-size: 18px; font-weight: bold;
                padding: 8px 12px; letter-spacing: 4px;
            }}
            QLineEdit:focus {{ border-color: {C_CYAN.name()}; }}
            QPushButton {{
                background: #1b2636; border: 1px solid #2a3c54;
                color: {C_CYAN.name()}; font-weight: bold; border-radius: 6px; padding: 10px 20px;
                font-size: 13px;
            }}
            QPushButton:hover {{ background: #263850; border-color: {C_CYAN.name()}; }}
            QCheckBox {{ color: {C_TEXT_PRIMARY.name()}; font-size: 12px; spacing: 8px; }}
            QProgressBar {{
                background: #121822; border: 1px solid #2a3c54; border-radius: 4px;
                text-align: center; color: white; font-weight: bold;
            }}
            QProgressBar::chunk {{ background: {C_CYAN.name()}; border-radius: 3px; }}
        """)

        self._build_ui()

    def _build_ui(self):
        root = QVBoxLayout(self)
        root.setContentsMargins(24, 20, 24, 20)
        root.setSpacing(16)

        # Header with step dots
        h_box = QHBoxLayout()
        self.lbl_step_indicator = QLabel("ШАГ 1 ИЗ 5: КОНФИДЕНЦИАЛЬНОСТЬ")
        self.lbl_step_indicator.setFont(QFont("Monospace", 10, QFont.Weight.Bold))
        self.lbl_step_indicator.setStyleSheet(f"color: {C_CYAN.name()};")
        h_box.addWidget(self.lbl_step_indicator)
        h_box.addStretch()

        self.lbl_shield = QLabel("🛡️")
        self.lbl_shield.setFont(QFont("Segoe UI Emoji", 18))
        h_box.addWidget(self.lbl_shield)
        root.addLayout(h_box)

        # Stacked Pages
        self.stack = QStackedWidget()
        self.stack.addWidget(self._create_step1_page())
        self.stack.addWidget(self._create_step2_page())
        self.stack.addWidget(self._create_step3_page())
        self.stack.addWidget(self._create_step4_page())
        self.stack.addWidget(self._create_step5_page())
        root.addWidget(self.stack, 1)

        # Navigation Bar
        nav_layout = QHBoxLayout()
        self.btn_back = QPushButton("◀ НАЗАД")
        self.btn_back.clicked.connect(self._go_back)
        self.btn_back.setVisible(False)
        nav_layout.addWidget(self.btn_back)

        nav_layout.addStretch()

        self.btn_next = QPushButton("ПРОДОЛЖИТЬ ▶")
        self.btn_next.setStyleSheet(f"background: #004433; border: 1.5px solid {C_GREEN.name()}; color: {C_GREEN.name()};")
        self.btn_next.clicked.connect(self._go_next)
        nav_layout.addWidget(self.btn_next)

        root.addLayout(nav_layout)

    # ─── STEP 1: WELCOME & LANGUAGE ───
    def _create_step1_page(self) -> QWidget:
        w = QWidget()
        layout = QVBoxLayout(w)
        layout.setSpacing(14)

        lbl_head = QLabel("ДОБРО ПОЖАЛОВАТЬ В MADGAMMON")
        lbl_head.setFont(QFont("Monospace", 15, QFont.Weight.Bold))
        lbl_head.setStyleSheet(f"color: {C_CYAN.name()};")
        layout.addWidget(lbl_head)

        lbl_desc = QLabel(
            "MadGammon объединяет классическую настольную игру в длинные нарды "
            "с суверенной криптографической цитаделью.\n\n"
            "• Правдоподобное отрицание (Plausible Deniability): интерфейс нард надёжно скрывает зашифрованный мессенджер.\n"
            "• Сквозное шифрование Double Ratchet с 16KB padding.\n"
            "• Децентрализованная маршрутизация через сеть SimpleX SMP и Tor Hidden Services."
        )
        lbl_desc.setWordWrap(True)
        lbl_desc.setFont(QFont("Sans", 10))
        lbl_desc.setStyleSheet(f"color: {C_TEXT_PRIMARY.name()}; line-height: 1.4;")
        layout.addWidget(lbl_desc)

        layout.addSpacing(10)
        lbl_lang = QLabel("ВЫБЕРИТЕ ЯЗЫК ИНТЕРФЕЙСА:")
        lbl_lang.setFont(QFont("Monospace", 9, QFont.Weight.Bold))
        lbl_lang.setStyleSheet(f"color: {C_AMBER.name()};")
        layout.addWidget(lbl_lang)

        lang_row = QHBoxLayout()
        self.btn_ru = QPushButton("🇷🇺 РУССКИЙ")
        self.btn_ru.setStyleSheet(f"border-color: {C_CYAN.name()}; color: {C_CYAN.name()}; font-weight: bold;")
        self.btn_en = QPushButton("🇬🇧 ENGLISH")

        def set_ru():
            self.lang = "RU"
            self.btn_ru.setStyleSheet(f"border-color: {C_CYAN.name()}; color: {C_CYAN.name()}; font-weight: bold;")
            self.btn_en.setStyleSheet("border-color: #2a3c54; color: #88a0bc;")

        def set_en():
            self.lang = "EN"
            self.btn_en.setStyleSheet(f"border-color: {C_CYAN.name()}; color: {C_CYAN.name()}; font-weight: bold;")
            self.btn_ru.setStyleSheet("border-color: #2a3c54; color: #88a0bc;")

        self.btn_ru.clicked.connect(set_ru)
        self.btn_en.clicked.connect(set_en)

        lang_row.addWidget(self.btn_ru)
        lang_row.addWidget(self.btn_en)
        lang_row.addStretch()
        layout.addLayout(lang_row)

        layout.addStretch()
        return w

    # ─── STEP 2: MASTER PIN & DURESS PIN ───
    def _create_step2_page(self) -> QWidget:
        w = QWidget()
        layout = QVBoxLayout(w)
        layout.setSpacing(12)

        lbl_head = QLabel("УСТАНОВКА PIN-КОДОВ БЕЗОПАСНОСТИ")
        lbl_head.setFont(QFont("Monospace", 14, QFont.Weight.Bold))
        lbl_head.setStyleSheet(f"color: {C_CYAN.name()};")
        layout.addWidget(lbl_head)

        lbl_desc = QLabel(
            "Мастер-PIN защищает ваш локальный криптоконтейнер и открывает секретный чат из нард.\n"
            "Тревожный PIN (Panic PIN) служит для защиты от принуждения — его ввод запустит ложный профиль."
        )
        lbl_desc.setWordWrap(True)
        lbl_desc.setFont(QFont("Sans", 9))
        layout.addWidget(lbl_desc)

        layout.addSpacing(6)
        layout.addWidget(QLabel("Основной Мастер-PIN (4-8 цифр):"))
        self.pin1_input = QLineEdit()
        self.pin1_input.setEchoMode(QLineEdit.EchoMode.Password)
        self.pin1_input.setMaxLength(8)
        self.pin1_input.setPlaceholderText("••••")
        layout.addWidget(self.pin1_input)

        layout.addWidget(QLabel("Повторите Мастер-PIN:"))
        self.pin2_input = QLineEdit()
        self.pin2_input.setEchoMode(QLineEdit.EchoMode.Password)
        self.pin2_input.setMaxLength(8)
        self.pin2_input.setPlaceholderText("••••")
        layout.addWidget(self.pin2_input)

        layout.addWidget(QLabel("Тревожный PIN (Panic / Duress PIN, опционально, например 9999):"))
        self.duress_input = QLineEdit()
        self.duress_input.setEchoMode(QLineEdit.EchoMode.Password)
        self.duress_input.setMaxLength(8)
        self.duress_input.setPlaceholderText("•••• (необязательно)")
        layout.addWidget(self.duress_input)

        self.lbl_pin_err = QLabel("")
        self.lbl_pin_err.setStyleSheet(f"color: {C_RED.name()}; font-weight: bold;")
        layout.addWidget(self.lbl_pin_err)

        layout.addStretch()
        return w

    # ─── STEP 3: BIP-39 SEED GENERATION ───
    def _create_step3_page(self) -> QWidget:
        w = QWidget()
        layout = QVBoxLayout(w)
        layout.setSpacing(12)

        lbl_head = QLabel("СУВЕРЕННАЯ СИД-ФРАЗА BIP-39 (12 СЛОВ)")
        lbl_head.setFont(QFont("Monospace", 14, QFont.Weight.Bold))
        lbl_head.setStyleSheet(f"color: {C_CYAN.name()};")
        layout.addWidget(lbl_head)

        lbl_desc = QLabel(
            "Из этой фразы детерминированно выводятся ваши мастер-ключи Ed25519 (подписи) "
            "и X25519 (Double Ratchet). Запишите эти слова на бумаге и храните в секрете!"
        )
        lbl_desc.setWordWrap(True)
        lbl_desc.setFont(QFont("Sans", 9))
        layout.addWidget(lbl_desc)

        # 12 cards grid
        grid_frame = QFrame()
        grid = QGridLayout(grid_frame)
        grid.setSpacing(8)
        grid.setContentsMargins(12, 12, 12, 12)

        words = self.mnemonic.split()
        for i, word in enumerate(words):
            card = QFrame()
            card.setStyleSheet("background: #141c26; border: 1px solid #28384e; border-radius: 6px;")
            c_layout = QHBoxLayout(card)
            c_layout.setContentsMargins(8, 6, 8, 6)
            lbl_num = QLabel(f"{i+1}.")
            lbl_num.setFont(QFont("Monospace", 8, QFont.Weight.Bold))
            lbl_num.setStyleSheet(f"color: {C_AMBER.name()};")
            lbl_w = QLabel(word)
            lbl_w.setFont(QFont("Monospace", 10, QFont.Weight.Bold))
            lbl_w.setStyleSheet(f"color: {C_TEXT_PRIMARY.name()};")
            c_layout.addWidget(lbl_num)
            c_layout.addWidget(lbl_w)
            c_layout.addStretch()
            grid.addWidget(card, i // 3, i % 3)

        layout.addWidget(grid_frame)

        # Actions
        btn_row = QHBoxLayout()
        btn_copy = QPushButton("📋 СКОПИРОВАТЬ СИД-ФРАЗУ")
        btn_copy.clicked.connect(self._copy_mnemonic)
        btn_row.addWidget(btn_copy)

        btn_regen = QPushButton("🔄 СГЕНЕРИРОВАТЬ ЗАНОВО")
        btn_regen.clicked.connect(self._regen_mnemonic)
        btn_row.addWidget(btn_regen)
        btn_row.addStretch()
        layout.addLayout(btn_row)

        self.cb_backup_confirmed = QCheckBox("Я надёжно сохранил сид-фразу и понимаю, что её нельзя восстановить извне")
        layout.addWidget(self.cb_backup_confirmed)

        layout.addStretch()
        return w

    # ─── STEP 4: IDENTITY DERIVATION & FINGERPRINT ───
    def _create_step4_page(self) -> QWidget:
        w = QWidget()
        layout = QVBoxLayout(w)
        layout.setSpacing(14)

        lbl_head = QLabel("КРИПТОГРАФИЧЕСКИЙ ПАСПОРТ УСТРОЙСТВА")
        lbl_head.setFont(QFont("Monospace", 14, QFont.Weight.Bold))
        lbl_head.setStyleSheet(f"color: {C_CYAN.name()};")
        layout.addWidget(lbl_head)

        lbl_desc = QLabel(
            "На основе сид-фразы создана суверенная криптографическая личность. "
            "Ключи полностью независимы от Google Play, Apple ID и серверов авторизации."
        )
        lbl_desc.setWordWrap(True)
        lbl_desc.setFont(QFont("Sans", 9))
        layout.addWidget(lbl_desc)

        info_frame = QFrame()
        if_layout = QVBoxLayout(info_frame)
        if_layout.setSpacing(10)
        if_layout.setContentsMargins(14, 14, 14, 14)

        self.lbl_fp_display = QLabel(f"Отпечаток личности (Fingerprint):\n{self.identity['fingerprint']}")
        self.lbl_fp_display.setFont(QFont("Monospace", 11, QFont.Weight.Bold))
        self.lbl_fp_display.setStyleSheet(f"color: {C_GREEN.name()};")
        if_layout.addWidget(self.lbl_fp_display)

        self.lbl_x25519_pub = QLabel(f"X25519 Double Ratchet ключ:\n{self.identity['x25519_pub_hex'][:32]}...")
        self.lbl_x25519_pub.setFont(QFont("Monospace", 9))
        self.lbl_x25519_pub.setStyleSheet(f"color: {C_TEXT_MUTED.name()};")
        if_layout.addWidget(self.lbl_x25519_pub)

        self.lbl_ed25519_pub = QLabel(f"Ed25519 ключ подписи:\n{self.identity['ed25519_pub_hex'][:32]}...")
        self.lbl_ed25519_pub.setFont(QFont("Monospace", 9))
        self.lbl_ed25519_pub.setStyleSheet(f"color: {C_TEXT_MUTED.name()};")
        if_layout.addWidget(self.lbl_ed25519_pub)

        layout.addWidget(info_frame)
        layout.addStretch()
        return w

    # ─── STEP 5: ENCRYPTION & COMPLETION ───
    def _create_step5_page(self) -> QWidget:
        w = QWidget()
        layout = QVBoxLayout(w)
        layout.setSpacing(16)

        lbl_head = QLabel("ШИФРОВАНИЕ И СОХРАНЕНИЕ КОНТЕЙНЕРА")
        lbl_head.setFont(QFont("Monospace", 14, QFont.Weight.Bold))
        lbl_head.setStyleSheet(f"color: {C_CYAN.name()};")
        layout.addWidget(lbl_head)

        lbl_desc = QLabel(
            "Генерация мастер-ключа AES-256-GCM с применением 210,000 итераций "
            "PBKDF2-HMAC-SHA256 для защиты от перебора на GPU/ASIC..."
        )
        lbl_desc.setWordWrap(True)
        lbl_desc.setFont(QFont("Sans", 9))
        layout.addWidget(lbl_desc)

        self.progress_bar = QProgressBar()
        self.progress_bar.setRange(0, 100)
        self.progress_bar.setValue(0)
        layout.addWidget(self.progress_bar)

        self.lbl_encrypt_status = QLabel("Ожидание запуска шифрования...")
        self.lbl_encrypt_status.setFont(QFont("Monospace", 9))
        layout.addWidget(self.lbl_encrypt_status)

        layout.addStretch()
        return w

    # ─── NAVIGATION LOGIC ───
    def _go_next(self):
        cur = self.stack.currentIndex()

        if cur == 0:
            self.stack.setCurrentIndex(1)
            self.btn_back.setVisible(True)
            self.lbl_step_indicator.setText("ШАГ 2 ИЗ 5: МАСТЕР-PIN И ПАНИК-PIN")

        elif cur == 1:
            p1 = self.pin1_input.text().strip()
            p2 = self.pin2_input.text().strip()
            if len(p1) < 4:
                self.lbl_pin_err.setText("Ошибка: PIN должен состоять минимум из 4 символов!")
                return
            if p1 != p2:
                self.lbl_pin_err.setText("Ошибка: введённые PIN-коды не совпадают!")
                return
            self.lbl_pin_err.setText("")
            self.master_pin = p1
            self.duress_pin = self.duress_input.text().strip() or "9999"

            self.stack.setCurrentIndex(2)
            self.lbl_step_indicator.setText("ШАГ 3 ИЗ 5: СИД-ФРАЗА BIP-39")

        elif cur == 2:
            if not self.cb_backup_confirmed.isChecked():
                QMessageBox.warning(
                    self, "Подтверждение",
                    "Пожалуйста, подтвердите флажком, что вы надёжно сохранили сид-фразу офлайн!"
                )
                return
            self.stack.setCurrentIndex(3)
            self.lbl_step_indicator.setText("ШАГ 4 ИЗ 5: КРИПТО-ПАСПОРТ")

        elif cur == 3:
            self.stack.setCurrentIndex(4)
            self.lbl_step_indicator.setText("ШАГ 5 ИЗ 5: ШИФРОВАНИЕ VAULT")
            self.btn_next.setEnabled(False)
            self.btn_back.setEnabled(False)
            QTimer.singleShot(100, self._perform_encryption)

        elif cur == 4:
            self.accept()

    def _go_back(self):
        cur = self.stack.currentIndex()
        if cur > 0:
            self.stack.setCurrentIndex(cur - 1)
            if cur - 1 == 0:
                self.btn_back.setVisible(False)
            steps = [
                "ШАГ 1 ИЗ 5: КОНФИДЕНЦИАЛЬНОСТЬ",
                "ШАГ 2 ИЗ 5: МАСТЕР-PIN И ПАНИК-PIN",
                "ШАГ 3 ИЗ 5: СИД-ФРАЗА BIP-39",
                "ШАГ 4 ИЗ 5: КРИПТО-ПАСПОРТ",
                "ШАГ 5 ИЗ 5: ШИФРОВАНИЕ VAULT"
            ]
            self.lbl_step_indicator.setText(steps[cur - 1])

    def _copy_mnemonic(self):
        QApplication.clipboard().setText(self.mnemonic)
        QMessageBox.information(
            self, "Скопировано",
            "Сид-фраза скопирована в буфер обмена!\n"
            "Не забудьте очистить буфер после сохранения в надежном менеджере паролей."
        )

    def _regen_mnemonic(self):
        self.mnemonic = Bip39Vault.generate_mnemonic(12)
        self.identity = IdentityManager.derive_identity(self.mnemonic)
        # Re-create steps 3 and 4
        self.stack.removeWidget(self.stack.widget(3))
        self.stack.removeWidget(self.stack.widget(2))
        self.stack.insertWidget(2, self._create_step3_page())
        self.stack.insertWidget(3, self._create_step4_page())
        self.stack.setCurrentIndex(2)

    def _perform_encryption(self):
        self.lbl_encrypt_status.setText("Вычисление 210,000 итераций PBKDF2...")
        self.progress_bar.setValue(30)
        QApplication.processEvents()

        vault_data = {
            "identity": self.identity,
            "master_pin": self.master_pin,
            "duress_pin": self.duress_pin,
            "language": self.lang,
            "topology": "DIRECT",
            "active_smp": "smp10.simplex.im",
            "created_at": int(time.time())
        }

        # Save to encrypted container
        success = VaultStorage.save_vault(self.master_pin, vault_data)
        self.progress_bar.setValue(100)

        if success:
            self.lbl_encrypt_status.setText("✅ Контейнер успешно зашифрован и сохранён!")
            self.lbl_encrypt_status.setStyleSheet(f"color: {C_GREEN.name()}; font-weight: bold;")
            self.btn_next.setText("ВОЙТИ В MADGAMMON ▶")
            self.btn_next.setEnabled(True)
            self.btn_next.setStyleSheet(f"background: #004433; border: 1.5px solid {C_GREEN.name()}; color: {C_GREEN.name()};")
            self.btn_next.clicked.disconnect()
            self.btn_next.clicked.connect(self._finish_onboarding)
        else:
            self.lbl_encrypt_status.setText("❌ Ошибка при записи контейнера!")
            self.lbl_encrypt_status.setStyleSheet(f"color: {C_RED.name()};")

    def _finish_onboarding(self):
        log.info("Onboarding wizard finished successfully.")
        self.onboarding_completed.emit(self.identity)
        self.accept()
