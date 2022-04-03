package repositories

import cats.effect.{Concurrent, Resource}
import models.Tag.*
import models.Article.*
import Codecs.*
import skunk.*
import skunk.implicits.*
import skunk.codec.text.*
import skunk.codec.temporal.*
import cats.syntax.all.*
import skunk.codec.all.int8

import java.time.LocalDate

trait Articles[F[_]]:
  def findAllArticles: F[List[Article]]
  def findArticleById(id: Id): F[Option[Article]]
  def findArticleByUser(user: String): F[List[Article]]
  def findArticleByTag(tagName: TagName): F[List[Article]]
  def findArticleByTagAndUser(tagName: TagName, user: String): F[List[Article]]
  def create(article: Article): F[Article]
  def update(article: Article): F[Article]
  def delete(articleId: Id): F[Option[Article]]
  def addTag(tag: Tag): F[Tag]

object Articles:
  import ArticlesSql.*
  def make[F[_]: Concurrent](postgres: Resource[F, Resource[F, Session[F]]]): Articles[F] =
    new Articles[F] {
      override def findAllArticles: F[List[Article]] = postgres.use(_.use(_.execute(selectAll)))

      override def findArticleById(id: Id): F[Option[Article]] = postgres.use(_.use { session =>
        session.prepare(selectById).use { ps =>
          ps.option(id)
        }
      })

      override def findArticleByUser(user: String): F[List[Article]] = postgres.use(_.use { session =>
        session.prepare(selectByUser).use { ps =>
          ps.stream(user, 15).compile.toList
        }
      })

      override def findArticleByTag(tagName: TagName): F[List[Article]] = postgres.use(_.use { session =>
        session.prepare(selectByTag).use { ps =>
          ps.stream(tagName, 15).compile.toList
        }
      })

      override def findArticleByTagAndUser(tagName: TagName, user: String): F[List[Article]] = postgres.use(_.use { session =>
        session.prepare(selectByTagAndUser).use { ps =>
          ps.stream((tagName, user),15).compile.toList
        }
      })

      override def create(article: Article): F[Article] = postgres.use(_.use { session =>
        session.prepare(insertNote).use(_.execute(article)).as(article)
      })

      override def update(article: Article): F[Article] = postgres.use(_.use { session =>
        session.prepare(updateNote).use(_.execute(article)).as(article)
      })

      override def delete(blogId: Id): F[Option[Article]] = postgres.use(_.use { session =>
        session.prepare(deleteNote).use(ps => ps.option(blogId))
      })

      override def addTag(tag: Tag): F[Tag] = postgres.use(_.use { session =>
        session.prepare(insertTag).use(_.execute(tag)).as(tag)
      })
    }

private object ArticlesSql:
  val decoder: Decoder[Article] =
    ( articleId ~ varchar ~ varchar ~ articleAuthorId ~ int8 ~ varchar ~ date ~ date ).map {
      case nId ~ title ~ content ~ aId ~ likes ~ vis ~ publish ~ edit =>
        Article(
          nId,
          Title.unsafeFrom(title),
          Content.unsafeFrom(content),
          aId,
          WordCount.unsafeFrom(content.split(" ").length),
          ReadingTime.unsafeFrom(content.split(" ").length / 200.0),
          Likes.unsafeFrom(likes.toInt),
          Visibility.unsafeFromString(vis),
          ArticleDate(publish),
          ArticleDate(edit)
        )
    }

  val encoder: Encoder[Article] =
    (varchar ~ varchar ~ varchar ~ varchar ~ varchar ~ date ~ date)
      .contramap { case Article(id, title, content, author, _, _, _, visibility, publish, edit) =>
      id.value ~ title.value ~ content.value ~ author.value ~ visibility.toString ~ publish.value ~ edit.value }

  val tagEncoder: Encoder[Tag] =
    ( varchar ~ varchar ~ varchar).contramap { t => t.id.value ~ t.name.value ~ t.articleId.value }

  val selectAll: Query[Void, Article] =
    sql"""
         select a.article_id,
                a.article_title,
                a.article_content,
                a.article_author,
                (select count(*) from likes_map l where l.like_article = a.article_id) as likes,
                a.article_visibility,
                a.article_publish_date,
                a.article_last_edit_date
         from articles a;
    """.query(decoder)

  val selectById: Query[Id, Article] =
    sql"""
         select a.article_id,
                a.article_title,
                a.article_content,
                a.article_author,
                (select count(*) from likes_map l where l.like_article = a.article_id) as likes,
                a.article_visibility,
                a.article_publish_date,
                a.article_last_edit_date
         from articles a
        where a.article_id = $articleId;
    """.query(decoder)

  val insertNote: Command[Article] =
    sql"""
        insert into articles
        values ($encoder)
    """.command

  val updateNote: Command[Article] =
    sql"""
        update articles
        set article_title = $varchar,
            article_content = $varchar,
            article_visibility = $varchar,
            article_last_edit_date = $date
        where article_id = $articleId
    """.command.contramap { case Article(id, title, content, _, _, _, _, vis, _, _) =>
      title.value ~ content.value ~ vis.toString ~ LocalDate.now ~ id
    }

  val deleteNote: Query[Id, Article] =
    sql"""
        delete from articles where article_id = $articleId returning *
    """.query(decoder)

  val insertTag: Command[Tag] =
    sql"""
        insert into tag_map
        values ($tagEncoder)
    """.command
    
  val selectByUser: Query[String, Article] =
    sql"""
        select * from articles
        where article_author = $varchar
    """.query(decoder)

  val selectByTag: Query[TagName, Article] =
    sql"""
        select a.*
        from articles a, tag_map t
        where t.article_id = a.article_id
        and t.tag_id = $tagName
    """.query(decoder)

  val selectByTagAndUser: Query[TagName ~ String, Article] =
    sql"""
        select a.*
        from articles a, tag_map t
        where t.article_id = a.article_id
        and t.tag_id = $tagName
        and a.article_id = $varchar
    """.query(decoder)