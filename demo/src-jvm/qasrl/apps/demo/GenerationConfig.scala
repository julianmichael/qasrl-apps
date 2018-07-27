package qasrl.apps.demo

import scalatags.Text.all.Frag
import java.nio.file.Path

case class GenerationConfig(
  apiUrl: String,
  bootstrapLink: Frag,
  bootstrapScripts: Frag,
  demoScriptDepsLocation: String,
  demoScriptLocation: String,
  siteRoot: Path
)
