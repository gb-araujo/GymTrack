# Room, Compose, Firebase e kotlinx.serialization já trazem as regras de R8 necessárias.

# Credential Manager: o provedor do Play Services é carregado por reflexão.
-if class androidx.credentials.CredentialManager
-keep class androidx.credentials.playservices.** { *; }

# Modelos serializados para JSON (backup e Firestore): mantém nomes de campos estáveis.
-keep,includedescriptorclasses class app.gabrielaraujo.gymtrack.data.**$$serializer { *; }
-keepclassmembers class app.gabrielaraujo.gymtrack.data.** {
    *** Companion;
}
-keepclasseswithmembers class app.gabrielaraujo.gymtrack.data.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class app.gabrielaraujo.gymtrack.domain.**$$serializer { *; }
-keepclasseswithmembers class app.gabrielaraujo.gymtrack.domain.** {
    kotlinx.serialization.KSerializer serializer(...);
}
