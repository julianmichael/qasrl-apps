package qasrl.apps.demo

import org.scalajs.dom

import scalacss.DevDefaults._

object Main {
  def main(args: Array[String]): Unit = {
    DemoStyles.addToDocument()
    val apiEndpoint = "http://recycle.cs.washington.edu:5000/predict"
    Demo.Component(Demo.Props(apiEndpoint)).renderIntoDOM(
      dom.document.getElementById("demo")
    )
  }
}
