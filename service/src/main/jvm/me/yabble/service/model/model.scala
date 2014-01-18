package me.yabble.service.model

import me.yabble.service.NotFoundException
import me.yabble.service.proto.ServiceProtos.EntityType

import org.joda.time.DateTime
import org.joda.time.DateTimeZone

case class Dimensions(width: Long, height: Long)

object Entity {
  case class Free()
  case class Persisted(id: String, creationDate: DateTime, lastUpdatedDate: DateTime, isActive: Boolean)
  class Builder(val id: String, val creationDate: DateTime, val lastUpdatedDate: DateTime, val isActive: Boolean)
  case class Update(id: String)
}

object UserNotification {
  class Free(
      val userId: String,
      val kind: UserNotificationType,
      val refId: Option[String],
      val refType: Option[EntityType],
      val data: Option[Array[Byte]])
    extends Entity.Free

  class Update(
      id: String)
    extends Entity.Update(id)

  class Persisted(
      id: String,
      creationDate: DateTime,
      lastUpdatedDate: DateTime,
      isActive: Boolean,
      val user: User.Persisted,
      val kind: UserNotificationType,
      val refId: Option[String],
      val refType: Option[EntityType],
      val data: Option[Array[Byte]])
    extends Entity.Persisted(id, creationDate, lastUpdatedDate, isActive)

  object Push {
    class Free(
        val userNotificationId: String)
      extends Entity.Free

    class Update(
        id: String)
      extends Entity.Update(id)

    class Persisted(
        id: String,
        creationDate: DateTime,
        lastUpdatedDate: DateTime,
        isActive: Boolean,
        val userNotificationId: String)
      extends Entity.Persisted(id, creationDate, lastUpdatedDate, isActive)
  }
}

object User {
  class Free(
      val name: Option[String],
      val email: Option[String],
      val tz: Option[DateTimeZone],
      val imageId: Option[String])
    extends Entity.Free

  class Update(
      id: String,
      var name: Option[String],
      var email: Option[String],
      var tz: Option[DateTimeZone],
      var imageId: Option[String])
    extends Entity.Update(id)

  class Persisted(
      id: String,
      creationDate: DateTime,
      lastUpdatedDate: DateTime,
      isActive: Boolean,
      val name: Option[String],
      val email: Option[String],
      val tz: Option[DateTimeZone],
      val image: Option[Image.Persisted])
    extends Entity.Persisted(id, creationDate, lastUpdatedDate, isActive)
  {
    def displayName(): String = name.orElse(email).getOrElse("Guest")

    def nameAndEmail(): String = name match {
      case Some(n) => email match {
        case Some(e) => "%s (%s)".format(n, e)
        case None => n
      }
      case None => email match {
        case Some(e) => e
        case None => "Guest"
      }
    }

    def canLogin(): Boolean = name.orElse(email).isDefined

    def toUpdate(): Update = new Update(id, name, email, tz, image.map(_.id))
  }

  object Auth {
    class Free(
        val userId: String,
        val clearPassword: String)
      extends Entity.Free

    class Update(
        id: String,
        val clearPassword: String,
        val resetToken: Option[String],
        val resetTokenCreationDate: Option[DateTime])
      extends Entity.Update(id)

    class Persisted(
        id: String,
        creationDate: DateTime,
        lastUpdatedDate: DateTime,
        isActive: Boolean,
        val userId: String,
        val encPassword: String,
        val salt: String,
        val resetToken: Option[String],
        val resetTokenCreationDate: Option[DateTime])
      extends Entity.Persisted(id, creationDate, lastUpdatedDate, isActive)
  }
}

object Comment {
  class Free(
      val parentId: String,
      val userId: String,
      val body: String)
    extends Entity.Free

  class Update(
      id: String,
      val body: String)
    extends Entity.Update(id)

  class Persisted(
      id: String,
      creationDate: DateTime,
      lastUpdatedDate: DateTime,
      isActive: Boolean,
      val parentId: String,
      val user: User.Persisted,
      val body: String)
    extends Entity.Persisted(id, creationDate, lastUpdatedDate, isActive)
}

object Vote {
  class Free(
      val parentId: String,
      val userId: String)
    extends Entity.Free

  class Update(
      id: String,
      val parentId: String,
      val userId: String)
    extends Entity.Update(id)

  class Persisted(
      id: String,
      creationDate: DateTime,
      lastUpdatedDate: DateTime,
      isActive: Boolean,
      val parentId: String,
      val user: User.Persisted)
    extends Entity.Persisted(id, creationDate, lastUpdatedDate, isActive)
}

object YList {
  class Free(
      val userId: String,
      val title: String,
      val body: Option[String])
    extends Entity.Free

  class Update(id: String, val title: String, val body: Option[String])
    extends Entity.Update(id)

  class Builder(
      id: String,
      creationDate: DateTime,
      lastUpdatedDate: DateTime,
      isActive: Boolean,
      var user: User.Persisted,
      var title: String,
      var body: Option[String],
      var items: List[Item.Persisted],
      var comments: List[Comment.Persisted],
      var users: List[User.Persisted])
    extends Entity.Builder(id, creationDate, lastUpdatedDate, isActive)
  {
    def this(p: Persisted) = this(
        p.id, p.creationDate, p.lastUpdatedDate, p.isActive,
        p.user, p.title, p.body, p.items, p.comments, p.users)

    def toPersisted(): Persisted = new Persisted(
        id, creationDate, lastUpdatedDate, isActive,
        user, title, body, items, comments, users)
  }

  class Persisted(
      id: String,
      creationDate: DateTime,
      lastUpdatedDate: DateTime,
      isActive: Boolean,
      val user: User.Persisted,
      val title: String,
      val body: Option[String],
      val items: List[Item.Persisted],
      val comments: List[Comment.Persisted],
      val users: List[User.Persisted])
    extends Entity.Persisted(id, creationDate, lastUpdatedDate, isActive)
  {
    def slug(): String = SlugUtils.gen(title)

    def optionalComment(commentId: String) = comments.find(c => c.id == commentId)

    def itemVoteCountSince(since: DateTime): Long = items.map(i => {
          i.votes.filter(v => { v.lastUpdatedDate.isEqual(since) || v.lastUpdatedDate.isAfter(since) }).size
        }).sum

    def itemVoteCount(): Long = items.map(_.votes.size).sum

    def itemCommentCount(): Long = items.map(_.comments.size).sum

    def optionalItem(itemId: String): Option[YList.Item.Persisted] = items.find(itemId == _.id)

    def item(itemId: String): YList.Item.Persisted = optionalItem(itemId) match {
      case Some(item) => item
      case None => throw new NotFoundException("List item [%s]".format(itemId))
    }

    def itemComment(commentId: String): Comment.Persisted = items.flatMap(_.comments).find(commentId == _.id) match {
      case Some(comment) => comment
      case None => throw new NotFoundException("List item comment [%s]".format(commentId))
    }

    def itemVote(voteId: String): Vote.Persisted = items.flatMap(_.votes).find(voteId == _.id) match {
      case Some(vote) => vote
      case None => throw new NotFoundException("List item vote [%s]".format(voteId))
    }

    def itemByComment(id: String): YList.Item.Persisted = items.find(i => { i.optionalComment(id).isDefined }) match {
      case Some(item) => item
      case None => throw new NotFoundException("List item by comment [%s]".format(id))
    }

    def itemByVote(id: String): YList.Item.Persisted = items.find(i => { i.optionalVote(id).isDefined }) match {
      case Some(item) => item
      case None => throw new NotFoundException("List item by vote [%s]".format(id))
    }

    def toBuilder(): Builder = new Builder(this)
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
        val images: List[Image.Persisted],
        val votes: List[Vote.Persisted],
        val comments: List[Comment.Persisted])
      extends Entity.Persisted(id, creationDate, lastUpdatedDate, isActive)
    {
      def optionalComment(id: String): Option[Comment.Persisted] = comments.find(_.id == id)

      def comment(id: String): Comment.Persisted = optionalComment(id) match {
        case Some(c) => c
        case None => throw new NotFoundException("List item comment by ID [%s]".format(id))
      }

      def optionalVote(id: String): Option[Vote.Persisted] = votes.find(_.id == id)

      def optionalVoteByUser(uid: String): Option[Vote.Persisted] = votes.find(_.user.id == uid)

      def vote(id: String): Vote.Persisted = optionalVote(id) match {
        case Some(v) => v
        case None => throw new NotFoundException("List item vote [%s]".format(id))
      }
    }
  }
}
