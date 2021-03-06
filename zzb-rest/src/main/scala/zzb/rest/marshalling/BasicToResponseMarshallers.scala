package zzb.rest
package marshalling

import akka.actor.ActorRef

trait BasicToResponseMarshallers {
  implicit def fromResponse: ToResponseMarshaller[RestResponse] = ToResponseMarshaller((value, ctx) ⇒ ctx.marshalTo(value))

  // No implicit conversion to StatusCode allowed here: in `complete(i: Int)`, `i` shouldn't be marshalled
  // as a StatusCode
  implicit def fromStatusCode: ToResponseMarshaller[StatusCode] =
    fromResponse.compose(s ⇒ RestResponse(status = s, entity = s.defaultMessage))

  implicit def fromStatusCodeAndT[S, T](implicit sConv: S ⇒ StatusCode, tMarshaller: Marshaller[T]): ToResponseMarshaller[(S, T)] =
    fromStatusCodeAndHeadersAndT[T].compose { case (s, t) ⇒ (sConv(s), Nil, t) }

  implicit def fromStatusCodeConvertibleAndHeadersAndT[S, T](implicit sConv: S ⇒ StatusCode, tMarshaller: Marshaller[T]): ToResponseMarshaller[(S, Seq[RestHeader], T)] =
    fromStatusCodeAndHeadersAndT[T].compose { case (s, headers, t) ⇒ (sConv(s), headers, t) }

  implicit def fromStatusCodeAndHeadersAndT[T](implicit tMarshaller: Marshaller[T]): ToResponseMarshaller[(StatusCode, Seq[RestHeader], T)] =
    new ToResponseMarshaller[(StatusCode, Seq[RestHeader], T)] {
      def apply(value: (StatusCode, Seq[RestHeader], T), ctx: ToResponseMarshallingContext): Unit = {
        val status = value._1
        val headers = value._2
        val mCtx = new MarshallingContext {
          //def tryAccept(contentTypes: Seq[ContentType]): Option[ContentType] = ctx.tryAccept(contentTypes)
          def handleError(error: Throwable): Unit = ctx.handleError(error)
          def marshalTo(entity: RestEntity, hs: RestHeader*): Unit =
            ctx.marshalTo(RestResponse(status, entity, (headers ++ hs).toList))
          //def rejectMarshalling(supported: Seq[ContentType]): Unit = ctx.rejectMarshalling(supported)
          //def startChunkedMessage(entity: RestEntity, ack: Option[Any], hs: Seq[RestHeader])(implicit sender: ActorRef): ActorRef =
          //  ctx.startChunkedMessage(RestResponse(status, entity, (headers ++ hs).toList), ack)
        }
        tMarshaller(value._3, mCtx)
      }
    }
}
