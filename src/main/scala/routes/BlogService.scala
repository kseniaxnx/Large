package routes

import cats.effect.Concurrent
import models.KewlBlog.*
import org.http4s.HttpRoutes
import org.http4s.Status.{Created, NoContent, Ok}
import org.http4s.circe.*
import org.http4s.circe.CirceEntityCodec.circeEntityEncoder
import org.http4s.dsl.Http4sDsl
import org.http4s.implicits.*
import org.http4s.syntax.*
import repositories.Blogs

// These are necessary to use for-comprehensions on F
import cats.syntax.flatMap.*
import cats.syntax.functor.*

// The type constraint of Concurrent is necessary to decode Json
class BlogService[F[_]: Concurrent](repository: Blogs[F]) extends Http4sDsl[F] {
  val routes: HttpRoutes[F] = HttpRoutes.of[F] {
    case GET -> Root => Ok(repository.findAllKewlBlogs)

    case GET -> Root / IntVar(id) =>
      for {
        blog <- repository.findKewlBlogById(id)
        res <- blog.fold(NotFound())(Ok(_))
      } yield res

    case req @ POST -> Root =>
      for {
        kewl <- req.decodeJson[KewlBlog]
        tup <- repository.create(kewl.id.value, kewl.title.value, kewl.content.v)
        kewB = KewlBlog(KewlId(tup._1), KewlTitle(tup._2), KewlContent(tup._3))
        res <- Created(kewB)
      } yield res

    case req @ PUT -> Root / IntVar(id) =>
      for {
        kewl <- req.decodeJson[KewlBlogDto]
        newId <- repository.update(id, kewl.title, kewl.content)
        res <- Created(newId)
      } yield res

    case DELETE -> Root / IntVar(id) =>
      for {
        res <- repository.delete(id)
        y <- res.fold(_ => NotFound(), _ => NoContent())
      } yield y
  }
}