package org.enso.languageserver.data
import java.util.UUID

import io.circe._
import org.enso.languageserver.filemanager.Path

/**
  * A superclass for all capabilities in the system.
  * @param method method name used to identify the capability.
  */
sealed abstract class Capability(val method: String)

/**
  * A capability allowing the user to modify a given file.
  * @param path the file path this capability is granted for.
  */
case class CanEdit(path: Path) extends Capability(CanEdit.methodName)

object CanEdit {
  val methodName = "canEdit"
}

object Capability {
  import cats.syntax.functor._
  import io.circe.generic.auto._
  import io.circe.syntax._

  implicit val encoder: Encoder[Capability] = {
    case cap: CanEdit => cap.asJson
  }

  implicit val decoder: Decoder[Capability] = Decoder[CanEdit].widen
}

/**
  * A capability registration object, used to identify acquired capabilities.
  *
  * @param id the registration id.
  * @param capability the registered capability.
  */
case class CapabilityRegistration(
  id: CapabilityRegistration.Id,
  capability: Capability
)

object CapabilityRegistration {
  import io.circe.generic.auto._
  import io.circe.syntax._

  type Id = UUID

  /**
    * Creates a new registration from a given capability, assigning it a random
    * ID.
    *
    * @param capability the capability being registered
    * @param idGenerator a generator for the capability ID.
    * @return a new registration for the given capability.
    */
  def apply(
    capability: Capability
  )(implicit idGenerator: IdGenerator): CapabilityRegistration =
    CapabilityRegistration(idGenerator.capabilityRegistrationId, capability)

  private val idField      = "id"
  private val methodField  = "method"
  private val optionsField = "registerOptions"

  implicit val encoder: Encoder[CapabilityRegistration] = registration =>
    Json.obj(
      idField      -> registration.id.asJson,
      methodField  -> registration.capability.method.asJson,
      optionsField -> registration.capability.asJson
    )

  implicit val decoder: Decoder[CapabilityRegistration] = json => {
    def resolveOptions(
      method: String,
      json: Json
    ): Decoder.Result[Capability] = method match {
      case CanEdit.methodName => json.as[CanEdit]
      case _ =>
        Left(DecodingFailure("Unrecognized capability method.", List()))
    }

    for {
      id     <- json.downField(idField).as[Id]
      method <- json.downField(methodField).as[String]
      capability <- resolveOptions(
        method,
        json.downField(optionsField).focus.getOrElse(Json.Null)
      )
    } yield CapabilityRegistration(id, capability)
  }
}
