package qasrl.apps.browser

import scalatags.Text.all.Frag
import java.nio.file.Path

case class GenerationConfig(
  apiUrl: String,
  bootstrapLink: Frag,
  bootstrapScripts: Frag,
  dataMetaIndexLocation: String,
  browserScriptDepsLocation: String,
  browserScriptLocation: String,
  siteRoot: Path
)
