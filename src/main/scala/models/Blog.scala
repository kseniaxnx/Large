package models

import doobie.{Read, Write}
import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec

import java.time.LocalDate
import doobie.implicits.legacy.localdate.*
import com.aventrix.jnanoid.jnanoid.*
import cats.data.*
import cats.implicits.*
import common.{GetItem, GetItems}
import eu.timepit.refined.api.RefinedTypeOps
import eu.timepit.refined.cats.CatsRefinedTypeOpsSyntax
import eu.timepit.refined.types.numeric.{NonNegInt, PosDouble, PosFloat, PosInt, PosLong}
import eu.timepit.refined.types.string.NonEmptyString
import io.circe.refined.*
import org.latestbit.circe.adt.codec.JsonTaggedAdt



object Blog:
  implicit val noteCodec: Codec[Blog] = deriveCodec[Blog]
  implicit val GetItemCodec: Codec[GetItem[Blog]] = deriveCodec
  implicit val GetItemsCodec: Codec[GetItems[Blog]] = deriveCodec

  case class Blog(
                   id: Id,
                   title: Title,
                   content: Content,
                   author: Author,
                   word_count: WordCount,
                   reading_time: ReadingTime,
                   likes: Likes,
                   visibility: Visibility,
                   published_on: BlogDate,
                   last_updated: BlogDate,
                 )

  type Id = NonEmptyString
  object Id extends RefinedTypeOps[NonEmptyString, String] with CatsRefinedTypeOpsSyntax

  type Title = NonEmptyString
  object Title extends RefinedTypeOps[NonEmptyString, String] with CatsRefinedTypeOpsSyntax

  type Content = NonEmptyString
  object Content extends RefinedTypeOps[NonEmptyString, String] with CatsRefinedTypeOpsSyntax

  type Author = NonEmptyString
  object Author extends RefinedTypeOps[NonEmptyString, String] with CatsRefinedTypeOpsSyntax

  enum Visibility derives JsonTaggedAdt.PureEncoder, JsonTaggedAdt.PureDecoder:
    case Public
    case Private

  object Visibility:
    val init: Either[NonEmptyChain[String], Visibility] = Right(Private)
    def fromString(input: String): Either[NonEmptyChain[String], Visibility] = input match
      case "Public" => Right(Public)
      case _ => Right(Private)
    def unsafeFromString(input: String): Visibility = input match
      case "Public" => Public
      case _ => Private

  type WordCount = PosInt
  object WordCount extends RefinedTypeOps[WordCount, Int] with CatsRefinedTypeOpsSyntax

  type ReadingTime = PosDouble
  object ReadingTime extends RefinedTypeOps[ReadingTime, Double] with CatsRefinedTypeOpsSyntax

  type Likes = NonNegInt
  object Likes extends RefinedTypeOps[Likes, Int] with CatsRefinedTypeOpsSyntax

  opaque type BlogDate = LocalDate
  object BlogDate:
    def apply(date: LocalDate): BlogDate = date
    val create: Either[NonEmptyChain[String], BlogDate] =
      Right(LocalDate.now())
    extension (x: BlogDate) def value: LocalDate = x

  case class BlogDto(title: String, content: String, author: String, visibility: String)

  object BlogDto:
    implicit val noteDtoCodec: Codec[BlogDto] = deriveCodec[BlogDto]
    def toDomain(dto: BlogDto): Either[NonEmptyChain[String], Blog] =
      val id = NanoIdUtils.randomNanoId()
      (
        Id.from(id).toEitherNec,
        Title.from(dto.title).toEitherNec,
        Content.from(dto.content).toEitherNec,
        Author.from(dto.author).toEitherNec,
        WordCount.from(dto.content.split(" ").length).toEitherNec,
        ReadingTime.from(dto.content.split(" ").length / 200.0).toEitherNec,
        Likes.from(0).toEitherNec,
        Visibility.fromString(dto.visibility),
        BlogDate.create,
        BlogDate.create
        ).parMapN(Blog.apply)