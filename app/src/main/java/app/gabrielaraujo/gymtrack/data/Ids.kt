package app.gabrielaraujo.gymtrack.data

import java.util.UUID

/** Id globalmente único (UUID v4): não colide entre aparelhos, ao contrário de ids por timestamp. */
fun newId(): String = UUID.randomUUID().toString()
