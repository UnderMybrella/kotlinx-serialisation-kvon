
rootProject.name = "kotlinx-serialisation-kvon"

include(":kotlinx-serialisation", ":ktor:http", ":ktor:client", ":ktor:server", ":bootstrap")

project(":kotlinx-serialisation").name = "kotlinx-serialisation-kvon"
project(":ktor:http").name = "ktor-http-kvon"
project(":ktor:client").name = "ktor-client-kvon"
project(":ktor:server").name = "ktor-server-kvon"