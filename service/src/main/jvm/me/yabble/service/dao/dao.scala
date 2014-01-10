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

  protected def genId(): String = {
    (1 to 8).foreach(i => {
      val id = SecurityUtils.randomAlphanumeric(8).toLowerCase()
      if (maybeCreateId(id)) {
        return id
      }
    })
    error("Could not generate unique external ID")
  }
}

abstract class EntityDao[F <: FreeEntity, P <: PersistedEntity, U <: UpdateEntity](tableName: String, npt: NamedParameterJdbcTemplate)
  extends ORM(tableName, npt)
  with Log
{
  private val EMPTY_PARAMS = mapAsJavaMap(Map[String, Any]())

  def create(f: F): String = {
    val id = genId()
    val params = getInsertParams(f) + ("id" -> id)

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

  def find(id: String): P = {
    val stmt = optionalStatement("find-by-id") match {
      case Some(v) => v
      case None => s"select * from $tableName where id = ?"
    }
    t.queryForObject(stmt, getRowMapper, id)
  }

  def find(): List[P] = {
    t.query(s"select * from $tableName where is_active = true order by creation_date desc", getRowMapper).toList
  }

  def find(offset: Long, limit: Long): List[P] = {
    npt.query(
        s"select * from $tableName where is_active = true order by creation_date desc limit :limit offset :offset",
        mapAsJavaMap(Map("limit" -> limit, "offset" -> offset)),
        getRowMapper).toList
  }

  protected def getInsertParams(f: F): Map[String, Any]
  protected def getUpdateParams(u: U): Map[String, Any]
  protected def getQueryParams(f: F): Map[String, Any]
  protected def getRowMapper(): RowMapper[P]
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
