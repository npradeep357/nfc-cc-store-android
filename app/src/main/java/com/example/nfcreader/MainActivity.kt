package com.example.nfcreader

import android.app.PendingIntent
import android.content.Intent
import android.content.IntentFilter
import android.nfc.NdefMessage
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.*
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.ui.semantics.text
import com.github.devnied.emvnfccard.exception.CommunicationException
import com.github.devnied.emvnfccard.parser.EmvTemplate
import com.github.devnied.emvnfccard.parser.IProvider
import java.io.IOException
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.text.format

class PcscProvider : IProvider {

    private lateinit var mTagCom: IsoDep

    @Throws(CommunicationException::class)
    override fun transceive(pCommand: ByteArray?): ByteArray? {
        var response: ByteArray? = null
        try {
            // send command to emv card
            if (mTagCom.isConnected) {
                response = mTagCom.transceive(pCommand)
            }
        } catch (e: IOException) {
            throw CommunicationException(e.message)
        }
        return response
    }

    override fun getAt(): ByteArray {
        var result: ByteArray?
        result = mTagCom.historicalBytes // for tags using NFC-B
        if (result == null) {
            result = mTagCom.hiLayerResponse // for tags using NFC-B
        }
        return result
    }

    fun setmTagCom(mTagCom: IsoDep) {
        this.mTagCom = mTagCom
    }
}

class MainActivity : AppCompatActivity(), NfcAdapter.ReaderCallback {
    private var nfcAdapter: NfcAdapter? = null
    private var pendingIntent: PendingIntent? = null
    private var intentFiltersArray: Array<IntentFilter>? = null
    private var techListsArray: Array<Array<String>>? = null
    private lateinit var contentText: TextView
    private lateinit var nfcStatusText: TextView
    private lateinit var cardNumberText: TextView
    private lateinit var cardNameText: TextView
    private lateinit var expiryText: TextView
    private lateinit var cvvText: TextView
    private lateinit var cardTypeText: TextView
    val formatter = DateTimeFormatter.ofPattern("MM/yy")


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main) // Inflate the XML layout

        contentText = findViewById(R.id.contentText)
        nfcStatusText = findViewById(R.id.nfcStatusText)
        cardNumberText = findViewById(R.id.cardNumberText)
        cardNameText = findViewById(R.id.cardNameText)
        expiryText = findViewById(R.id.expiryText)
        cvvText = findViewById(R.id.cvvText)
        cardTypeText = findViewById(R.id.cardTypeText)

        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        if (nfcAdapter == null) {
            Toast.makeText(this, "This device doesn't support NFC.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        if (!nfcAdapter!!.isEnabled) {
            Toast.makeText(this, "Please enable NFC to use this feature.", Toast.LENGTH_LONG).show()
            val enableNfcIntent = Intent(android.provider.Settings.ACTION_NFC_SETTINGS)
            startActivity(enableNfcIntent)
            return
        }

        val intent = Intent(this, javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_MUTABLE
        )

        val ndef = IntentFilter(NfcAdapter.ACTION_NDEF_DISCOVERED)
        try {
            ndef.addDataType("*/*")
        } catch (e: IntentFilter.MalformedMimeTypeException) {
            throw RuntimeException("fail", e)
        }
        intentFiltersArray = arrayOf(ndef)

        techListsArray = arrayOf(
            arrayOf(NfcA::class.java.name),
            arrayOf(NfcB::class.java.name),
            arrayOf(NfcF::class.java.name),
            arrayOf(NfcV::class.java.name),
            arrayOf(IsoDep::class.java.name),
            arrayOf(MifareClassic::class.java.name),
            arrayOf(MifareUltralight::class.java.name),
            arrayOf(Ndef::class.java.name),
            arrayOf(NdefFormatable::class.java.name)
        )
    }

    override fun onResume() {
        super.onResume()
        val options = Bundle()
        options.putInt(NfcAdapter.EXTRA_READER_PRESENCE_CHECK_DELAY, 250)

        nfcAdapter?.enableReaderMode(
            this,
            this,
            NfcAdapter.FLAG_READER_NFC_A or
                    NfcAdapter.FLAG_READER_NFC_B or
                    NfcAdapter.FLAG_READER_NFC_F or
                    NfcAdapter.FLAG_READER_NFC_V or
                    NfcAdapter.FLAG_READER_NFC_BARCODE,
            options
        )

    }

    override fun onPause() {
        super.onPause()
        nfcAdapter?.disableForegroundDispatch(this)
    }

    override fun onTagDiscovered(tag: Tag?) {
        val isoDep: IsoDep?
        try {
            isoDep = IsoDep.get(tag)
            if (isoDep != null) {
                (getSystemService(VIBRATOR_SERVICE) as Vibrator).vibrate(
                    VibrationEffect.createOneShot(
                        150,
                        10
                    )
                )
                isoDep.connect()
                val provider = PcscProvider()
                provider.setmTagCom(isoDep)
                val config = EmvTemplate.Config()
                    .setContactLess(true)
                    .setReadAllAids(true)
                    .setReadTransactions(true)
                    .setRemoveDefaultParsers(false)
                    .setReadAt(true)
                    .setReadCplc(true)
                val parser = EmvTemplate.Builder()
                    .setProvider(provider)
                    .setConfig(config)
                    .build()

                val card = parser.readEmvCard()
                cardNumberText.text = card.cardNumber
                cardNameText.text = card.holderLastname
                cardTypeText.text = card.type.name

                val expireDate = card.expireDate
                expiryText.text = expireDate.toInstant()
                    .atZone(ZoneId.systemDefault())
                    .toLocalDate()
                    .format(formatter)

                try {
                    isoDep.close()
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }
        } catch (e: IOException) {
            Toast.makeText(this, "error: ${e.message}", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }


}