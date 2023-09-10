package artois

import cats.effect.IO
import cats.effect.unsafe.implicits.global

import scala.scalajs.js
import scala.scalajs.js.annotation.*
import org.scalajs.dom.*
import org.scalajs.dom

import scala.concurrent.{ExecutionContext, ExecutionContextExecutor, Future}

package object artois {
  val URL = "http://localhost:1366/"

  case class Token(
      text: String,
      localAttentionPaid: List[Double],
      localAttentionReceived: List[Double],
      globalAttention: Double,
      characterIndex: Int,
  )

  case class ContextBox(
      originalText: String,
      currentText: String,
      tokens: List[Token],
  ) {
    def updateText(newText: String): ContextBox = {
      ContextBox(originalText, newText, tokens)
    }

    private def getGlobalHighlights(tokens: List[Token]): String = {
      tokens.map { token =>
        if (token.globalAttention > 0.05) {
          val GB = (255 * (1 - token.globalAttention)).toInt
          s"<mark style='background-color: rgb(255, $GB, $GB)'>${token.text}</mark>"
        } else {
          token.text
        }
      }.mkString
    }

    private def getLocalHighlights(tokens: List[Token], textSelected: (Int, Int)): String = {
      val tokensSelected = tokens
        .dropWhile(token => token.characterIndex + token.text.length < textSelected._1)
        .takeWhile(token => token.characterIndex < textSelected._2)

      val localAttentionsPaid = tokensSelected.map(_.localAttentionPaid.padTo(tokens.length, 0))
      val localAttentionsReceived =
        tokensSelected.map(_.localAttentionReceived.reverse.padTo(tokens.length, 0).reverse) // i am lazy bastard

      // these casts are needed, sbt big dumb
      val averageLocalAttentionPaid =
        localAttentionsPaid.transpose.map(_.asInstanceOf[List[Double]].sum / tokensSelected.length)
      val averageLocalAttentionReceived =
        localAttentionsReceived.transpose.map(_.asInstanceOf[List[Double]].sum / tokensSelected.length)

      println(s"Average local attention paid: $averageLocalAttentionPaid")
      println(s"Average local attention received: $averageLocalAttentionReceived")

      tokens.zipWithIndex.map {
        case (token, index) =>
          if (tokensSelected.contains(token)) {
            s"<mark style='background-color: rgb(223, 223, 255)'>${token.text}</mark>"
          } else {
            if (averageLocalAttentionPaid(index) != 0) {
              val GB = (255 * (1 - averageLocalAttentionPaid(index))).toInt
              s"<mark style='background-color: rgb(255, $GB, $GB)'>${token.text}</mark>"
            } else if (averageLocalAttentionReceived(index) != 0) {
              val RG = (255 * (1 - averageLocalAttentionReceived(index))).toInt
              s"<mark style='background-color: rgb($RG, 255, $RG)'>${token.text}</mark>"
            } else {
              token.text
            }
          }
      }.mkString
    }

    private def getHighlights(textSelected: Option[(Int, Int)]): String = {
      val originalCharactersCount =
        Some(originalText.map(Some.apply).zipAll(currentText.map(Some.apply), None, None).indexWhere((a, b) => a != b))
          .filter(_ >= 0)
          .filter(_ < originalText.length)

      val tokensToHighlight = originalCharactersCount match {
        case Some(count) => tokens.takeWhile(_.characterIndex <= count).dropRight(1)
        case None        => tokens
      }

      textSelected match {
        case Some(textSelected) => getLocalHighlights(tokensToHighlight, textSelected)
        case None               => getGlobalHighlights(tokensToHighlight)
      }
    }

    def renderContextBox(
        textElement: HTMLTextAreaElement,
        highlightsElement: Element,
        textSelected: Option[(Int, Int)] = None,
    ): Unit = {
      textElement.value = currentText
      highlightsElement.innerHTML = getHighlights(textSelected)
    }
  }

  object ContextBox {
    def empty(): ContextBox = ContextBox("", List.empty)

    def example(): ContextBox =
      ContextBox(
        text = "This is an example of some text.",
        tokens = List(
          Token("This ", List.empty[Double], List(0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7), 0.32, 0),
          Token("is ", List(0.1), List(0.1, 0.2, 0.3, 0.4, 0.5, 0.6), 0.30, 5),
          Token("an ", List(0.2, 0.1), List(0.1, 0.2, 0.3, 0.4, 0.5), 0.28, 8),
          Token("example ", List(0.3, 0.2, 0.1), List(0.1, 0.2, 0.3, 0.4), 0.25, 11),
          Token("of ", List(0.4, 0.3, 0.2, 0.1), List(0.1, 0.2, 0.3), 0.21, 19),
          Token("some ", List(0.5, 0.4, 0.3, 0.2, 0.1), List(0.1, 0.2), 0.16, 22),
          Token("text", List(0.6, 0.5, 0.4, 0.3, 0.2, 0.1), List(0.1), 0.1, 27),
          Token(".", List(0.7, 0.6, 0.5, 0.4, 0.3, 0.2, 0.1), List.empty[Double], 0.0, 31),
        ),
      )

    def apply(
        text: String,
        tokens: List[Token],
    ): ContextBox = {
      ContextBox(text, text, tokens)
    }
  }

  case class APIResponse(
      tokenStrings: List[String],
      attention: List[List[Double]],
  )

  object APIResponse {
    def apply(json: js.Dynamic): APIResponse = {
      val tokenStrings =
        json.selectDynamic("tokens").asInstanceOf[js.Array[String]].toList
      val attention = json.selectDynamic("attention").asInstanceOf[js.Array[js.Array[Double]]].toList.map(_.toList)
      APIResponse(tokenStrings, attention)
    }
  }

  object utils {
    def textToSliderMapping(x: Int): Int = Math.floor(2 * Math.log(x) / Math.log(2)).toInt

    def sliderToTextMapping(x: Int): Int = {
      x match {
        case 1               => 1
        case x if x % 2 == 0 => Math.pow(2, x / 2).toInt
        case x =>
          val x1 = Math.pow(2, (x - 1) / 2)
          val x2 = Math.pow(2, (x + 1) / 2)
          Math.floor((x1 + x2) / 2).toInt
      }
    }

    def fetchKWArgs(): Map[String, String] = {
      val keys = document.getElementsByClassName("kwargs-key").asInstanceOf[HTMLCollection[HTMLInputElement]]
      val values = document.getElementsByClassName("kwargs-value").asInstanceOf[HTMLCollection[HTMLInputElement]]

      keys
        .zip(values)
        .map((k, v) => (k.value, v.value))
        .filter((k, v) => k != "" && v != "")
        .toMap
    }

    def generate(context: String, kwargs: Map[String, String])(implicit ec: ExecutionContext): IO[APIResponse] = {
      val url = URL
      val data = js.JSON.stringify(js.Dictionary[String]((kwargs + ("context" -> context)).toSeq: _*))
      val header = new Headers()
      header.append("Content-Type", "application/json")
      val requestInit = new RequestInit {
        method = HttpMethod.POST
        body = data
        headers = header
      }
      val request = new Request(url, requestInit)

      IO.fromFuture(
        IO(
          Fetch
            .fetch(request)
            .toFuture
            .flatMap(_.json().toFuture.asInstanceOf[Future[js.Dynamic]])
            .map(APIResponse.apply)
        )
      )
    }

    def generateN(context: String, kwargs: Map[String, String], maxTokens: Int, handler: APIResponse => Unit)(implicit
        ec: ExecutionContext
    ): IO[Unit] = {
      if (maxTokens <= 0) {
        IO.unit
      } else {
        generate(context, kwargs).flatMap { response =>
          handler(response)
          generateN(response.tokenStrings.mkString, kwargs, maxTokens - kwargs("batch_size").toInt, handler)
        }
      }
    }

    def localAttentionPaid(attention: List[List[Double]], index: Int): List[Double] =
      index match {
        case 0     => List.empty
        case index => attention(index - 1).take(3)
      }

    def localAttentionReceived(attention: List[List[Double]], index: Int): List[Double] =
      attention.map(_(index)).drop(index)

    def globalAttention(attention: List[List[Double]], index: Int): Double =
      localAttentionReceived(attention, index).sum / index
  }

  @main
  def ArtoisFrontend(): Unit = {

    implicit val ec: ExecutionContextExecutor = ExecutionContext.global

    val textWindow: HTMLTextAreaElement = document.getElementById("text-window").asInstanceOf[HTMLTextAreaElement]
    val textHighlights = document.getElementById("text-highlights")
    val generateButton = document.getElementById("generate-button")
    val stopButton = document.getElementById("stop-button")
    val clearButton = document.getElementById("clear-button")
    val generationSlider: HTMLInputElement =
      document.getElementById("generation-count-slider").asInstanceOf[HTMLInputElement]
    val generationTextbox: HTMLInputElement =
      document.getElementById("generation-count-textbox").asInstanceOf[HTMLInputElement]

    var contextBox = ContextBox.example()

    var generationCancelToken: Option[() => Future[Unit]] = None

    contextBox.renderContextBox(textWindow, textHighlights)

    textWindow.addEventListener(
      "input",
      _ => {
        contextBox = contextBox.updateText(textWindow.value)
        contextBox.renderContextBox(textWindow, textHighlights)
      },
    )

    generateButton.addEventListener(
      "click",
      _ => {
        val context = textWindow.value
        val kwargs = utils.fetchKWArgs()
        val maxTokens = generationTextbox.value.toInt

        val generateNIO = utils.generateN(
          context,
          kwargs,
          maxTokens,
          {
            case APIResponse(tokenStrings, attention) =>
              println(s"Got response: $tokenStrings")
              println(s"Got attention: $attention")

              val text = tokenStrings.mkString
              val tokens = tokenStrings
                .scanLeft[(String, Int)]("" -> 0) {
                  case ((prevTokenString, index), tokenString) =>
                    (tokenString, index + prevTokenString.length)
                }
                .drop(1)
                .zipWithIndex
                .map {
                  case ((tokenString, characterIndex), index) =>
                    Token(
                      tokenString,
                      utils.localAttentionPaid(attention, index),
                      utils.localAttentionReceived(attention, index),
                      utils.globalAttention(attention, index),
                      characterIndex,
                    )
                }

              println("Got tokens")
              tokens.foreach { token =>
                println(s"Token: ${token.text}, index: ${token.characterIndex}")
              }

              textWindow.value = text
              contextBox = ContextBox(text, tokens)
              contextBox.renderContextBox(textWindow, textHighlights)
          },
        )

        generationCancelToken = Some(generateNIO.unsafeRunCancelable())
      },
    )

    stopButton.addEventListener(
      "click",
      _ => {
        generationCancelToken.foreach(_.apply())
        generationCancelToken = None
      },
    )

    clearButton.addEventListener(
      "click",
      _ => {
        textWindow.value = ""
        contextBox = contextBox.updateText(textWindow.value)
        contextBox.renderContextBox(textWindow, textHighlights)
      },
    )

    generationSlider.addEventListener(
      "input",
      _ => {
        generationTextbox.value = utils.sliderToTextMapping(generationSlider.value.toInt).toString
      },
    )

    generationTextbox.addEventListener(
      "input",
      _ => {
        generationSlider.value = utils.textToSliderMapping(generationTextbox.value.toInt).toString
      },
    )

    // onmouseup event
    textWindow.addEventListener(
      "mouseup",
      _ => {
        if (textWindow.selectionStart != textWindow.selectionEnd) {
          val textSelected = (textWindow.selectionStart, textWindow.selectionEnd)
          contextBox.renderContextBox(textWindow, textHighlights, Some(textSelected))
        } else {
          contextBox.renderContextBox(textWindow, textHighlights)
        }
      },
    )
  }
}
