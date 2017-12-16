package cat.dev.messages

import android.net.Uri
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.TextView

import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket

class MainActivity : AppCompatActivity() {

    companion object {
        const val MESSAGE_INBOX = 1
        const val TAG = "MainActivity"
    }

    private var mClient: Socket? = null
    private var mServer: ServerSocket? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val statusTextView = findViewById<TextView>(R.id.status)

        val server = object : Thread() {
            override fun run() {
                val messages = hashMapOf<Int, MessageThread>()
                val sortOrder = "date ASC"

                var uri = Uri.parse("content://sms/conversations")
                var projection = arrayOf("thread_id")
                var cursor = contentResolver.query(uri, projection, null, null, null, null)

                while (cursor.moveToNext()) {
                    messages.put(cursor.getInt(0), MessageThread())
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

                    messages[id]?.date = date
                    messages[id]?.messages?.add(message)

                    if (type == MESSAGE_INBOX) {
                        messages[id]?.recipients?.add(address)
                    }
                }

                cursor.close()

                mServer = ServerSocket(1337)

                Log.d(TAG, "Listening on port 1337")
                statusTextView.post { statusTextView.text = "Listening on port 1337" }

                mClient = mServer?.accept()

                // val receive = BufferedReader(InputStreamReader(client.getInputStream()))
                val send = PrintWriter(mClient?.getOutputStream(), true)

                // Log.d(TAG, receive.readLine())
                Log.d(TAG, "Sending information")
                statusTextView.post { statusTextView.text = "Sending information" }

                send.println(messages.json())

                Log.d(TAG, "Transmission is done")
                statusTextView.post { statusTextView.text = "Transmission is done" }
            }
        }

        server.start()

        statusTextView.text = "Loading messages..."
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
