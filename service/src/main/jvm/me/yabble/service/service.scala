package me.yabble.service

import me.yabble.common.Log
import me.yabble.common.TextUtils._
import me.yabble.service.model._
import me.yabble.service.dao._
import me.yabble.service.proto.ServiceProtos._

class NotFoundException(message: String) extends RuntimeException(message)
class EntityNotFoundException(val kind: EntityType, val id: String) extends NotFoundException("[%s] not found [%s]".format(enumToString(kind), id))

trait IService[F <: Entity.Free, P <: Entity.Persisted, U <: Entity.Update] {
  def create(f: F): String
  def find(id: String): P
  def optional(id: String): Option[P]
  def update(u: U)
  def activate(id: String)
  def deactivate(id: String)
}

class Service[F <: Entity.Free, P <: Entity.Persisted, U <: Entity.Update](
    private val dao: EntityDao[F, P, U])
  extends IService[F, P, U]
  with Log
{
  override def create(f: F): String = dao.create(f)
  override def find(id: String): P = dao.find(id)
  override def optional(id: String): Option[P] = dao.optional(id)
  override def update(u: U) { dao.update(u) }
  override def activate(id: String) = dao.activate(id)
  override def deactivate(id: String) = dao.deactivate(id)
}
