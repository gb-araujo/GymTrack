package app.gabrielaraujo.gymtrack.testing

import app.gabrielaraujo.gymtrack.sync.RemoteDoc
import app.gabrielaraujo.gymtrack.sync.RemoteStore
import java.io.IOException

/**
 * Firestore em memória, compartilhável entre vários "aparelhos": relógio de servidor próprio,
 * LWW na escrita (como a transação do [app.gabrielaraujo.gymtrack.sync.FirestoreRemoteStore]) e falhas controláveis.
 */
class FakeRemoteStore : RemoteStore {
    private val docs = LinkedHashMap<String, RemoteDoc>()
    private var serverClock = 1_000L

    /** Sem rede: toda chamada falha. */
    @Volatile var offline = false

    /** Coleções cujo envio e recebimento falham (falha parcial). */
    val failingCollections: MutableSet<String> = java.util.Collections.synchronizedSet(mutableSetOf())

    /** O próximo envio é gravado no servidor, mas a resposta se perde (o app não sabe que deu certo). */
    @Volatile var loseNextPushResponse = false

    /** Chamado depois de buscar as mudanças de uma coleção e antes de o app aplicá-las. */
    @Volatile var onChangesFetched: suspend (collection: String) -> Unit = {}

    @Volatile var pushedDocs = 0
        private set

    private fun key(uid: String, collection: String, id: String) = "$uid/$collection/$id"

    @Synchronized
    fun doc(uid: String, collection: String, id: String): RemoteDoc? = docs[key(uid, collection, id)]

    @Synchronized
    fun count(uid: String, collection: String): Int = docs.keys.count { it.startsWith("$uid/$collection/") }

    private fun checkAvailable(collection: String) {
        if (offline) throw IOException("sem conexão")
        if (collection in failingCollections) throw IOException("falha em $collection")
    }

    override suspend fun pushIfNewer(uid: String, collection: String, docs: List<RemoteDoc>): List<RemoteDoc> {
        checkAvailable(collection)
        val lost = mutableListOf<RemoteDoc>()
        synchronized(this) {
            for (doc in docs) {
                val k = key(uid, collection, doc.id)
                val current = this.docs[k]
                if (current != null && current.updatedAt > doc.updatedAt) {
                    lost += current
                } else {
                    this.docs[k] = doc.copy(serverUpdatedAt = ++serverClock)
                    pushedDocs++
                }
            }
        }
        if (loseNextPushResponse) {
            loseNextPushResponse = false
            throw IOException("resposta perdida")
        }
        return lost
    }

    override suspend fun changesSince(uid: String, collection: String, since: Long?): List<RemoteDoc> {
        checkAvailable(collection)
        val prefix = "$uid/$collection/"
        val result = synchronized(this) {
            docs.filter { (k, d) -> k.startsWith(prefix) && (since == null || (d.serverUpdatedAt ?: 0) > since) }.values.toList()
        }
        onChangesFetched(collection)
        return result
    }
}
