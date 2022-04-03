package routes

import cats.data.Validated.{Invalid, Valid}
import cats.effect.Concurrent
import cats.Monad
import common.*
import models.Blog.*
import models.Tag.{TagDto, TagName}
import org.http4s.*
import org.http4s.Status.{Created, NoContent, NotFound, Ok, UnprocessableEntity}
import org.http4s.circe.*
import org.http4s.circe.CirceEntityCodec.circeEntityEncoder
import org.http4s.dsl.Http4sDsl
import org.http4s.implicits.*
import org.http4s.syntax.*
import repositories.Notes

// These are necessary to use for-comprehensions on F
import cats.syntax.flatMap.*
import cats.syntax.functor.*
//import cats.syntax.*
import cats.implicits.*


//import monocle.syntax.*
import monocle.Lens
import monocle.macros.GenLens
import monocle.macros.syntax.AppliedFocusSyntax
import monocle.syntax.all.*

// The type constraint of Concurrent is necessary to decode Json
class NotesRoutes[F[_]: JsonDecoder: Monad](repository: Notes[F]) extends Http4sDsl[F] {

  implicit val tagCoder: QueryParamDecoder[TagName] =
    QueryParamDecoder[String].map(TagName.unsafeFrom)

  object NoteIdVar:
    def unapply(str: String): Option[Id] = Some(Id.unsafeFrom(str))
  
  object OptionalTagQueryParamMatcher extends  OptionalQueryParamDecoderMatcher[TagName]("tag")
  object OptionalUserIdParamMatch extends OptionalQueryParamDecoderMatcher[String]("user")

  val routes: HttpRoutes[F] = HttpRoutes.of[F] {

    case GET -> Root :? OptionalTagQueryParamMatcher(tag) +& OptionalUserIdParamMatch(user) => (tag, user) match {
      case (Some(t), Some(u)) => Ok(repository.findBlogByTagAndUser(t, u))
      case (Some(t), None) => Ok(repository.findBlogByTag(t))
      case (None, Some(u)) => Ok(repository.findBlogByUser(u))
      case (None, None) => Ok(repository.findAllBlogs.map(GetItems.apply))
    }

    case GET -> Root / NoteIdVar(id) =>
      for {
        note <- repository.findBlogById(id)
        res <- note.fold(NotFound())(n => Ok(GetItem(n)))
      } yield res

    case req @ POST -> Root =>
      for
        dto <- req.asJsonDecode[BlogDto]
        note <- BlogDto.toDomain(dto).pure[F]
        res <- note.fold(UnprocessableEntity(_), b =>
          Created(repository.create(b).map(GetItem.apply)))
      yield res

    case req @ POST -> Root / NoteIdVar(id) / "addTag" =>
      for {
        dto <- req.asJsonDecode[TagDto]
        tag <- TagDto.toDomain(dto, id).pure[F]
        res <- tag.fold(UnprocessableEntity(_), t => Ok(repository.addTag(t)))
      } yield res

    case req @ PUT -> Root / NoteIdVar(id) =>
      for {
        dto <- req.asJsonDecode[BlogDto]
        foundNote <- repository.findBlogById(id)
        updateNote = BlogDto.toDomain(dto)
        res <- (foundNote, updateNote) match
          case (None, _) => NotFound()
          case (_, Left(e)) => UnprocessableEntity(e)
          case (Some(b), Right(u)) =>
            val newNote = b.copy(title = u.title, content = u.content, visibility = u.visibility)
            Created(repository.update(newNote).map(GetItem.apply))
      } yield res

    case DELETE -> Root / NoteIdVar(id) =>
      for {
        res <- repository.delete(id)
        y <- res.fold(NotFound())( _ => NoContent())
      } yield y
  }
}