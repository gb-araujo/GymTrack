package app.gabrielaraujo.gymtrack.sync

import com.google.firebase.Timestamp
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import java.util.Date

/**
 * [RemoteStore] no Firestore. Cada doc = { updatedAt, serverUpdatedAt, deleted, json }.
 *
 * O envio usa transações, não `WriteBatch`: a transação lê a versão remota antes de gravar (o LWW vale
 * também na escrita) e falha sem rede. Um lote, ao contrário, ficaria na fila offline do SDK e poderia
 * chegar ao servidor horas depois, sobrescrevendo uma versão mais nova feita em outro aparelho.
 */
class FirestoreRemoteStore : RemoteStore {
    private val db: FirebaseFirestore by lazy { Firebase.firestore }

    private fun collection(uid: String, name: String): CollectionReference =
        db.collection("users").document(uid).collection(name)

    override suspend fun pushIfNewer(uid: String, collection: String, docs: List<RemoteDoc>): List<RemoteDoc> {
        val col = collection(uid, collection)
        val newerRemote = mutableListOf<RemoteDoc>()
        for (chunk in docs.chunked(TRANSACTION_LIMIT)) {
            newerRemote += timeout {
                db.runTransaction { tx ->
                    // Todas as leituras antes das escritas (exigência das transações do Firestore).
                    val current = chunk.map { tx.get(col.document(it.id)) }
                    val lost = mutableListOf<RemoteDoc>()
                    chunk.forEachIndexed { i, doc ->
                        val remote = current[i].toRemoteDoc()
                        if (remote != null && remote.updatedAt > doc.updatedAt) {
                            lost += remote
                        } else {
                            tx.set(
                                col.document(doc.id),
                                mapOf(
                                    "updatedAt" to doc.updatedAt,
                                    "serverUpdatedAt" to FieldValue.serverTimestamp(),
                                    "deleted" to doc.deleted,
                                    "json" to doc.json,
                                ),
                            )
                        }
                    }
                    lost
                }.await()
            }
        }
        return newerRemote
    }

    override suspend fun changesSince(uid: String, collection: String, since: Long?): List<RemoteDoc> {
        val col = collection(uid, collection)
        val query: Query = if (since == null) col else col.whereGreaterThan("serverUpdatedAt", Timestamp(Date(since)))
        // Source.SERVER: sem rede, falha em vez de devolver o cache como se tivesse sincronizado.
        val snap = timeout { query.get(Source.SERVER).await() }
        return snap.documents.mapNotNull { it.toRemoteDoc() }
    }

    private fun DocumentSnapshot.toRemoteDoc(): RemoteDoc? {
        if (!exists()) return null
        val json = getString("json") ?: return null
        return RemoteDoc(
            id = id,
            updatedAt = getLong("updatedAt") ?: 0L,
            deleted = getBoolean("deleted") ?: false,
            json = json,
            serverUpdatedAt = getTimestamp("serverUpdatedAt")?.toDate()?.time,
        )
    }

    /** Rede ruim vira erro comum (não cancelamento), para o status não ficar preso em "Sincronizando…". */
    private suspend fun <T> timeout(block: suspend () -> T): T =
        withTimeoutOrNull(TIMEOUT_MS) { block() } ?: throw IOException("Tempo esgotado ao sincronizar.")

    private companion object {
        /** Poucas centenas por transação: abaixo do limite de 500 escritas e com pouca chance de contenção. */
        const val TRANSACTION_LIMIT = 200
        const val TIMEOUT_MS = 15_000L
    }
}
