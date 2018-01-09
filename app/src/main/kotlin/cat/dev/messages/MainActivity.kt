package cat.dev.messages

import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.widget.TextView

import java.io.IOException
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket

class MainActivity : AppCompatActivity() {

    companion object {
        const val MESSAGE_INBOX = 1
        const val TAG = "MainActivity"
    }

    private val mHandler = object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(message: Message?) {
            when (message?.what) {
                0 -> findViewById<TextView>(R.id.ip_address).text = message.obj as String
                1 -> findViewById<TextView>(R.id.status).text = message.obj as String
                else -> Log.d(TAG, "Something went wrong...")
            }
        }
    }

    private var mClient: Socket? = null
    private var mServer: ServerSocket? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val server = object : Thread() {
            private fun obtainOsMessage(text: String): Message {
                val message = mHandler.obtainMessage()

                message.obj = text
                message.what = 1

                return message
            }

            override fun run() {
                mHandler.sendMessage(obtainOsMessage("Loading messages..."))

                val messages = hashMapOf<Int, MessageThread>()
                val sortOrder = "date ASC"

                var uri = Uri.parse("content://sms/conversations")
                var projection = arrayOf("thread_id")
                var cursor = contentResolver.query(uri, projection, null, null, null, null)

                while (cursor.moveToNext()) {
                    messages[cursor.getInt(0)] = MessageThread()
                }

                uri = Uri.parse("content://sms")
                projection = arrayOf("thread_id", "address", "body", "date", "type")
                cursor = contentResolver.query(uri, projection, null, null, sortOrder, null)

                while (cursor.moveToNext()) {
                    val id = cursor.getInt(0)
                    val body = parseBody(cursor.getString(2))
                    val date = cursor.getLong(3)
                    val type = cursor.getInt(4)

                    val address = if (type == MESSAGE_INBOX) {
                        "\"${cursor.getString(1)}\""
                    } else {
                        "null"
                    }

                    val message = "{\"address\": $address, \"body\": \"$body\", \"date\": $date}"

                    if (messages[id]?.address == null && type == MESSAGE_INBOX) {
                        messages[id]?.address = address
                    }

                    messages[id]?.date = date
                    messages[id]?.messages?.add(message)
                }

                cursor.close()

                mServer = ServerSocket(1337)

                mHandler.sendMessage(obtainOsMessage("Listening on port 1337"))

                mClient = mServer?.accept()

                // val receive = BufferedReader(InputStreamReader(client.getInputStream()))
                val send = PrintWriter(mClient?.getOutputStream(), true)

                // Log.d(TAG, receive.readLine())
                mHandler.sendMessage(obtainOsMessage("Sending information"))
                send.println(messages.json())
                mHandler.sendMessage(obtainOsMessage("Transmission is done"))
            }
        }

        val updateIp = object : Thread() {
            override fun run() {
                val message = Message()
                var connection: Socket? = null

                message.obj = "127.0.0.1"
                message.what = 0

                try {
                    connection = Socket("google.com", 80)
                    message.obj = connection.localAddress.hostAddress
                } catch (exception: IOException) {
                    Log.e(TAG, exception.message)
                } finally {
                    mHandler.sendMessage(message)
                    connection?.close()
                }
            }
        }

        server.start()
        updateIp.start()
    }

    override fun onDestroy() {
        mClient?.close()
        mServer?.close()

        super.onDestroy()
    }
}

private fun <K, V> HashMap<K, V>.json(): String {
    val stringBuilder = StringBuilder()
    val count = keys.count() - 1

    stringBuilder.append("{")

    keys.forEachIndexed { index, key ->
        if (index < count) {
            stringBuilder.append("\"$key\": ${this[key]}, ")
        } else {
            stringBuilder.append("\"$key\": ${this[key]}}")
        }
    }

    return stringBuilder.toString()
}

private fun parseBody(body: String): String {
    val stringBuilder = StringBuilder()

    for (character in body) {
        when (character) {
            '"' -> stringBuilder.append("\\\"")
            '\\' -> stringBuilder.append("\\\\")
            '\n' -> stringBuilder.append("\\n")
            else -> stringBuilder.append(character)
        }
    }

    return stringBuilder.toString()
}
