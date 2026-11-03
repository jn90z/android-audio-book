# Public release checklist

A public stable release requires all of the following:

- Android, edge and full-template workflows green at the release commit.
- No credentials, production OAuth IDs, signing material or private endpoints.
- Apache-2.0 LICENSE and current third-party notices.
- Blank starter app still builds and launches.
- Optional integrations compile independently.
- Runtime permission and lifecycle behavior documented.
- Security policy present.
- Changelog updated.
- Release tag created only from a green main commit.
- Repository visibility changed only after the final secrets/history audit.

The first stable release target is v1.0.0.
