package me.yabble.service

import me.yabble.common.Log
import me.yabble.service.model._
import me.yabble.service.dao._

class NotFoundException(message: String) extends RuntimeException(message)
class EntityNotFoundException(val kind: String, val id: String) extends RuntimeException("[%s] not found [%s]".format(kind, id))

trait IService[F <: Entity.Free, P <: Entity.Persisted, U <: Entity.Update] {
  def create(f: F): String
  def find(id: String): P
  def optional(id: String): Option[P]
}

class Service[F <: Entity.Free, P <: Entity.Persisted, U <: Entity.Update](
    private val dao: EntityDao[F, P, U])
  extends IService[F, P, U]
  with Log
{
  override def create(f: F): String = dao.create(f)
  override def find(id: String): P = dao.find(id)
  override def optional(id: String): Option[P] = dao.optional(id)
}
