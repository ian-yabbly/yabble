package me.yabble.service

import me.yabble.common.Log
import me.yabble.service.model._
import me.yabble.service.dao._

class NotFoundException(message: String) extends RuntimeException(message)
class EntityNotFoundException(id: String) extends RuntimeException("Entity not found [%s]".format(id))

trait IService[F <: Entity.Free, P <: Entity.Persisted, U <: Entity.Update] {
  def create(f: F): String
  def find(id: String): P
}

class Service[F <: Entity.Free, P <: Entity.Persisted, U <: Entity.Update](
    private val dao: EntityDao[F, P, U])
  extends IService[F, P, U]
  with Log
{
  override def create(f: F): String = dao.create(f)
  override def find(id: String): P = dao.find(id)
}
