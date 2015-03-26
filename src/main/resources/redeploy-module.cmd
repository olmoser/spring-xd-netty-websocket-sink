stream destroy "riox-websocket-test"
module delete sink:riox-websocket
module upload --file "websocket-sink-0.0.1-SNAPSHOT.jar" --type "sink" --name "riox-websocket"
stream create --name "riox-websocket-test" --definition "http --port=9191 | riox-websocket" --deploy
