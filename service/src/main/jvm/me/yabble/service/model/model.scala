package me.yabble.service.model

import me.yabble.service.NotFoundException

import org.joda.time.DateTime
import org.joda.time.DateTimeZone

object Entity {
  case class Free()
  case class Persisted(id: String, creationDate: DateTime, lastUpdatedDate: DateTime, isActive: Boolean)
  case class Update(id: String)
}

object User {
  class Free(val name: Option[String], val email: Option[String], val tz: Option[DateTimeZone])
    extends Entity.Free

  class Update(id: String, val name: Option[String], val email: Option[String], val tz: Option[DateTimeZone])
    extends Entity.Update(id)

  class Persisted(id: String, creationDate: DateTime, lastUpdatedDate: DateTime, isActive: Boolean,
      val name: Option[String], val email: Option[String], val tz: Option[DateTimeZone])
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

  class Persisted(
      id: String,
      creationDate: DateTime,
      lastUpdatedDate: DateTime,
      isActive: Boolean,
      val user: User.Persisted,
      val title: String,
      val body: Option[String],
      val items: List[Item.Persisted],
      val comments: List[Comment.Persisted])
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

    def itemComment(commentId: String): YList.Item.Comment.Persisted = items.flatMap(_.comments).find(commentId == _.id) match {
      case Some(comment) => comment
      case None => throw new NotFoundException("List item comment [%s]".format(commentId))
    }

    def itemVote(voteId: String): YList.Item.Vote.Persisted = items.flatMap(_.votes).find(voteId == _.id) match {
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

  }

  object Comment {
    class Free(
        val listId: String,
        val userId: String,
        val body: String)
      extends Entity.Free

    class Persisted(
        id: String,
        creationDate: DateTime,
        lastUpdatedDate: DateTime,
        isActive: Boolean,
        val user: User.Persisted,
        val listId: Long,
        val body: String)
      extends Entity.Persisted(id, creationDate, lastUpdatedDate, isActive)
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

    object Vote {
      class Free(
          val itemId: String,
          val userId: String)
        extends Entity.Free

      class Persisted(
          id: String,
          creationDate: DateTime,
          lastUpdatedDate: DateTime,
          isActive: Boolean,
          val user: User.Persisted,
          val itemId: String)
        extends Entity.Persisted(id, creationDate, lastUpdatedDate, isActive)
    }

    object Comment {
      class Free(
          val itemId: String,
          val userId: String,
          val body: String)
        extends Entity.Free

      class Persisted(
          id: String,
          creationDate: DateTime,
          lastUpdatedDate: DateTime,
          isActive: Boolean,
          val user: User.Persisted,
          val itemId: Long,
          val body: String)
        extends Entity.Persisted(id, creationDate, lastUpdatedDate, isActive)
    }
  }
}
