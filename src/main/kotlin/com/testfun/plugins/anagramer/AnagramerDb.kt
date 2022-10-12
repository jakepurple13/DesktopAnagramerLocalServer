package com.testfun.plugins.anagramer

import kotlinx.serialization.Serializable
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction

object AnagramerDb {

    private const val dbPath = "src/main/resources/data.db"

    private val db by lazy {
        //Database.connect("jdbc:h2:mem:regular", driver = "org.h2.Driver")
        Database.connect("jdbc:sqlite:$dbPath", "org.sqlite.JDBC")
    }

    fun init() {
        transaction(db) {
            addLogger(StdOutSqlLogger)
            SchemaUtils.create(PlayerHighScore)
        }
    }

    suspend fun insertNewScore(name: String, score: Int): Scores {
        return newSuspendedTransaction(db = db) {
            val playerScores = PlayerHighScore.selectAll()
                .orderBy(PlayerHighScore.score to SortOrder.DESC)
                .map { it[PlayerHighScore.score] }

            if (score > (playerScores.lastOrNull() ?: 0) || playerScores.size < 10) {
                val id = PlayerHighScore.insertAndGetId {
                    it[this.name] = name
                    it[this.score] = score
                }
                println(id)
            }

            /*val all = PlayerHighScore.selectAll()
            .orderBy(PlayerHighScore.score to SortOrder.DESC)

            if (all.count() > 10) {
                all.forEach { }
            }*/

            Scores(
                PlayerHighScore.selectAll()
                    .orderBy(PlayerHighScore.score to SortOrder.DESC)
                    .limit(10)
                    .map { NewHighScore(it[PlayerHighScore.name], it[PlayerHighScore.score]) }
            )
        }
    }

    suspend fun getHighScores(): Scores {
        return newSuspendedTransaction(db = db) {
            Scores(
                PlayerHighScore.selectAll()
                    .orderBy(PlayerHighScore.score to SortOrder.DESC)
                    .limit(10)
                    .map { NewHighScore(it[PlayerHighScore.name], it[PlayerHighScore.score]) }
            )
        }
    }

}

object PlayerHighScore : IntIdTable() {
    val name = varchar("name", 50)
    val score = integer("score")
}

class PlayerHS(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<PlayerHS>(PlayerHighScore)

    var name by PlayerHighScore.name
    var score by PlayerHighScore.score
}

@Serializable
data class Scores(val list: List<NewHighScore>)