package cat.dev.messages

class MessageThread {
    val messages: ArrayList<String> = arrayListOf()
    val recipients: HashSet<String> = hashSetOf()
    var date = 0L

    override fun toString(): String = "{\"date\": $date, \"messages\": $messages, \"recipients\": $recipients}"
}
