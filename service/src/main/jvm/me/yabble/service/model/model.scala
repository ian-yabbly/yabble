package me.yabble.service.model

import org.joda.time.DateTime

object Entity {
  case class Free()
  case class Persisted(id: String, creationDate: DateTime, lastUpdatedDate: DateTime, isActive: Boolean)
  case class Update(id: String)
}

object User {
  class Free(val name: Option[String], val email: Option[String])
    extends Entity.Free

  class Update(id: String, val name: Option[String], val email: Option[String])
    extends Entity.Update(id)

  class Persisted(id: String, creationDate: DateTime, lastUpdatedDate: DateTime, isActive: Boolean,
      val name: Option[String], val email: Option[String])
    extends Entity.Persisted(id, creationDate, lastUpdatedDate, isActive)
}

object YList {
  class Free(val userId: String, val title: String, val body: Option[String])
    extends Entity.Free

  class Update(id: String, val title: String, val body: Option[String])
    extends Entity.Update(id)

  class Persisted(id: String, creationDate: DateTime, lastUpdatedDate: DateTime, isActive: Boolean,
      val user: User.Persisted, val title: String, val body: Option[String])
    extends Entity.Persisted(id, creationDate, lastUpdatedDate, isActive)
  {
    def slug(): String = SlugUtils.gen(title)
  }

  object Item {
    class Free(
        val listId: String,
        val userId: String,
        val title: Option[String],
        val body: Option[String],
        val imageIds: List[String])
      extends Entity.Free

    class Update(
        id: String,
        val title: Option[String],
        val body: Option[String])
      extends Entity.Update(id)

    class Persisted(
        id: String,
        creationDate: DateTime,
        lastUpdatedDate: DateTime,
        isActive: Boolean,
        val listId: String,
        val user: User.Persisted,
        val title: Option[String],
        val body: Option[String],
        val images: List[Image.Persisted])
      extends Entity.Persisted(id, creationDate, lastUpdatedDate, isActive)
  }
}
