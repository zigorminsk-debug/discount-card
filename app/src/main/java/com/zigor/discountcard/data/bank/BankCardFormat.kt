package com.zigor.discountcard.data.bank

import java.util.Calendar

/** Платёжные системы, которые встречаются на картах белорусских банков. */
object PaymentSystem {
    const val UNKNOWN = ""
    const val VISA = "VISA"
    const val MASTERCARD = "MASTERCARD"
    const val MAESTRO = "MAESTRO"
    const val MIR = "MIR"
    const val BELCARD = "BELCARD"
    const val AMEX = "AMEX"

    /**
     * Определяет систему по первым цифрам номера (BIN) — правила открытые
     * и одинаковые во всём мире, никакого обращения к сети не нужно.
     */
    fun detect(number: String): String {
        val digits = CardNumber.digits(number)
        if (digits.length < 2) return UNKNOWN
        val two = digits.take(2).toInt()
        val four = if (digits.length >= 4) digits.take(4).toInt() else 0

        return when {
            // БЕЛКАРТ — национальная система Беларуси, диапазон 9112xx.
            digits.startsWith("9112") -> BELCARD
            four in 2200..2204 -> MIR
            digits.startsWith("4") -> VISA
            two in 51..55 || four in 2221..2720 -> MASTERCARD
            two == 34 || two == 37 -> AMEX
            four == 5018 || four == 5020 || four == 5038 || four == 5893 -> MAESTRO
            four == 6304 || four == 6759 || four == 6761 || four == 6762 || four == 6763 -> MAESTRO
            two == 50 || two in 56..58 -> MAESTRO
            else -> UNKNOWN
        }
    }

    /** Название системы для показа на карте. */
    fun title(system: String): String = when (system) {
        VISA -> "VISA"
        MASTERCARD -> "Mastercard"
        MAESTRO -> "Maestro"
        MIR -> "МИР"
        BELCARD -> "БЕЛКАРТ"
        AMEX -> "American Express"
        else -> ""
    }
}

/** Разбор, проверка и форматирование номера карты. */
object CardNumber {

    private const val MIN_LENGTH = 12
    private const val MAX_LENGTH = 19

    /** Оставляет в строке только цифры и обрезает по максимальной длине PAN. */
    fun digits(raw: String): String = raw.filter { it.isDigit() }.take(MAX_LENGTH)

    /**
     * Проверка по алгоритму Луна: последняя цифра номера — контрольная,
     * поэтому опечатку видно сразу, ещё до попытки оплаты.
     */
    fun luhnValid(number: String): Boolean {
        val digits = digits(number)
        if (digits.length < MIN_LENGTH) return false
        var sum = 0
        var double = false
        for (index in digits.lastIndex downTo 0) {
            var value = digits[index] - '0'
            if (double) {
                value *= 2
                if (value > 9) value -= 9
            }
            sum += value
            double = !double
        }
        return sum % 10 == 0
    }

    /** Полная проверка номера: длина под платёжную систему плюс контрольная цифра. */
    fun valid(number: String): Boolean {
        val digits = digits(number)
        val lengthOk = when (PaymentSystem.detect(digits)) {
            PaymentSystem.AMEX -> digits.length == 15
            PaymentSystem.MAESTRO -> digits.length in 12..19
            PaymentSystem.VISA -> digits.length == 13 || digits.length == 16 || digits.length == 19
            PaymentSystem.UNKNOWN -> digits.length in MIN_LENGTH..MAX_LENGTH
            else -> digits.length == 16
        }
        return lengthOk && luhnValid(digits)
    }

    /** Группирует номер: 4-4-4-4, а для American Express — 4-6-5. */
    fun format(number: String): String {
        val digits = digits(number)
        val groups = if (PaymentSystem.detect(digits) == PaymentSystem.AMEX) {
            listOf(4, 6, 5)
        } else {
            List(5) { 4 }
        }
        val builder = StringBuilder()
        var index = 0
        for (size in groups) {
            if (index >= digits.length) break
            if (builder.isNotEmpty()) builder.append(' ')
            builder.append(digits.substring(index, minOf(index + size, digits.length)))
            index += size
        }
        if (index < digits.length) builder.append(' ').append(digits.substring(index))
        return builder.toString()
    }

    fun last4(number: String): String = digits(number).takeLast(4)

    /** Маска для списка: видны только последние четыре цифры. */
    fun mask(last4: String): String = "•••• •••• •••• " + last4.padStart(4, '•')
}

/** Срок действия карты в формате ММ/ГГ. */
object CardExpiry {

    /** Приводит ввод к виду `MM/YY` прямо во время набора. */
    fun format(raw: String): String {
        val digits = raw.filter { it.isDigit() }.take(4)
        if (digits.isEmpty()) return ""
        var month = digits.take(2)
        // «5» превращаем в «05»: месяца 50 не бывает, человек имел в виду май.
        if (month.length == 1 && month[0] > '1') month = "0$month"
        return if (digits.length <= 2 && month.length < 2) month else {
            val rest = digits.drop(if (digits.length == 1 && month.length == 2) 1 else 2)
            if (rest.isEmpty()) month else "$month/$rest"
        }
    }

    /** Месяц в допустимых пределах и дата полностью заполнена. */
    fun wellFormed(value: String): Boolean {
        val digits = value.filter { it.isDigit() }
        if (digits.length != 4) return false
        val month = digits.take(2).toInt()
        return month in 1..12
    }

    /** Срок истёк? Карта считается годной до конца указанного месяца. */
    fun expired(value: String, now: Calendar = Calendar.getInstance()): Boolean {
        if (!wellFormed(value)) return false
        val digits = value.filter { it.isDigit() }
        val month = digits.take(2).toInt()
        val year = 2000 + digits.drop(2).toInt()
        val currentYear = now.get(Calendar.YEAR)
        val currentMonth = now.get(Calendar.MONTH) + 1
        return year < currentYear || (year == currentYear && month < currentMonth)
    }
}

/** Цвета для карточек: тёмные, чтобы белые цифры читались. */
object BankPalette {
    val DEFAULT: Int = 0xFF1F3A5F.toInt()

    val COLORS: List<Int> = listOf(
        0xFF1F3A5F.toInt(), // тёмно-синий
        0xFF0F766E.toInt(), // изумрудный
        0xFF7C2D12.toInt(), // кирпичный
        0xFF4C1D95.toInt(), // фиолетовый
        0xFF155E75.toInt(), // морской
        0xFF374151.toInt(), // графитовый
        0xFFB45309.toInt(), // янтарный
        0xFF831843.toInt(), // бордовый
    )
}

/** Подсказки с банками — просто список для автодополнения, можно ввести свой. */
object BankSuggestions {
    val NAMES: List<String> = listOf(
        "Беларусбанк",
        "Белагропромбанк",
        "Белинвестбанк",
        "Приорбанк",
        "Альфа-Банк",
        "МТБанк",
        "Белгазпромбанк",
        "Сбер Банк",
        "Банк Дабрабыт",
        "Паритетбанк",
        "БНБ-Банк",
        "Технобанк",
        "ВТБ (Беларусь)",
    )
}
