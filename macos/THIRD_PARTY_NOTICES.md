# Third-party notices

## TorrServer

Anikku distribution builds bundle the architecture-specific TorrServer
`MatriX.141.1` executable from https://github.com/YouROK/TorrServer.

TorrServer is copyright its contributors and is distributed under the GNU
General Public License v3.0. Its source and complete license terms are available
from the upstream repository and release tag:
https://github.com/YouROK/TorrServer/tree/MatriX.141.1

Pinned release checksums:

- `TorrServer-darwin-arm64`: `a91adbfcec069a0db204ae909d098832d16220c154e86e409b10e2f243e1c7f9`
- `TorrServer-darwin-amd64`: `fbf13d00e9619524b3caba12302886151e3e84219fd08549bb6f585285dfc5ab`

## JSON-java (org.json)

Anikku vendors the JSON-java (org.json) library source at
`src/main/java/org/json/` (version 20231013, tag `20231013` from
https://github.com/stleary/JSON-java), with a small Anikku addition: a
Kotlin-companion shim (`JSONObject$Companion.getNULL()`) so prebuilt anime
extensions compiled against a Kotlin build of org.json can access
`JSONObject.NULL`.

JSON-java is released to the public domain. Its license terms:
https://github.com/stleary/JSON-java (see the license header in each source
file and the repository's LICENSE).
