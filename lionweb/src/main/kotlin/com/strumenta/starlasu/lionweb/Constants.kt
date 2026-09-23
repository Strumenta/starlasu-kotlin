package com.strumenta.starlasu.lionweb

import io.lionweb.LionWebVersion

val LIONWEB_VERSION_USED_BY_STARLASU = LionWebVersion.v2023_1

/**
 * Kolasu 1.5 name of [LIONWEB_VERSION_USED_BY_STARLASU]. Kept only to ease the migration of 1.5 language modules.
 */
@Deprecated("Renamed in Starlasu 1.7", ReplaceWith("LIONWEB_VERSION_USED_BY_STARLASU"))
val LIONWEB_VERSION_USED_BY_KOLASU = LIONWEB_VERSION_USED_BY_STARLASU
