package com.testfun.plugins.anagramer

import io.github.serpro69.kfaker.Faker
import io.ktor.http.*
import io.ktor.serialization.kotlinx.*
import io.ktor.server.application.*
import io.ktor.server.application.ApplicationCallPipeline.ApplicationPhase.Plugins
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import io.ktor.server.websocket.*
import io.ktor.util.*
import io.ktor.websocket.*
import kotlinx.coroutines.channels.ClosedSendChannelException
import kotlinx.coroutines.channels.consumeEach
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.time.Duration
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

fun Application.configureAnagramerChat() {

    val chat = AnagramerChat()

    install(WebSockets) {
        pingPeriod = Duration.ofSeconds(15)
        timeout = Duration.ofSeconds(15)
        maxFrameSize = Long.MAX_VALUE
        masking = false
        contentConverter = KotlinxWebsocketSerializationConverter(
            Json {
                prettyPrint = true
                isLenient = true
                encodeDefaults = true
            }
        )
    }

    intercept(Plugins) {
        if (call.sessions.get<ChatSession>() == null) {
            call.sessions.set(ChatSession(generateNonce()))
        }
    }

    routing {
        webSocket("/anagramerChat") {
            val session = call.sessions.get<ChatSession>()

            if (session == null) {
                close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "No session"))
                return@webSocket
            }

            chat.memberJoin(session.id, this)

            try {
                incoming.consumeEach { frame ->
                    if (frame is Frame.Text) receivedMessage(
                        chat,
                        session.id,
                        frame.readText()
                    )
                }
            } finally {
                chat.memberLeft(session.id, this)
            }
        }

        post("/anagramerMessage") {
            val f = call.receive<PostMessage>()
            println("message: $f")
            chat.message(f.name, f.message)
            call.respond(HttpStatusCode.OK, f)
        }
    }
}

@Serializable
data class PostMessage(val name: String, val message: String)

@Serializable
data class ChatUser(
    var name: String,
)

data class ChatSession(val id: String)

suspend fun receivedMessage(chat: AnagramerChat, id: String, command: String) {
    println("$id: $command")
    try {
        chat.message(id, command)
        /*val cardPlay = command.fromJson<CardType>()
        when (cardPlay?.type) {
            Type.GET_HAND -> server.sendCards(id, cardPlay)
            Type.DRAW_CARDS -> server.drawCards(id, cardPlay)
            Type.SUBMIT_HAND -> server.submitHand(id, cardPlay)
            Type.CHAT -> server.message(id, command)
            Type.RENAME -> server.memberRenamed(id, cardPlay<String>()!!)
            else -> Unit
        }*/
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

class AnagramerChat {
    /**
     * Atomic counter used to get unique user-names based on the maximum users the server had.
     */
    private val usersCounter = AtomicInteger()

    /**
     * A concurrent map associating session IDs to user names.
     */
    private val memberNames = ConcurrentHashMap<String, ChatUser>()

    /**
     * Associates a session-id to a set of websockets.
     * Since a browser is able to open several tabs and windows with the same cookies and thus the same session.
     * There might be several opened sockets for the same client.
     */
    private val members = ConcurrentHashMap<String, MutableList<WebSocketSession>>()

    /**
     * A list of the latest messages sent to the server, so new members can have a bit context of what
     * other people was talking about before joining.
     */
    private val lastMessages = LinkedList<SendMessage>()

    private val json = Json {
        prettyPrint = true
        isLenient = true
        encodeDefaults = true
    }

    private val faker = Faker().apply { unique.configuration { enable(this@apply::funnyName) } }

    /**
     * Handles that a member identified with a session id and a socket joined.
     */
    suspend fun memberJoin(member: String, socket: WebSocketSession) {
        // Checks if this user is already registered in the server and gives him/her a temporal name if required.
        //val name = memberNames.computeIfAbsent(member) { ChatUser("user${usersCounter.incrementAndGet()}") }
        val name = memberNames.computeIfAbsent(member) {
            //usersCounter.incrementAndGet()
            //val n = faker.funnyName.name()//randomName()
            /*while (memberNames.values.any { it.name == n }) {
                n = ""//randomName()
            }*/
            ChatUser(faker.funnyName.name())
        }

        println("Member joined: $name")

        // Associates this socket to the member id.
        // Since iteration is likely to happen more frequently than adding new items,
        // we use a `CopyOnWriteArrayList`.
        // We could also control how many sockets we would allow per client here before appending it.
        // But since this is a sample we are not doing it.
        val list = members.computeIfAbsent(member) { CopyOnWriteArrayList<WebSocketSession>() }
        list.add(socket)

        socket.send(name.name)
        socket.send(json.encodeToString(memberNames.map { it.value.name }))

        // Only when joining the first socket for a member notifies the rest of the users.
        if (list.size == 1) {
            broadcastUserUpdate()
            //val sendMessage = SendMessage(ChatUser("Server"), "Connected as ${name.name}", MessageType.SERVER)
            //members[member]?.send(CardType(Type.UPDATE, sendMessage.toJson()).toFrameJson())
        }

        // Sends the user the latest messages from this server to let the member have a bit context.
        /*val messages = synchronized(lastMessages) { lastMessages.toList() }
        for (message in messages) {
            //socket.send(CardType(Type.UPDATE, message.toJson()).toFrameJson())
        }*/
    }

    /*suspend fun sendCards(sender: String, cardPlay: CardType) {
        members[sender]?.send(CardType(Type.GET_HAND, scores.getWinningHand(cardPlay.any.toJson().fromJson<List<Card>>()!!)).toFrameJson())
    }

    suspend fun drawCards(sender: String, cardPlay: CardType) {
        members[sender]?.send(CardType(Type.DRAW_CARDS, deck.draw(cardPlay.any.toJson().fromJson<Int>()!!)).toFrameJson())
    }

    suspend fun submitHand(sender: String, cardPlay: CardType) {
        memberNames[sender]?.hand = cardPlay.getAnyType<List<Card>>()!!
        memberNames[sender]?.submitted = true
        submittedHandCheck()
    }

    private suspend fun submittedHandCheck() {
        if (memberNames.all { it.value.submitted } && memberNames.isNotEmpty()) {
            //val otherHands = memberNames.elements().toList().groupBy { scores.getWinningHand(it.hand) }
            //val highest = otherHands.maxBy { it.key.defaultWinning }!!
            *//*val high = if (highest.value.size > 1)
                highest.key to listOf(highest.value.maxBy { it.hand.map(Card::value).maxBy { if (it == 1) 14 else it }!! }!!)
            else highest.toPair()*//*
            val high = findBestHand(memberNames.elements().toList())
            val allHands = memberNames.elements().toList().sortedByDescending { scores.getWinningHand(it.hand).defaultWinning }.joinToString("\n") {
                "${it.name} had a ${scores.getWinningHand(it.hand).stringName} with: ${it.hand.map { "${it.symbol}${it.suit.unicodeSymbol}" }}"
            }
            broadcast("${high.second.joinToString(", ") { it.name }} won with a ${high.first.stringName}\n$allHands")
            memberNames.forEach {
                it.value.submitted = false
                it.value.hand = emptyList()
            }
        }
    }*/

    /**
     * Handles a [member] identified by its session id renaming [to] a specific name.
     */
    suspend fun memberRenamed(member: String, to: String) {
        // Re-sets the member name.
        println("Member renamed: From: ${memberNames[member]?.name} To: $to")
        memberNames[member]?.name = to
        // Notifies everyone in the server about this change.
        //broadcastUserUpdate()
    }

    /**
     * Handles that a [member] with a specific [socket] left the server.
     */
    suspend fun memberLeft(member: String, socket: WebSocketSession) {
        // Removes the socket connection for this member
        val connections = members[member]
        connections?.remove(socket)

        // If no more sockets are connected for this member, let's remove it from the server
        // and notify the rest of the users about this event.
        if (connections != null && connections.isEmpty()) {
            val name = memberNames.remove(member)
            println("Member left: $name")
            //broadcast("server", "Member left: $name.", MessageType.SERVER)
            //broadcastUserUpdate()
        }
    }

    /**
     * Handles the 'who' command by sending the member a list of all members names in the server.
     */
    suspend fun who(sender: String) {
        val text = memberNames.values.joinToString(prefix = "[server::who] ") { it.name }
        val sendMessage = SendMessage(ChatUser("Server"), text, MessageType.SERVER)
        members[sender]?.send(Frame.Text(sendMessage.toJson(json)))
    }

    /**
     * Handles a [message] sent from a [sender] by notifying the rest of the users.
     */
    suspend fun message(sender: String, message: String) {
        // Pre-format the message to be sent, to prevent doing it for all the users or connected sockets.
        val name = memberNames[sender]?.name ?: sender
        //val formatted = "$message"
        // Sends this pre-formatted message to all the members in the server.
        broadcast(sender, message, MessageType.MESSAGE)
    }

    private suspend fun somethingWentWrong(sender: String, message: String = "Something went wrong") {
        val sendMessage = SendMessage(ChatUser("Server"), message, MessageType.SERVER)
        members[sender]?.send(Frame.Text(sendMessage.toJson(json)))
    }

    enum class MessageType {
        MESSAGE, SERVER, INFO, TYPING_INDICATOR
    }

    @Serializable
    data class SendMessage(
        val user: ChatUser,
        val message: String,
        val type: MessageType?,
        val time: String = SimpleDateFormat("MM/dd hh:mm a").format(System.currentTimeMillis())
    ) {
        fun toJson(json: Json) = json.encodeToString(this)
    }

    suspend fun sendServerMessage(msg: String) {
        broadcast(SendMessage(ChatUser("Server"), msg, MessageType.SERVER).toJson(json))
    }

    /**
     * Sends a [message] to all the members in the server, including all the connections per member.
     */
    private suspend fun broadcast(message: String) {
        members.values.forEach { socket ->
            socket.send(Frame.Text(message))
        }
    }

    private suspend fun broadcastUserUpdate() {
        /*members.values.forEach { sockets ->
            sockets.send(Frame.Text(SendMessage(
                ChatUser("Server"),
                "${SimpleDateFormat("MM/dd hh:mm a").format(System.currentTimeMillis())} ",
                MessageType.INFO,
                memberNames.values.joinToString("\n") { it.name }
            ).toJson()))
        }*/
        val message = SendMessage(
            ChatUser("Server"),
            "Current User: ${memberNames.values.joinToString(",") { it.name }}",
            MessageType.INFO,
            //
        )
        members.values.forEach { sockets ->
            sockets.send(Frame.Text(json.encodeToString(message)))
        }
    }

    /**
     * Sends a [message] to all the members in the server, including all the connections per member.
     */
    private suspend fun broadcast(message: String, recipient: String) {
        members[recipient]?.send(Frame.Text(message))
    }

    /**
     * Sends a [message] coming from a [sender] to all the members in the server, including all the connections per member.
     */
    private suspend fun broadcast(
        sender: String,
        message: String,
        type: MessageType = MessageType.MESSAGE,
    ) {
        //val name = memberNames[sender]?.name ?: sender
        //val text = "${SimpleDateFormat("MM/dd hh:mm a").format(System.currentTimeMillis())} $message"
        val sendMessage = SendMessage(
            memberNames.values.find { it.name == sender } ?: ChatUser("Server"),
            message,
            type
        )
        broadcast(sendMessage.toJson(json))
        //prettyLog(sendMessage.toJson())
        if (type != MessageType.TYPING_INDICATOR) {
            synchronized(lastMessages) {
                lastMessages.add(sendMessage)
                if (lastMessages.size > 100) {
                    lastMessages.removeFirst()
                }
            }
        }
    }

    /**
     * Sends a [message] to a list of [this] [WebSocketSession].
     */
    private suspend fun List<WebSocketSession>.send(frame: Frame) {
        forEach {
            try {
                it.send(frame.copy())
            } catch (t: Throwable) {
                try {
                    it.close(CloseReason(CloseReason.Codes.PROTOCOL_ERROR, ""))
                } catch (ignore: ClosedSendChannelException) {
                    // at some point it will get closed
                }
            }
        }
    }
}