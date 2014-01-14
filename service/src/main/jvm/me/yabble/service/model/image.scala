package me.yabble.service.model

import org.joda.time.DateTime

object Image {
  class Free(
      val isInternal: Boolean,
      val url: String,
      val secureUrl: String,
      val mimeType: String,
      val originalFilename: Option[String],
      val originalImageId: Option[String],
      val size: Option[Long],
      val width: Option[Long],
      val height: Option[Long],
      val transform: Option[ImageTransform],
      val originalUrl: Option[String])
    extends Entity.Free

  class Update(
      id: String,
      val url: String,
      val secureUrl: String)
    extends Entity.Update(id)

  class Persisted(id: String, creationDate: DateTime, lastUpdatedDate: DateTime,
      val isInternal: Boolean,
      val url: String,
      val secureUrl: String,
      val mimeType: String,
      val originalFilename: Option[String],
      val originalImageId: Option[String],
      val size: Option[Long],
      val width: Option[Long],
      val height: Option[Long],
      val transform: Option[ImageTransform],
      val originalUrl: Option[String],
      val previewData: Option[Array[Byte]])
    extends Entity.Persisted(id, creationDate, lastUpdatedDate, true)
}
