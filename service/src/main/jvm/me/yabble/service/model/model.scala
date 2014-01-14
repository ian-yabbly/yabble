package me.yabble.service.model

import org.joda.time.DateTime

case class FreeEntity()
case class PersistedEntity(id: String, creationDate: DateTime, lastUpdatedDate: DateTime, isActive: Boolean)
case class UpdateEntity(id: String)

object User {
  class Free(val name: Option[String], val email: Option[String])
    extends FreeEntity

  class Update(id: String, val name: Option[String], val email: Option[String])
    extends UpdateEntity(id)

  class Persisted(id: String, creationDate: DateTime, lastUpdatedDate: DateTime, isActive: Boolean,
      val name: Option[String], val email: Option[String])
    extends PersistedEntity(id, creationDate, lastUpdatedDate, isActive)
}

object YList {
  class Free(val userId: String, val title: String, val body: Option[String])
    extends FreeEntity

  class Update(id: String, val title: String, val body: Option[String])
    extends UpdateEntity(id)

  class Persisted(id: String, creationDate: DateTime, lastUpdatedDate: DateTime, isActive: Boolean,
      val user: User.Persisted, val title: String, val body: Option[String])
    extends PersistedEntity(id, creationDate, lastUpdatedDate, isActive)
}
