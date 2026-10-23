# Docker

Everything for running oiltank-monitor in containers lives here, one directory per way
of running it:

| Directory | Use | What runs in Docker |
| --- | --- | --- |
| [`dev/`](dev/README.md) | Development in the IDE | The support services only: a Mosquitto broker |
| [`local/`](local/README.md) | Local provisioning | The application, publishing to the broker `.env` names |
| [`prod/`](prod/README.md) | Production, deployed by Ansible | The application |

Shared by all three:

| Path | |
| --- | --- |
| `Dockerfile` | The application image, with ffmpeg. It packages the jar Gradle built (`./gradlew bootJar`); it does not build it. |
| `Dockerfile.dockerignore` | Keeps everything but that jar out of the build context, which is the repository root. |
| `mosquitto/` | The development broker's configuration. |

Every compose file is run from the repository root and names the env file
explicitly. Compose looks for `.env` next to the compose file, which here would be
the wrong directory:

```bash
docker compose -f docker/<usage>/compose.<usage>.yaml --env-file .env <command>
```

The image runs on x86-64 and ARM64 alike (a Raspberry Pi included): its base image
and ffmpeg are built for both, so it is built on the host that runs it.

`dev` and `local` share the project name `oiltank-monitor`, and so a network: the
application started by `local` can publish to `dev`'s broker as
`tcp://mosquitto:1883`.
