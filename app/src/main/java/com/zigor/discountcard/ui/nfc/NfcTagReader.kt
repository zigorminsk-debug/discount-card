package com.zigor.discountcard.ui.nfc

import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.nfc.tech.NfcA
import android.nfc.tech.NfcB
import android.nfc.tech.NfcF
import android.nfc.tech.NfcV

data class NfcTagData(
    val uid: String,
    val techs: List<String>,
    val payload: String? = null,
    val uri: String? = null,
    val extra: String? = null,
) {
    /** Что можно использовать как «номер карты». */
    val codeCandidate: String? get() = uri ?: payload
}

/** Чтение NFC-метки: UID, список технологий и содержимое NDEF. */
object NfcTagReader {

    private val URI_PREFIXES = arrayOf(
        "", "http://www.", "https://www.", "http://", "https://", "tel:", "mailto:",
        "ftp://anonymous:anonymous@", "ftp://ftp.", "ftps://", "sftp://", "smb://",
        "nfs://", "ftp://", "dav://", "news:", "telnet://", "imap:", "rtsp://", "urn:",
        "pop:", "sip:", "sips:", "tftp:", "btspp://", "btl2cap://", "btgoep://",
        "tcpobex://", "irdaobex://", "file://", "urn:epc:id:", "urn:epc:tag:",
        "urn:epc:pat:", "urn:epc:raw:", "urn:epc:", "urn:nfc:",
    )

    fun read(tag: Tag): NfcTagData {
        val uid = tag.id?.joinToString(":") { "%02X".format(it) }.orEmpty()
        val techs = tag.techList.orEmpty().map { it.substringAfterLast('.') }

        var text: String? = null
        var uri: String? = null
        runCatching {
            Ndef.get(tag)?.let { ndef ->
                val message: NdefMessage? = ndef.cachedNdefMessage ?: runCatching {
                    ndef.connect()
                    ndef.ndefMessage
                }.getOrNull().also { runCatching { ndef.close() } }
                message?.records?.forEach { record ->
                    val value = parseRecord(record) ?: return@forEach
                    if (value.startsWith("http", ignoreCase = true) && uri == null) {
                        uri = value
                    } else if (text == null) {
                        text = value
                    }
                }
            }
        }

        val extra = buildList {
            runCatching {
                NfcA.get(tag)?.let { add("ATQA " + it.atqa.joinToString("") { b -> "%02X".format(b) } + ", SAK %02X".format(it.sak)) }
            }
            runCatching { NfcB.get(tag)?.let { add("NfcB") } }
            runCatching { NfcF.get(tag)?.let { add("FeliCa") } }
            runCatching { NfcV.get(tag)?.let { add("ISO 15693") } }
            runCatching { Ndef.get(tag)?.let { add(it.type + ", " + it.maxSize + " Б") } }
        }.joinToString(" · ").takeIf { it.isNotBlank() }

        return NfcTagData(uid = uid, techs = techs, payload = text, uri = uri, extra = extra)
    }

    private fun parseRecord(record: NdefRecord): String? = runCatching {
        when {
            record.tnf == NdefRecord.TNF_WELL_KNOWN && record.type.contentEquals(NdefRecord.RTD_TEXT) -> {
                val payload = record.payload
                if (payload.isEmpty()) return null
                val status = payload[0].toInt()
                val langLength = status and 0x3F
                val charset = if (status and 0x80 == 0) Charsets.UTF_8 else Charsets.UTF_16
                String(payload, 1 + langLength, payload.size - 1 - langLength, charset)
            }
            record.tnf == NdefRecord.TNF_WELL_KNOWN && record.type.contentEquals(NdefRecord.RTD_URI) -> {
                val payload = record.payload
                if (payload.isEmpty()) return null
                val prefix = URI_PREFIXES.getOrElse(payload[0].toInt()) { "" }
                prefix + String(payload, 1, payload.size - 1, Charsets.UTF_8)
            }
            else -> String(record.payload, Charsets.UTF_8)
        }.trim().takeIf { it.isNotBlank() && it.none { ch -> ch.code in 0..8 } }
    }.getOrNull()
}
