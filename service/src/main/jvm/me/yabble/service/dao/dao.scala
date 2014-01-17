package me.yabble.service.dao

import me.yabble.common.Predef._
import me.yabble.common.SecurityUtils
import me.yabble.common.TextUtils._
import me.yabble.common.Log
import me.yabble.common.txn.SpringTransactionSynchronization
import me.yabble.common.wq.WorkQueue
import me.yabble.service._
import me.yabble.service.dao.Predef._
import me.yabble.service.model._
import me.yabble.service.proto.ServiceProtos._

import com.google.common.base.Function

import org.joda.time.DateTime
import org.joda.time.DateTimeZone

import org.springframework.dao.IncorrectResultSizeDataAccessException
import org.springframework.dao.EmptyResultDataAccessException
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

class UnexpectedNumberOfRowsSelectedException(message: String)
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

abstract class EntityDao[F <: Entity.Free, P <: Entity.Persisted, U <: Entity.Update](
    tableName: String,
    private val kind: EntityType,
    npt: NamedParameterJdbcTemplate,
    private val txnSync: SpringTransactionSynchronization,
    private val workQueue: WorkQueue)
  extends ORM(tableName, npt)
  with Log
{
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

    addEntityEventTxnSync(kind, EventType.CREATE, id, None)

    id
  }

  private def optional(f: F, activeOnly: Boolean = true): Option[P] = optionalQuery(getQueryParams(f), activeOnly)

  protected def addEntityEventTxnSync(kind: EntityType, event: EventType, id: String, userId: Option[String]) {
    //log.info("Adding entity event txn sync [{}] [{}]", enumToCode(kind), enumToCode(event))
    txnSync.add(new Function[Void, Void]() {
      override def apply(ingored: Void): Void = {
        val b = EntityEvent.newBuilder()
            .setEntityType(kind)
            .setEventType(event)
            .setEntityId(id)
            .setEventTime(DateTime.now().toString())

        userId.foreach(v => b.setUserId(v))

        workQueue.submit("entity-event", b.build().toByteArray())

        return null
      }
    })
  }

  protected def optionalQuery(params: Map[String, Any], activeOnly: Boolean = true): Option[P] = {
    var b = new StringBuilder()
    b.append("select * from ").append(tableName).append(" where ")
    params.foreach(t => {
      b.append(t._1)
      t._2 match {
        case null => b.append(" is null and ")
        case v => b.append(" = :").append(t._1).append(" and ")
      }
    })

    if (activeOnly) {
      b.append("is_active = true")
    } else {
      b = b.dropRight(5)
    }

    val stmt = b.toString()
    //log.info("optionalQuery stmt [{}]", stmt)
    optional(all(stmt, params))
  }

  def createOrUpdate(f: F): String = {
    optional(f) match {
      case Some(v) => v.id
      case None => create(f)
    }
  }

  def maybeActivateOrCreate(f: F) {
    optional(f, false) match {
      case Some(v) => activate(v.id)
      case None => create(f)
    }
  }

  def maybeDeactivate(f: F) {
    optional(f) match {
      case Some(v) => deactivate(v.id)
      case None => // Do nothing
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
        s"update $tableName set is_active = false, last_updated_date = now() where id = :id and is_active = true",
        mapAsJavaMap(Map("id" -> id))) match {
          case 0 => false
          case 1 => true
          case c: Int => throw new UnexpectedNumberOfRowsUpdatedException(c)
        }
  }

  def activate(id: String): Boolean = {
    npt.update(
        s"update $tableName set is_active = true, last_updated_date = now() where id = :id and is_active = false",
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

    try {
      t.queryForObject(stmt, getRowMapper, id)
    } catch {
      case e: EmptyResultDataAccessException => throw new EntityNotFoundException(kind, id)
    }
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
      optionalStatement(stmtName).getOrElse(stmtName),
      mapAsJavaMap(params),
      getRowMapper).toList

  def optional(stmtName: String, params: Map[String, Any]): Option[P] = optional(all(stmtName, params))

  def optional(vs: List[P]): Option[P] = vs match {
        case Nil => None
        case head :: Nil => Some(head)
        case _ => throw new UnexpectedNumberOfRowsSelectedException(vs.size)
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

class ImageDao(npt: NamedParameterJdbcTemplate, txnSync: SpringTransactionSynchronization, workQueue: WorkQueue)
  extends EntityDao[Image.Free, Image.Persisted, Image.Update]("images", EntityType.IMAGE, npt, txnSync, workQueue)
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
    buf.append(" and transform_height ")
    if (transform.getHeight.isPresent) {
      buf.append("= ?")
    } else {
      buf.append("is null")
    }

    if (transform.getWidth.isPresent && transform.getHeight.isPresent) {
      optional(t.query(buf.toString, getRowMapper, originalId, enumToCode(transform.getType), transform.getWidth.get, transform.getHeight.get).toList)
    } else if (transform.getWidth.isPresent) {
      optional(t.query(buf.toString, getRowMapper, originalId, enumToCode(transform.getType), transform.getWidth.get).toList)
    } else if (transform.getHeight.isPresent) {
      optional(t.query(buf.toString, getRowMapper, originalId, enumToCode(transform.getType), transform.getHeight.get).toList)
    } else {
      optional(t.query(buf.toString, getRowMapper, originalId, enumToCode(transform.getType)).toList)
    }
  }

  def updatePreviewData(id: String, previewData: Array[Byte]) {
    t.update(findStatement("update-preview-data"), previewData, id)
  }

  def allByYListItem(id: String): List[Image.Persisted] = all("all-by-list-item", Map("list_item_id" -> id))

  override def getInsertParams(f: Image.Free) = {
    val ps = Map(
        "is_internal" -> f.isInternal,
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
      "is_internal" -> f.isInternal,
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

class UserDao(imageDao: ImageDao, npt: NamedParameterJdbcTemplate, txnSync: SpringTransactionSynchronization, workQueue: WorkQueue)
  extends EntityDao[User.Free, User.Persisted, User.Update]("users", EntityType.USER, npt, txnSync, workQueue)
  with Log
{
  def optionalByEmailForUpdate(email: String): Option[User.Persisted] = optional("select * from users where email = :email for update", Map("email" -> email))

  def optionalByEmail(email: String): Option[User.Persisted] = optionalQuery(Map("email" -> email))

  def optionalByName(name: String): Option[User.Persisted] = optionalQuery(Map("lower_name" -> name.toLowerCase()))

  def allByYList(id: String): List[User.Persisted] = all(
      "select u.* from users u inner join list_users lu on u.id = lu.user_id where lu.list_id = :list_id",
      Map("list_id" -> id))

  override def getInsertParams(f: User.Free) = Map("name" -> f.name.orNull, "lower_name" -> f.name.map(_.toLowerCase).orNull, "email" -> f.email.orNull, "tz" -> f.tz.orNull)

  override def getUpdateParams(u: User.Update) = Map("name" -> u.name.orNull, "lower_name" -> u.name.map(_.toLowerCase).orNull, "email" -> u.email.orNull, "tz" -> u.tz.orNull)

  override def getQueryParams(f: User.Free) = Map("lower_name" -> f.name.map(_.toLowerCase).orNull, "email" -> f.email.orNull, "tz" -> f.tz.orNull)

  override def getRowMapper() = new RowMapper[User.Persisted]() {
    override def mapRow(rs: ResultSet, rowNum: Int): User.Persisted = {
      val id = rs.getString("id")
      new User.Persisted(
          id,
          rs.getTimestamp("creation_date"),
          rs.getTimestamp("last_updated_date"),
          rs.getBoolean("is_active"),
          Option(rs.getString("name")),
          Option(rs.getString("email")),
          Option(rs.getString("tz")).map(tz => DateTimeZone.forID(tz)),
          Option(rs.getString("image_id")).map(iid => imageDao.find(iid)))
    }
  }
}

class UserAuthDao(npt: NamedParameterJdbcTemplate, txnSync: SpringTransactionSynchronization, workQueue: WorkQueue)
  extends EntityDao[User.Auth.Free, User.Auth.Persisted, User.Auth.Update]("user_auths", EntityType.USER_AUTH, npt, txnSync, workQueue)
  with Log
{
  def optionalByUser(id: String): Option[User.Auth.Persisted] = optionalQuery(Map("user_id" -> id))

  override def getInsertParams(f: User.Auth.Free) = {
    val salt = SecurityUtils.randomAlphanumeric(16)
    val encPassword = SecurityUtils.encryptPassword(f.clearPassword, salt)

    Map(
      "user_id" -> f.userId,
      "enc_password" -> encPassword,
      "salt" -> salt)
  }

  override def getUpdateParams(u: User.Auth.Update) = {
    Map(
        "reset_token" -> u.resetToken.orNull,
        "reset_token_creation_date" -> u.resetTokenCreationDate.map(d => dateTime2SqlTimestamp(d)).orNull)
  }

  override def getQueryParams(f: User.Auth.Free) = Map("user_id" -> f.userId)

  override def getRowMapper() = new RowMapper[User.Auth.Persisted]() {
    override def mapRow(rs: ResultSet, rowNum: Int): User.Auth.Persisted = {
      val id = rs.getString("id")
      new User.Auth.Persisted(
          id,
          rs.getTimestamp("creation_date"),
          rs.getTimestamp("last_updated_date"),
          rs.getBoolean("is_active"),
          rs.getString("user_id"),
          rs.getString("enc_password"),
          rs.getString("salt"),
          Option(rs.getString("reset_token")),
          Option(rs.getTimestamp("reset_token_creation_date")))
    }
  }
}

class YListDao(
    private val userDao: UserDao,
    private val ylistCommentDao: YListCommentDao,
    private val ylistItemDao: YListItemDao,
    npt: NamedParameterJdbcTemplate, txnSync: SpringTransactionSynchronization, workQueue: WorkQueue)
  extends EntityDao[YList.Free, YList.Persisted, YList.Update]("lists", EntityType.YLIST, npt, txnSync, workQueue)
  with Log
{
  def addUser(lid: String, uid: String): Boolean = {
    val params = Map("list_id" -> lid, "user_id" -> uid)
    npt.queryForList("select * from list_users where list_id = :list_id and user_id = :user_id for update", params).toList match {
      case Nil => {
        npt.update("insert into list_users (list_id, user_id) values (:list_id, :user_id)", params)
        addEntityEventTxnSync(EntityType.YLIST_USER, EventType.CREATE, lid, Some(uid))
        true
      }
      case head :: Nil => false
      case vs => throw new UnexpectedNumberOfRowsSelectedException(vs.size)
    }
  }

  def removeUser(lid: String, uid: String): Boolean = npt.update(
      "delete from list_users where list_id = :list_id and user_id = :user_id",
      Map("list_id" -> lid, "user_id" -> uid)) match {
        case 0 => false
        case 1 => {
          addEntityEventTxnSync(EntityType.YLIST_USER, EventType.DELETE, lid, Some(uid))
          true
        }
        case n: Int => throw new UnexpectedNumberOfRowsUpdatedException(n)
      }

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
          Option(rs.getString("body")),
          ylistItemDao.allByYList(id),
          ylistCommentDao.allByParent(id),
          userDao.allByYList(id))
    }
  }
}

class YListItemDao(
    private val userDao: UserDao,
    private val imageDao: ImageDao,
    private val ylistItemVoteDao: YListItemVoteDao,
    private val ylistItemCommentDao: YListItemCommentDao,
    npt: NamedParameterJdbcTemplate, txnSync: SpringTransactionSynchronization, workQueue: WorkQueue)
  extends EntityDao[YList.Item.Free, YList.Item.Persisted, YList.Item.Update]("list_items", EntityType.YLIST_ITEM, npt, txnSync, workQueue)
  with Log
{
  def allByYList(id: String) = all("all-by-list", Map("list_id" -> id))

  def addImage(listItemId: String, imageId: String) {
    npt.update("insert into list_item_images (list_item_id, image_id) values (:list_item_id, :image_id)", Map("list_item_id" -> listItemId, "image_id" -> imageId))
  }

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
          imageDao.allByYListItem(id),
          ylistItemVoteDao.allByParent(id),
          ylistItemCommentDao.allByParent(id))
    }
  }
}

abstract class CommentDao(tableName: String, kind: EntityType, private val userDao: UserDao, npt: NamedParameterJdbcTemplate, txnSync: SpringTransactionSynchronization, workQueue: WorkQueue)
  extends EntityDao[Comment.Free, Comment.Persisted, Comment.Update](tableName, kind, npt, txnSync, workQueue)
  with Log
{
  def allByParent(id: String) = all(
      "select * from %s where parent_id = :parent_id and is_active = true order by creation_date asc".format(tableName),
      Map("parent_id" -> id))

  override def getInsertParams(f: Comment.Free) = Map("parent_id" -> f.parentId, "user_id" -> f.userId, "body" -> f.body)

  override def getUpdateParams(u: Comment.Update) = Map("body" -> u.body)

  override def getQueryParams(f: Comment.Free) = Map("parent_id" -> f.parentId, "user_id" -> f.userId, "body" -> f.body)

  override def getRowMapper() = new RowMapper[Comment.Persisted]() {
    override def mapRow(rs: ResultSet, rowNum: Int): Comment.Persisted = {
      val id = rs.getString("id")

      new Comment.Persisted(
          id,
          rs.getTimestamp("creation_date"),
          rs.getTimestamp("last_updated_date"),
          rs.getBoolean("is_active"),
          rs.getString("parent_id"),
          userDao.find(rs.getString("user_id")),
          rs.getString("body"))
    }
  }
}

class YListCommentDao(userDao: UserDao, npt: NamedParameterJdbcTemplate, txnSync: SpringTransactionSynchronization, workQueue: WorkQueue)
    extends CommentDao("list_comments", EntityType.YLIST_COMMENT, userDao, npt, txnSync, workQueue)

class YListItemCommentDao(userDao: UserDao, npt: NamedParameterJdbcTemplate, txnSync: SpringTransactionSynchronization, workQueue: WorkQueue)
    extends CommentDao("list_item_comments", EntityType.YLIST_ITEM_COMMENT, userDao, npt, txnSync, workQueue)

abstract class VoteDao(tableName: String, kind: EntityType, private val userDao: UserDao, npt: NamedParameterJdbcTemplate, txnSync: SpringTransactionSynchronization, workQueue: WorkQueue)
  extends EntityDao[Vote.Free, Vote.Persisted, Vote.Update](tableName, kind, npt, txnSync, workQueue)
  with Log
{
  def allByParent(id: String) = all(
      "select * from %s where parent_id = :parent_id and is_active = true order by creation_date asc".format(tableName),
      Map("parent_id" -> id))

  override def getInsertParams(f: Vote.Free) = Map("parent_id" -> f.parentId, "user_id" -> f.userId)

  override def getUpdateParams(u: Vote.Update) = Map()

  override def getQueryParams(f: Vote.Free) = Map("parent_id" -> f.parentId, "user_id" -> f.userId)

  override def getRowMapper() = new RowMapper[Vote.Persisted]() {
    override def mapRow(rs: ResultSet, rowNum: Int): Vote.Persisted = {
      val id = rs.getString("id")

      new Vote.Persisted(
          id,
          rs.getTimestamp("creation_date"),
          rs.getTimestamp("last_updated_date"),
          rs.getBoolean("is_active"),
          rs.getString("parent_id"),
          userDao.find(rs.getString("user_id")))
    }
  }
}

class YListVoteDao(userDao: UserDao, npt: NamedParameterJdbcTemplate, txnSync: SpringTransactionSynchronization, workQueue: WorkQueue)
    extends VoteDao("list_votes", EntityType.YLIST_VOTE, userDao, npt, txnSync, workQueue)

class YListItemVoteDao(userDao: UserDao, npt: NamedParameterJdbcTemplate, txnSync: SpringTransactionSynchronization, workQueue: WorkQueue)
    extends VoteDao("list_item_votes", EntityType.YLIST_ITEM_VOTE, userDao, npt, txnSync, workQueue)

class UserNotificationDao(userDao: UserDao, npt: NamedParameterJdbcTemplate, txnSync: SpringTransactionSynchronization, workQueue: WorkQueue)
    extends EntityDao[UserNotification.Free, UserNotification.Persisted, UserNotification.Update]("user_notifications", EntityType.USER_NOTIFICATION, npt, txnSync, workQueue)
    with Log
{
  override def getInsertParams(f: UserNotification.Free) = Map(
      "user_id" -> f.userId,
      "type" -> enumToCode(f.kind),
      "ref_id" -> f.refId.orNull,
      "ref_type" -> f.refType.map(enumToCode(_)).orNull,
      "data" -> f.data.orNull)

  override def getUpdateParams(u: UserNotification.Update) = Map()

  override def getQueryParams(f: UserNotification.Free) = Map(
      "user_id" -> f.userId,
      "type" -> enumToCode(f.kind),
      "ref_id" -> f.refId.orNull,
      "ref_type" -> f.refType.map(enumToCode(_)).orNull,
      "data" -> f.data.orNull)

  override def getRowMapper() = new RowMapper[UserNotification.Persisted]() {
    override def mapRow(rs: ResultSet, rowNum: Int): UserNotification.Persisted = {
      new UserNotification.Persisted(
          rs.getString("id"),
          rs.getTimestamp("creation_date"),
          rs.getTimestamp("last_updated_date"),
          rs.getBoolean("is_active"),
          userDao.find(rs.getString("user_id")),
          codeToEnum(rs.getString("type"), classOf[UserNotificationType]),
          Option(rs.getString("ref_id")),
          Option(codeToEnum(rs.getString("ref_type"), classOf[EntityType])),
          Option(rs.getBytes("data")))
    }
  }
}

class UserNotificationPushDao(npt: NamedParameterJdbcTemplate, txnSync: SpringTransactionSynchronization, workQueue: WorkQueue)
    extends EntityDao[UserNotification.Push.Free, UserNotification.Push.Persisted, UserNotification.Push.Update](
        "user_notifications",
        EntityType.USER_NOTIFICATION_PUSH,
        npt,
        txnSync,
        workQueue)
    with Log
{
  override def getInsertParams(f: UserNotification.Push.Free) = Map("user_notification_id" -> f.userNotificationId)

  override def getUpdateParams(u: UserNotification.Push.Update) = Map()

  override def getQueryParams(f: UserNotification.Push.Free) = Map("user_notification_id" -> f.userNotificationId)

  override def getRowMapper() = new RowMapper[UserNotification.Push.Persisted]() {
    override def mapRow(rs: ResultSet, rowNum: Int): UserNotification.Push.Persisted = {
      new UserNotification.Push.Persisted(
          rs.getString("id"),
          rs.getTimestamp("creation_date"),
          rs.getTimestamp("last_updated_date"),
          rs.getBoolean("is_active"),
          rs.getString("user_notification_id"))
    }
  }
}
