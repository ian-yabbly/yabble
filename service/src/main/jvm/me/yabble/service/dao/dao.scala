package me.yabble.service.dao

import me.yabble.common.Predef._
import me.yabble.common.SecurityUtils
import me.yabble.common.TextUtils._
import me.yabble.common.Log
import me.yabble.service.dao.Predef._
import me.yabble.service.model._

import org.joda.time.DateTime

import org.springframework.dao.IncorrectResultSizeDataAccessException
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate

import java.sql.ResultSet
import java.sql.Timestamp
import java.util.Properties
import java.util.{Map => JMap}

import scala.collection.JavaConversions._

object Predef {
  implicit def sqlTimestamp2DateTime(ts: Timestamp): DateTime = ts match {
    case null => null
    case v => new DateTime(ts.getTime)
  }

  implicit def dateTime2SqlTimestamp(dt: DateTime): Timestamp = dt match {
    case null => null
    case v => new Timestamp(v.getMillis)
  }
}

class DuplicateKeyException(e: Exception) extends RuntimeException("Duplicate key", e)

class UnexpectedNumberOfRowsUpdatedException(message: String)
  extends RuntimeException(message)
{
  def this(v: Int) = this(v.toString)
}

class UnexpectedNumberOfRowsSelected(message: String)
  extends RuntimeException(message)
{
  def this(v: Int) = this(v.toString)
}

class ORM(
    protected val tableName: String,
    protected val npt: NamedParameterJdbcTemplate)
{
  private val statements = new Properties()

  val is = classOf[ORM].getResourceAsStream("/sql/%s.properties".format(stringToCode(tableName)))
  try {
    if (null != is) { statements.load(is) }
  } finally {
    if (null != is) { is.close() }
  }

  protected def t() = npt.getJdbcOperations

  protected def optionalStatement(name: String): Option[String] = statements.getProperty(name) match {
    case null => None
    case v => Some(v)
  }

  protected def findStatement(name: String): String = optionalStatement(name) match {
    case Some(v) => v
    case None => error("Statement not found [%s] [%s]".format(tableName, name))
  }

  protected def maybeCreateId(id: String): Boolean = {
    t.queryForList("select value from ids where value = ? for update", classOf[String], id).toList match {
      case head :: tail => false
      case Nil => {
        createId(id)
        true
      }
    }
  }

  protected def createId(id: String) {
    t().update("insert into ids (value) values (?)", id)
  }

  def genId(): String = {
    (1 to 8).foreach(i => {
      val id = SecurityUtils.randomAlphanumeric(8).toLowerCase()
      if (maybeCreateId(id)) {
        return id
      }
    })
    error("Could not generate unique external ID")
  }
}

abstract class EntityDao[F <: Entity.Free, P <: Entity.Persisted, U <: Entity.Update](tableName: String, npt: NamedParameterJdbcTemplate)
  extends ORM(tableName, npt)
  with Log
{
  private val EMPTY_PARAMS = mapAsJavaMap(Map[String, Any]())

  def create(f: F): String = {
    val ps = getInsertParams(f)

    val params = ps.get("id") match {
      case Some(id) => ps
      case None => ps + ("id" -> genId())
    }

    val id = params("id").asInstanceOf[String]

    val stmt = optionalStatement("insert") match {
      case Some(v) => v
      case None => {
        var b = new StringBuilder()
        b.append("insert into ").append(tableName).append(" (")
        params.keySet.foreach(name => b.append(name).append(","))
        b = b.dropRight(1)
        b.append(") values (")
        params.keySet.foreach(name => b.append(":").append(name).append(","))
        b = b.dropRight(1)
        b.append(")")
        b.toString()
      }
    }

    try {
      npt.update(stmt, mapAsJavaMap(params))
    } catch {
      case e: org.springframework.dao.DuplicateKeyException => {
        throw new DuplicateKeyException(e)
      }
    }

    id
  }

  def createOrUpdate(f: F): String = {
    // First, see if we can find the entity
    val params = getQueryParams(f)
    var b = new StringBuilder()
    b.append("select id from ").append(tableName).append(" where ")
    params.foreach(t => {
      b.append(t._1)
      t._2 match {
        case null => b.append(" is null and ")
        case v => b.append(" = :").append(t._1).append(" and ")
      }
    })
    b = b.dropRight(5)
    val stmt = b.toString()

    npt.query(stmt, params, getRowMapper).toList match {
      case head :: tail => head.id
      case Nil => create(f)
    }
  }

  def update(u: U): Int = {
    val params = getUpdateParams(u)
    val stmt = optionalStatement("update") match {
      case Some(v) => v
      case None => {
        var b = new StringBuilder()
        b.append("update ").append(tableName).append(" set ")
        params.keySet.foreach(name => {
          b.append(name).append(" = ").append(":").append(name).append(",")
        })
        b.append("last_updated_date = now()")
        b.append(" where id = :id").toString()
      }
    }

    npt.update(stmt, params)
  }

  def deactivate(id: String): Boolean = {
    npt.update(
        s"update $tableName set is_active = false, last_updated_date = now() where id = :id",
        mapAsJavaMap(Map("id" -> id))) match {
          case 0 => false
          case 1 => true
          case c: Int => throw new UnexpectedNumberOfRowsUpdatedException(c)
        }
  }

  def activate(id: String): Boolean = {
    npt.update(
        s"update $tableName set is_active = true, last_updated_date = now() where id = :id",
        mapAsJavaMap(Map("id" -> id))) match {
          case 0 => false
          case 1 => true
          case c: Int => throw new UnexpectedNumberOfRowsUpdatedException(c)
        }
  }

  def findForUpdate(id: String): P = {
    val stmt = optionalStatement("find-by-id-for-update") match {
      case Some(v) => v
      case None => s"select * from $tableName where id = ? for update"
    }
    t.queryForObject(stmt, getRowMapper, id)
  }

  def optional(id: String): Option[P] = {
    val stmt = optionalStatement("find-by-id") match {
      case Some(v) => v
      case None => s"select * from $tableName where id = ?"
    }
    optional(t.query(stmt, getRowMapper, id).toList)
  }

  def find(id: String): P = {
    val stmt = optionalStatement("find-by-id") match {
      case Some(v) => v
      case None => s"select * from $tableName where id = ?"
    }
    t.queryForObject(stmt, getRowMapper, id)
  }

  def all(): List[P] = {
    t.query(s"select * from $tableName where is_active = true order by creation_date desc", getRowMapper).toList
  }

  def all(offset: Long, limit: Long): List[P] = {
    npt.query(
        s"select * from $tableName where is_active = true order by creation_date desc limit :limit offset :offset",
        mapAsJavaMap(Map("limit" -> limit, "offset" -> offset)),
        getRowMapper).toList
  }

  def all(stmtName: String, params: Map[String, Any]): List[P] = npt.query(
      findStatement(stmtName), mapAsJavaMap(params), getRowMapper).toList

  def optional(stmtName: String, params: Map[String, Any]): Option[P] = optional(all(stmtName, params))

  def optional(vs: List[P]): Option[P] = vs match {
        case Nil => None
        case head :: Nil => Some(head)
        case _ => throw new UnexpectedNumberOfRowsSelected(vs.size)
      }

  protected def getInsertParams(f: F): Map[String, Any]
  protected def getUpdateParams(u: U): Map[String, Any]
  protected def getQueryParams(f: F): Map[String, Any]
  protected def getRowMapper(): RowMapper[P]

  protected final def optionalLong(rs: ResultSet, name: String): Option[Long] = {
    val v = rs.getLong(name)
    if (rs.wasNull()) {
      None
    } else {
      Some(v)
    }
  }
}

class ImageDao(npt: NamedParameterJdbcTemplate)
  extends EntityDao[Image.Free, Image.Persisted, Image.Update]("images", npt)
  with Log
{
  def allOriginals(offset: Long, limit: Long): List[Image.Persisted] = all(
      "find-where-original-url-is-null",
      Map("offset" -> offset, "limit" -> limit))

  def allByOriginalUrl(originalUrl: String): List[Image.Persisted] = all(
      "find-by-original-url",
      Map("original_url" -> originalUrl))

  def optionalByUrl(url: String): Option[Image.Persisted] = optional(
      "find-by-url",
      Map("url" -> url))

  def optionalBySecureUrl(url: String): Option[Image.Persisted] = optional(
      "find-by-secure-url",
      Map("url" -> url))

  def optionalByOriginalImageAndTransform(originalId: String, transform: ImageTransform) = {
    val buf = new StringBuffer()
    buf.append("select * from ").append(tableName).append(" where original_image_id = ? and transform_type = ? and transform_width ")
    if (transform.getWidth.isPresent) {
      buf.append("= ?")
    } else {
      buf.append("is null")
    }
    buf.append(" and transform_height")
    if (transform.getHeight.isPresent) {
      buf.append("= ?")
    } else {
      buf.append("is null")
    }

    if (transform.getWidth.isPresent && transform.getHeight.isPresent) {
      optional(t.query(buf.toString, getRowMapper, enumToCode(transform.getType), transform.getWidth.get, transform.getHeight.get).toList)
    } else if (transform.getWidth.isPresent) {
      optional(t.query(buf.toString, getRowMapper, enumToCode(transform.getType), transform.getWidth.get).toList)
    } else if (transform.getHeight.isPresent) {
      optional(t.query(buf.toString, getRowMapper, enumToCode(transform.getType), transform.getHeight.get).toList)
    } else {
      optional(t.query(buf.toString, getRowMapper, enumToCode(transform.getType)).toList)
    }
  }

  def updatePreviewData(id: String, previewData: Array[Byte]) {
    t.update(findStatement("update-preview-data"), previewData, id)
  }

  def allByYListItem(id: String): List[Image.Persisted] = all("all-by-list-item", Map("list_item_id" -> id))

  override def getInsertParams(f: Image.Free) = {
    val ps = Map(
        "is_interjal" -> f.isInternal,
        "url" -> f.url,
        "secure_url" -> f.secureUrl,
        "mime_type" -> f.mimeType,
        "original_filename" -> f.originalFilename.orNull,
        "original_image_id" -> f.originalImageId.orNull,
        "size" -> f.size.map(v => new java.lang.Long(v)).orNull,
        "width" -> f.width.map(v => new java.lang.Long(v)).orNull,
        "height" -> f.height.map(v => new java.lang.Long(v)).orNull,
        "transform_type" -> f.transform.map(t => enumToCode(t.getType)).orNull,
        "transform_width" -> f.transform.map(t => t.getWidth.orNull).orNull,
        "transform_height" -> f.transform.map(t => t.getHeight.orNull).orNull,
        "original_url" -> f.originalUrl.orNull)

    f.id match {
      case Some(id) => ps + ("id" -> id)
      case None => ps
    }
  }

  override def getUpdateParams(u: Image.Update) = Map(
      "url" -> u.url,
      "secure_url" -> u.secureUrl)

  override def getQueryParams(f: Image.Free) = Map(
      "is_interjal" -> f.isInternal,
      "url" -> f.url,
      "secure_url" -> f.secureUrl,
      "mime_type" -> f.mimeType,
      "original_filename" -> f.originalFilename.orNull,
      "original_image_id" -> f.originalImageId.orNull,
      "size" -> f.size.orNull,
      "width" -> f.width.orNull,
      "height" -> f.height.orNull,
      "transform_type" -> f.transform.map(t => enumToCode(t.getType)).orNull,
      "transform_width" -> f.transform.map(t => t.getWidth.orNull).orNull,
      "transform_height" -> f.transform.map(t => t.getHeight.orNull).orNull,
      "original_url" -> f.originalUrl.orNull)

  override def getRowMapper() = new RowMapper[Image.Persisted]() {
    override def mapRow(rs: ResultSet, rowNum: Int): Image.Persisted = {
      val transform = rs.getString("transform_type") match {
            case null => None
            case kind: String => {
              Some(new ImageTransform(
                  codeToEnum(kind, classOf[ImageTransform.Type]),
                  optionalLong(rs, "transform_width").map(new java.lang.Long(_)),
                  optionalLong(rs, "transform_height").map(new java.lang.Long(_))))
            }
          }

      new Image.Persisted(
          rs.getString("id"),
          rs.getTimestamp("creation_date"),
          rs.getTimestamp("last_updated_date"),
          rs.getBoolean("is_internal"),
          rs.getString("url"),
          rs.getString("secure_url"),
          rs.getString("mime_type"),
          Option(rs.getString("original_filename")),
          Option(rs.getString("original_image_id")),
          optionalLong(rs, "size"),
          optionalLong(rs, "width"),
          optionalLong(rs, "height"),
          transform,
          Option(rs.getString("original_url")),
          Option(rs.getBytes("preview_data")))
    }
  }
}

class UserDao(npt: NamedParameterJdbcTemplate)
  extends EntityDao[User.Free, User.Persisted, User.Update]("users", npt)
  with Log
{
  override def getInsertParams(f: User.Free) = Map("name" -> f.name.orNull, "email" -> f.email.orNull)

  override def getUpdateParams(u: User.Update) = Map("name" -> u.name.orNull, "email" -> u.email.orNull)

  override def getQueryParams(f: User.Free) = Map("name" -> f.name.orNull, "email" -> f.email.orNull)

  override def getRowMapper() = new RowMapper[User.Persisted]() {
    override def mapRow(rs: ResultSet, rowNum: Int): User.Persisted = {
      val id = rs.getString("id")
      new User.Persisted(
          id,
          rs.getTimestamp("creation_date"),
          rs.getTimestamp("last_updated_date"),
          rs.getBoolean("is_active"),
          Option(rs.getString("name")),
          Option(rs.getString("email")))
    }
  }
}

class YListDao(private val userDao: UserDao, npt: NamedParameterJdbcTemplate)
  extends EntityDao[YList.Free, YList.Persisted, YList.Update]("lists", npt)
  with Log
{
  override def getInsertParams(f: YList.Free) = Map("user_id" -> f.userId, "title" -> f.title, "body" -> f.body.orNull)

  override def getUpdateParams(u: YList.Update) = Map("title" -> u.title, "body" -> u.body.orNull)

  override def getQueryParams(f: YList.Free) = Map("title" -> f.title, "body" -> f.body.orNull)

  override def getRowMapper() = new RowMapper[YList.Persisted]() {
    override def mapRow(rs: ResultSet, rowNum: Int): YList.Persisted = {
      val id = rs.getString("id")

      new YList.Persisted(
          id,
          rs.getTimestamp("creation_date"),
          rs.getTimestamp("last_updated_date"),
          rs.getBoolean("is_active"),
          userDao.find(rs.getString("user_id")),
          rs.getString("title"),
          Option(rs.getString("body")))
    }
  }
}

class YListItemDao(private val userDao: UserDao, imageDao: ImageDao, npt: NamedParameterJdbcTemplate)
  extends EntityDao[YList.Item.Free, YList.Item.Persisted, YList.Item.Update]("list_items", npt)
  with Log
{
  override def getInsertParams(f: YList.Item.Free) = Map("list_id" -> f.listId, "user_id" -> f.userId, "title" -> f.title.orNull, "body" -> f.body.orNull)

  override def getUpdateParams(u: YList.Item.Update) = Map("title" -> u.title.orNull, "body" -> u.body.orNull)

  override def getQueryParams(f: YList.Item.Free) = Map("list_id" -> f.listId, "user_id" -> f.userId, "title" -> f.title.orNull, "body" -> f.body.orNull)

  override def getRowMapper() = new RowMapper[YList.Item.Persisted]() {
    override def mapRow(rs: ResultSet, rowNum: Int): YList.Item.Persisted = {
      val id = rs.getString("id")

      new YList.Item.Persisted(
          id,
          rs.getTimestamp("creation_date"),
          rs.getTimestamp("last_updated_date"),
          rs.getBoolean("is_active"),
          rs.getString("list_id"),
          userDao.find(rs.getString("user_id")),
          Option(rs.getString("title")),
          Option(rs.getString("body")),
          imageDao.allByYListItem(id))
    }
  }
}
