package app.gabrielaraujo.gymtrack.sync

/**
 * Um documento em `users/{uid}/{coleção}/{id}`.
 * @param updatedAt relógio do aparelho que fez a edição (base do last-write-wins).
 * @param serverUpdatedAt relógio do servidor no momento da gravação (cursor do pull); null em docs antigos.
 */
data class RemoteDoc(
    val id: String,
    val updatedAt: Long,
    val deleted: Boolean,
    val json: String,
    val serverUpdatedAt: Long? = null,
)

/** Armazenamento remoto da sincronização. Abstraído para o [SyncEngine] ser testável sem Firebase. */
interface RemoteStore {
    /**
     * Grava cada documento só se a versão remota não for mais nova (last-write-wins na escrita).
     * Reenviar o mesmo documento é idempotente. Sem rede, falha com exceção em vez de enfileirar.
     * @return as versões remotas mais novas que impediram a gravação, para o chamador aplicá-las localmente.
     */
    suspend fun pushIfNewer(uid: String, collection: String, docs: List<RemoteDoc>): List<RemoteDoc>

    /** Documentos gravados no servidor depois de [since] (null = todos), consultando sempre o servidor. */
    suspend fun changesSince(uid: String, collection: String, since: Long?): List<RemoteDoc>
}
