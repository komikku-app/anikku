package exh.metadata.metadata

import kotlinx.serialization.Serializable

@Serializable
class RankedSearchMetadata {
    var rank: Int? = null
}

typealias RaisedSearchMetadata = RankedSearchMetadata
