# oiltank-monitor

Monitors a heating-oil tank's level by reading its float gauge from an RTSP camera
snapshot, and publishes the level to Home Assistant over MQTT.

A Kotlin / Spring Boot rewrite of
[monitor-oiltank](https://github.com/brulejr/monitor-oiltank). See
[docs/DESIGN.md](docs/DESIGN.md) for the design.

## Build and run

Requires Java 21. The Gradle wrapper downloads Gradle itself.

```
./gradlew build      # compile and test; produces build/libs/oiltank-monitor.jar
./gradlew bootRun    # runs on http://localhost:5001
```

Actuator endpoints are under `/mgmt`, for example
`curl localhost:5001/mgmt/health` and `curl localhost:5001/mgmt/info`.

## License

MIT; see [LICENSE](LICENSE).
