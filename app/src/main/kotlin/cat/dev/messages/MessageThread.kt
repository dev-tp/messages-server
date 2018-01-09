package cat.dev.messages

class MessageThread {
    val messages: ArrayList<String> = arrayListOf()
    var address: String? = null
    var date = 0L

    override fun toString(): String = "{\"address\": $address, \"date\": $date, \"messages\": $messages}"
}
