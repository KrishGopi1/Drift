package app.drift.engine

import app.drift.model.engine.EngineVersion

/** Version of the analysis engine. Any change to outputs bumps this and adds a line to CHANGELOG.md. */
val ENGINE_VERSION: EngineVersion = EngineVersion(1, 1, 1)
