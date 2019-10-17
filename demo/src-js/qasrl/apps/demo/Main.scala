package qasrl.apps.demo

import org.scalajs.dom

import scalacss.DevDefaults._

object Main {
  def main(args: Array[String]): Unit = {
    DemoStyles.addToDocument()

    val apiEndpoint: String = dom.document
      .getElementById(SharedConstants.apiUrlElementId)
      .getAttribute("value")

    Demo.Component(Demo.Props(apiEndpoint)).renderIntoDOM(
      dom.document.getElementById("demo")
    )
  }
}
