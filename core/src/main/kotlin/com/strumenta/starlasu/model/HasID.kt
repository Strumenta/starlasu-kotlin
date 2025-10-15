package com.strumenta.starlasu.model

/**
 * Anything with an ID. Currently used by Node. In the future other things which may need to get an identity
 * (e.g., issues, parsing results) may get an ID.
 */
interface HasID {
    @Internal
    var id: String?
}
