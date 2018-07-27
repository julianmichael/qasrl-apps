package qasrl.apps.browser

import org.scalajs.dom

import scalacss.DevDefaults._

import scala.concurrent.Future

import qasrl.bank.DataIndex
import qasrl.bank.Document
import qasrl.bank.DocumentId
import radhoc.{CacheCall, Cached, Remote}
import qasrl.bank.service.DocumentService
import qasrl.bank.service.WebClientDocumentService

import qasrl.data.Dataset

import nlpdata.util.LowerCaseStrings._

object Main {
  def main(args: Array[String]): Unit = {
    BrowserStyles.addToDocument()

    val dataIndex = {
      import scala.scalajs.js
      import io.circe.scalajs.decodeJs
      import io.circe.syntax._
      import collection.mutable
      import cats.Order.catsKernelOrderingForOrder
      import qasrl.bank.JsonCodecs._
      decodeJs[DataIndex](
        js.Dynamic.global.dataMetaIndex.asInstanceOf[js.Any]
      ) match {
        case Right(index) => index
        case Left(err) =>
          System.err.println(err)
          null: DataIndex
      }
    }

    val apiUrl: String = dom.document
      .getElementById(SharedConstants.apiUrlElementId)
      .getAttribute("value")

    import qasrl.bank.service.WebClientDocumentService
    val dataService = new WebClientDocumentService(
      "http://localhost:8080"
    )

    object CachedDataService extends DocumentService[CacheCall] {
      import scala.concurrent.ExecutionContext.Implicits.global
      import scala.collection.mutable
      import DocumentService._
      val documentCache = mutable.Map.empty[DocumentId, Document]
      val documentRequestCache = mutable.Map.empty[DocumentId, Future[Document]]

      def getDataIndex = Cached(dataIndex)

      def getDocument(id: DocumentId) = {
        documentCache.get(id).map(Cached(_)).getOrElse {
          documentRequestCache.get(id).map(Remote(_)).getOrElse {
            val fut = dataService.getDocument(id)
            documentRequestCache.put(id, fut)
            fut.foreach { doc =>
              documentRequestCache.remove(id)
              documentCache.put(id, doc)
            }
            Remote(fut)
          }
        }
      }

      def searchDocuments(query: Set[LowerCaseString]) = {
        if(query.isEmpty) {
          Cached(dataIndex.allDocumentIds)
        } else {
          Remote(dataService.searchDocuments(query))
        }
      }
    }

    Browser.Component(Browser.Props(CachedDataService)).renderIntoDOM(
      dom.document.getElementById("browser")
    )
  }
}
