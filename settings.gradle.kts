rootProject.name = "titan"

include("bootstrap")
include("core")
include("monitor")
include("titan-stomp")

include("benchmark")
include("fanout")
include("titan-spring-client")
include("smoke-test")
include("smoke-test:smoke-spring")