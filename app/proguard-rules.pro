# Règles R8 de la variante release.

# libadb-android et spake2-android : méthodes natives (JNI) et classes chargées par nom. Ni l'une
# ni l'autre ne fournit de règles R8.
-keep class io.github.muntashirakon.** { *; }

# Conscrypt fournit ses propres règles dans son AAR. Bouncy Castle n'est appelé que par des
# références directes (AdbIdentityStore, sans fournisseur "BC") : R8 peut l'élaguer.

# Adaptateurs de Conscrypt pour Android 4.4 et antérieur, jamais chargés avec minSdk 29.
-dontwarn com.android.org.conscrypt.SSLParametersImpl
-dontwarn org.apache.harmony.xnet.provider.jsse.SSLParametersImpl
