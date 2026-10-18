package com.example.eventmanagerapplication

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle

object EventValidation {
    fun validDate(value: String): Boolean = try {
        value.matches(Regex("\\d{2}/\\d{2}/\\d{4}")) &&
            LocalDate.parse(value, DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(ResolverStyle.STRICT)).year > 0
    } catch (_: java.time.DateTimeException) { false }

    fun validEmail(value: String): Boolean = value.isEmpty() ||
        value.matches(Regex("[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?(?:\\.[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?)+"))

    fun validPhone(value: String): Boolean = value.isEmpty() ||
        (value.matches(Regex("\\+?[0-9][0-9 ()-]*")) && value.count { it.isDigit() } in 7..15)
}
