package io.github.openquesttuner.core

import java.text.Normalizer
import java.util.Locale

private val COMBINING_MARKS = Regex("\\p{Mn}+")

/**
 * Clé de tri et de recherche d'un nom de jeu : sans accents, en minuscules et sans espaces de
 * bord, pour que « Élite » se range avec les E et soit trouvé en tapant « elite » (FR-009).
 */
fun searchKey(s: String): String =
    Normalizer.normalize(s, Normalizer.Form.NFD)
        .replace(COMBINING_MARKS, "")
        .lowercase(Locale.ROOT)
        .trim()

/** Filtre de la liste des jeux (FR-009) : une requête vide garde tout. */
fun matchesQuery(label: String, query: String): Boolean = searchKey(label).contains(searchKey(query))
