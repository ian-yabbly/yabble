// Generated by the protocol buffer compiler.  DO NOT EDIT!
// source: common.proto

package me.yabble.common.proto;

public final class CommonProtos {
  private CommonProtos() {}
  public static void registerAllExtensions(
      com.google.protobuf.ExtensionRegistry registry) {
  }
  public interface DelayedJobOrBuilder
      extends com.google.protobuf.MessageOrBuilder {

    // required int64 id = 1;
    /**
     * <code>required int64 id = 1;</code>
     */
    boolean hasId();
    /**
     * <code>required int64 id = 1;</code>
     */
    long getId();

    // required int64 item_id = 2;
    /**
     * <code>required int64 item_id = 2;</code>
     */
    boolean hasItemId();
    /**
     * <code>required int64 item_id = 2;</code>
     */
    long getItemId();

    // required string qname = 3;
    /**
     * <code>required string qname = 3;</code>
     */
    boolean hasQname();
    /**
     * <code>required string qname = 3;</code>
     */
    java.lang.String getQname();
    /**
     * <code>required string qname = 3;</code>
     */
    com.google.protobuf.ByteString
        getQnameBytes();

    // required string datetime_to_submit = 4;
    /**
     * <code>required string datetime_to_submit = 4;</code>
     */
    boolean hasDatetimeToSubmit();
    /**
     * <code>required string datetime_to_submit = 4;</code>
     */
    java.lang.String getDatetimeToSubmit();
    /**
     * <code>required string datetime_to_submit = 4;</code>
     */
    com.google.protobuf.ByteString
        getDatetimeToSubmitBytes();
  }
  /**
   * Protobuf type {@code me.yabble.common.proto.DelayedJob}
   */
  public static final class DelayedJob extends
      com.google.protobuf.GeneratedMessage
      implements DelayedJobOrBuilder {
    // Use DelayedJob.newBuilder() to construct.
    private DelayedJob(com.google.protobuf.GeneratedMessage.Builder<?> builder) {
      super(builder);
      this.unknownFields = builder.getUnknownFields();
    }
    private DelayedJob(boolean noInit) { this.unknownFields = com.google.protobuf.UnknownFieldSet.getDefaultInstance(); }

    private static final DelayedJob defaultInstance;
    public static DelayedJob getDefaultInstance() {
      return defaultInstance;
    }

    public DelayedJob getDefaultInstanceForType() {
      return defaultInstance;
    }

    private final com.google.protobuf.UnknownFieldSet unknownFields;
    @java.lang.Override
    public final com.google.protobuf.UnknownFieldSet
        getUnknownFields() {
      return this.unknownFields;
    }
    private DelayedJob(
        com.google.protobuf.CodedInputStream input,
        com.google.protobuf.ExtensionRegistryLite extensionRegistry)
        throws com.google.protobuf.InvalidProtocolBufferException {
      initFields();
      int mutable_bitField0_ = 0;
      com.google.protobuf.UnknownFieldSet.Builder unknownFields =
          com.google.protobuf.UnknownFieldSet.newBuilder();
      try {
        boolean done = false;
        while (!done) {
          int tag = input.readTag();
          switch (tag) {
            case 0:
              done = true;
              break;
            default: {
              if (!parseUnknownField(input, unknownFields,
                                     extensionRegistry, tag)) {
                done = true;
              }
              break;
            }
            case 8: {
              bitField0_ |= 0x00000001;
              id_ = input.readInt64();
              break;
            }
            case 16: {
              bitField0_ |= 0x00000002;
              itemId_ = input.readInt64();
              break;
            }
            case 26: {
              bitField0_ |= 0x00000004;
              qname_ = input.readBytes();
              break;
            }
            case 34: {
              bitField0_ |= 0x00000008;
              datetimeToSubmit_ = input.readBytes();
              break;
            }
          }
        }
      } catch (com.google.protobuf.InvalidProtocolBufferException e) {
        throw e.setUnfinishedMessage(this);
      } catch (java.io.IOException e) {
        throw new com.google.protobuf.InvalidProtocolBufferException(
            e.getMessage()).setUnfinishedMessage(this);
      } finally {
        this.unknownFields = unknownFields.build();
        makeExtensionsImmutable();
      }
    }
    public static final com.google.protobuf.Descriptors.Descriptor
        getDescriptor() {
      return me.yabble.common.proto.CommonProtos.internal_static_me_yabble_common_proto_DelayedJob_descriptor;
    }

    protected com.google.protobuf.GeneratedMessage.FieldAccessorTable
        internalGetFieldAccessorTable() {
      return me.yabble.common.proto.CommonProtos.internal_static_me_yabble_common_proto_DelayedJob_fieldAccessorTable
          .ensureFieldAccessorsInitialized(
              me.yabble.common.proto.CommonProtos.DelayedJob.class, me.yabble.common.proto.CommonProtos.DelayedJob.Builder.class);
    }

    public static com.google.protobuf.Parser<DelayedJob> PARSER =
        new com.google.protobuf.AbstractParser<DelayedJob>() {
      public DelayedJob parsePartialFrom(
          com.google.protobuf.CodedInputStream input,
          com.google.protobuf.ExtensionRegistryLite extensionRegistry)
          throws com.google.protobuf.InvalidProtocolBufferException {
        return new DelayedJob(input, extensionRegistry);
      }
    };

    @java.lang.Override
    public com.google.protobuf.Parser<DelayedJob> getParserForType() {
      return PARSER;
    }

    private int bitField0_;
    // required int64 id = 1;
    public static final int ID_FIELD_NUMBER = 1;
    private long id_;
    /**
     * <code>required int64 id = 1;</code>
     */
    public boolean hasId() {
      return ((bitField0_ & 0x00000001) == 0x00000001);
    }
    /**
     * <code>required int64 id = 1;</code>
     */
    public long getId() {
      return id_;
    }

    // required int64 item_id = 2;
    public static final int ITEM_ID_FIELD_NUMBER = 2;
    private long itemId_;
    /**
     * <code>required int64 item_id = 2;</code>
     */
    public boolean hasItemId() {
      return ((bitField0_ & 0x00000002) == 0x00000002);
    }
    /**
     * <code>required int64 item_id = 2;</code>
     */
    public long getItemId() {
      return itemId_;
    }

    // required string qname = 3;
    public static final int QNAME_FIELD_NUMBER = 3;
    private java.lang.Object qname_;
    /**
     * <code>required string qname = 3;</code>
     */
    public boolean hasQname() {
      return ((bitField0_ & 0x00000004) == 0x00000004);
    }
    /**
     * <code>required string qname = 3;</code>
     */
    public java.lang.String getQname() {
      java.lang.Object ref = qname_;
      if (ref instanceof java.lang.String) {
        return (java.lang.String) ref;
      } else {
        com.google.protobuf.ByteString bs = 
            (com.google.protobuf.ByteString) ref;
        java.lang.String s = bs.toStringUtf8();
        if (bs.isValidUtf8()) {
          qname_ = s;
        }
        return s;
      }
    }
    /**
     * <code>required string qname = 3;</code>
     */
    public com.google.protobuf.ByteString
        getQnameBytes() {
      java.lang.Object ref = qname_;
      if (ref instanceof java.lang.String) {
        com.google.protobuf.ByteString b = 
            com.google.protobuf.ByteString.copyFromUtf8(
                (java.lang.String) ref);
        qname_ = b;
        return b;
      } else {
        return (com.google.protobuf.ByteString) ref;
      }
    }

    // required string datetime_to_submit = 4;
    public static final int DATETIME_TO_SUBMIT_FIELD_NUMBER = 4;
    private java.lang.Object datetimeToSubmit_;
    /**
     * <code>required string datetime_to_submit = 4;</code>
     */
    public boolean hasDatetimeToSubmit() {
      return ((bitField0_ & 0x00000008) == 0x00000008);
    }
    /**
     * <code>required string datetime_to_submit = 4;</code>
     */
    public java.lang.String getDatetimeToSubmit() {
      java.lang.Object ref = datetimeToSubmit_;
      if (ref instanceof java.lang.String) {
        return (java.lang.String) ref;
      } else {
        com.google.protobuf.ByteString bs = 
            (com.google.protobuf.ByteString) ref;
        java.lang.String s = bs.toStringUtf8();
        if (bs.isValidUtf8()) {
          datetimeToSubmit_ = s;
        }
        return s;
      }
    }
    /**
     * <code>required string datetime_to_submit = 4;</code>
     */
    public com.google.protobuf.ByteString
        getDatetimeToSubmitBytes() {
      java.lang.Object ref = datetimeToSubmit_;
      if (ref instanceof java.lang.String) {
        com.google.protobuf.ByteString b = 
            com.google.protobuf.ByteString.copyFromUtf8(
                (java.lang.String) ref);
        datetimeToSubmit_ = b;
        return b;
      } else {
        return (com.google.protobuf.ByteString) ref;
      }
    }

    private void initFields() {
      id_ = 0L;
      itemId_ = 0L;
      qname_ = "";
      datetimeToSubmit_ = "";
    }
    private byte memoizedIsInitialized = -1;
    public final boolean isInitialized() {
      byte isInitialized = memoizedIsInitialized;
      if (isInitialized != -1) return isInitialized == 1;

      if (!hasId()) {
        memoizedIsInitialized = 0;
        return false;
      }
      if (!hasItemId()) {
        memoizedIsInitialized = 0;
        return false;
      }
      if (!hasQname()) {
        memoizedIsInitialized = 0;
        return false;
      }
      if (!hasDatetimeToSubmit()) {
        memoizedIsInitialized = 0;
        return false;
      }
      memoizedIsInitialized = 1;
      return true;
    }

    public void writeTo(com.google.protobuf.CodedOutputStream output)
                        throws java.io.IOException {
      getSerializedSize();
      if (((bitField0_ & 0x00000001) == 0x00000001)) {
        output.writeInt64(1, id_);
      }
      if (((bitField0_ & 0x00000002) == 0x00000002)) {
        output.writeInt64(2, itemId_);
      }
      if (((bitField0_ & 0x00000004) == 0x00000004)) {
        output.writeBytes(3, getQnameBytes());
      }
      if (((bitField0_ & 0x00000008) == 0x00000008)) {
        output.writeBytes(4, getDatetimeToSubmitBytes());
      }
      getUnknownFields().writeTo(output);
    }

    private int memoizedSerializedSize = -1;
    public int getSerializedSize() {
      int size = memoizedSerializedSize;
      if (size != -1) return size;

      size = 0;
      if (((bitField0_ & 0x00000001) == 0x00000001)) {
        size += com.google.protobuf.CodedOutputStream
          .computeInt64Size(1, id_);
      }
      if (((bitField0_ & 0x00000002) == 0x00000002)) {
        size += com.google.protobuf.CodedOutputStream
          .computeInt64Size(2, itemId_);
      }
      if (((bitField0_ & 0x00000004) == 0x00000004)) {
        size += com.google.protobuf.CodedOutputStream
          .computeBytesSize(3, getQnameBytes());
      }
      if (((bitField0_ & 0x00000008) == 0x00000008)) {
        size += com.google.protobuf.CodedOutputStream
          .computeBytesSize(4, getDatetimeToSubmitBytes());
      }
      size += getUnknownFields().getSerializedSize();
      memoizedSerializedSize = size;
      return size;
    }

    private static final long serialVersionUID = 0L;
    @java.lang.Override
    protected java.lang.Object writeReplace()
        throws java.io.ObjectStreamException {
      return super.writeReplace();
    }

    public static me.yabble.common.proto.CommonProtos.DelayedJob parseFrom(
        com.google.protobuf.ByteString data)
        throws com.google.protobuf.InvalidProtocolBufferException {
      return PARSER.parseFrom(data);
    }
    public static me.yabble.common.proto.CommonProtos.DelayedJob parseFrom(
        com.google.protobuf.ByteString data,
        com.google.protobuf.ExtensionRegistryLite extensionRegistry)
        throws com.google.protobuf.InvalidProtocolBufferException {
      return PARSER.parseFrom(data, extensionRegistry);
    }
    public static me.yabble.common.proto.CommonProtos.DelayedJob parseFrom(byte[] data)
        throws com.google.protobuf.InvalidProtocolBufferException {
      return PARSER.parseFrom(data);
    }
    public static me.yabble.common.proto.CommonProtos.DelayedJob parseFrom(
        byte[] data,
        com.google.protobuf.ExtensionRegistryLite extensionRegistry)
        throws com.google.protobuf.InvalidProtocolBufferException {
      return PARSER.parseFrom(data, extensionRegistry);
    }
    public static me.yabble.common.proto.CommonProtos.DelayedJob parseFrom(java.io.InputStream input)
        throws java.io.IOException {
      return PARSER.parseFrom(input);
    }
    public static me.yabble.common.proto.CommonProtos.DelayedJob parseFrom(
        java.io.InputStream input,
        com.google.protobuf.ExtensionRegistryLite extensionRegistry)
        throws java.io.IOException {
      return PARSER.parseFrom(input, extensionRegistry);
    }
    public static me.yabble.common.proto.CommonProtos.DelayedJob parseDelimitedFrom(java.io.InputStream input)
        throws java.io.IOException {
      return PARSER.parseDelimitedFrom(input);
    }
    public static me.yabble.common.proto.CommonProtos.DelayedJob parseDelimitedFrom(
        java.io.InputStream input,
        com.google.protobuf.ExtensionRegistryLite extensionRegistry)
        throws java.io.IOException {
      return PARSER.parseDelimitedFrom(input, extensionRegistry);
    }
    public static me.yabble.common.proto.CommonProtos.DelayedJob parseFrom(
        com.google.protobuf.CodedInputStream input)
        throws java.io.IOException {
      return PARSER.parseFrom(input);
    }
    public static me.yabble.common.proto.CommonProtos.DelayedJob parseFrom(
        com.google.protobuf.CodedInputStream input,
        com.google.protobuf.ExtensionRegistryLite extensionRegistry)
        throws java.io.IOException {
      return PARSER.parseFrom(input, extensionRegistry);
    }

    public static Builder newBuilder() { return Builder.create(); }
    public Builder newBuilderForType() { return newBuilder(); }
    public static Builder newBuilder(me.yabble.common.proto.CommonProtos.DelayedJob prototype) {
      return newBuilder().mergeFrom(prototype);
    }
    public Builder toBuilder() { return newBuilder(this); }

    @java.lang.Override
    protected Builder newBuilderForType(
        com.google.protobuf.GeneratedMessage.BuilderParent parent) {
      Builder builder = new Builder(parent);
      return builder;
    }
    /**
     * Protobuf type {@code me.yabble.common.proto.DelayedJob}
     */
    public static final class Builder extends
        com.google.protobuf.GeneratedMessage.Builder<Builder>
       implements me.yabble.common.proto.CommonProtos.DelayedJobOrBuilder {
      public static final com.google.protobuf.Descriptors.Descriptor
          getDescriptor() {
        return me.yabble.common.proto.CommonProtos.internal_static_me_yabble_common_proto_DelayedJob_descriptor;
      }

      protected com.google.protobuf.GeneratedMessage.FieldAccessorTable
          internalGetFieldAccessorTable() {
        return me.yabble.common.proto.CommonProtos.internal_static_me_yabble_common_proto_DelayedJob_fieldAccessorTable
            .ensureFieldAccessorsInitialized(
                me.yabble.common.proto.CommonProtos.DelayedJob.class, me.yabble.common.proto.CommonProtos.DelayedJob.Builder.class);
      }

      // Construct using me.yabble.common.proto.CommonProtos.DelayedJob.newBuilder()
      private Builder() {
        maybeForceBuilderInitialization();
      }

      private Builder(
          com.google.protobuf.GeneratedMessage.BuilderParent parent) {
        super(parent);
        maybeForceBuilderInitialization();
      }
      private void maybeForceBuilderInitialization() {
        if (com.google.protobuf.GeneratedMessage.alwaysUseFieldBuilders) {
        }
      }
      private static Builder create() {
        return new Builder();
      }

      public Builder clear() {
        super.clear();
        id_ = 0L;
        bitField0_ = (bitField0_ & ~0x00000001);
        itemId_ = 0L;
        bitField0_ = (bitField0_ & ~0x00000002);
        qname_ = "";
        bitField0_ = (bitField0_ & ~0x00000004);
        datetimeToSubmit_ = "";
        bitField0_ = (bitField0_ & ~0x00000008);
        return this;
      }

      public Builder clone() {
        return create().mergeFrom(buildPartial());
      }

      public com.google.protobuf.Descriptors.Descriptor
          getDescriptorForType() {
        return me.yabble.common.proto.CommonProtos.internal_static_me_yabble_common_proto_DelayedJob_descriptor;
      }

      public me.yabble.common.proto.CommonProtos.DelayedJob getDefaultInstanceForType() {
        return me.yabble.common.proto.CommonProtos.DelayedJob.getDefaultInstance();
      }

      public me.yabble.common.proto.CommonProtos.DelayedJob build() {
        me.yabble.common.proto.CommonProtos.DelayedJob result = buildPartial();
        if (!result.isInitialized()) {
          throw newUninitializedMessageException(result);
        }
        return result;
      }

      public me.yabble.common.proto.CommonProtos.DelayedJob buildPartial() {
        me.yabble.common.proto.CommonProtos.DelayedJob result = new me.yabble.common.proto.CommonProtos.DelayedJob(this);
        int from_bitField0_ = bitField0_;
        int to_bitField0_ = 0;
        if (((from_bitField0_ & 0x00000001) == 0x00000001)) {
          to_bitField0_ |= 0x00000001;
        }
        result.id_ = id_;
        if (((from_bitField0_ & 0x00000002) == 0x00000002)) {
          to_bitField0_ |= 0x00000002;
        }
        result.itemId_ = itemId_;
        if (((from_bitField0_ & 0x00000004) == 0x00000004)) {
          to_bitField0_ |= 0x00000004;
        }
        result.qname_ = qname_;
        if (((from_bitField0_ & 0x00000008) == 0x00000008)) {
          to_bitField0_ |= 0x00000008;
        }
        result.datetimeToSubmit_ = datetimeToSubmit_;
        result.bitField0_ = to_bitField0_;
        onBuilt();
        return result;
      }

      public Builder mergeFrom(com.google.protobuf.Message other) {
        if (other instanceof me.yabble.common.proto.CommonProtos.DelayedJob) {
          return mergeFrom((me.yabble.common.proto.CommonProtos.DelayedJob)other);
        } else {
          super.mergeFrom(other);
          return this;
        }
      }

      public Builder mergeFrom(me.yabble.common.proto.CommonProtos.DelayedJob other) {
        if (other == me.yabble.common.proto.CommonProtos.DelayedJob.getDefaultInstance()) return this;
        if (other.hasId()) {
          setId(other.getId());
        }
        if (other.hasItemId()) {
          setItemId(other.getItemId());
        }
        if (other.hasQname()) {
          bitField0_ |= 0x00000004;
          qname_ = other.qname_;
          onChanged();
        }
        if (other.hasDatetimeToSubmit()) {
          bitField0_ |= 0x00000008;
          datetimeToSubmit_ = other.datetimeToSubmit_;
          onChanged();
        }
        this.mergeUnknownFields(other.getUnknownFields());
        return this;
      }

      public final boolean isInitialized() {
        if (!hasId()) {
          
          return false;
        }
        if (!hasItemId()) {
          
          return false;
        }
        if (!hasQname()) {
          
          return false;
        }
        if (!hasDatetimeToSubmit()) {
          
          return false;
        }
        return true;
      }

      public Builder mergeFrom(
          com.google.protobuf.CodedInputStream input,
          com.google.protobuf.ExtensionRegistryLite extensionRegistry)
          throws java.io.IOException {
        me.yabble.common.proto.CommonProtos.DelayedJob parsedMessage = null;
        try {
          parsedMessage = PARSER.parsePartialFrom(input, extensionRegistry);
        } catch (com.google.protobuf.InvalidProtocolBufferException e) {
          parsedMessage = (me.yabble.common.proto.CommonProtos.DelayedJob) e.getUnfinishedMessage();
          throw e;
        } finally {
          if (parsedMessage != null) {
            mergeFrom(parsedMessage);
          }
        }
        return this;
      }
      private int bitField0_;

      // required int64 id = 1;
      private long id_ ;
      /**
       * <code>required int64 id = 1;</code>
       */
      public boolean hasId() {
        return ((bitField0_ & 0x00000001) == 0x00000001);
      }
      /**
       * <code>required int64 id = 1;</code>
       */
      public long getId() {
        return id_;
      }
      /**
       * <code>required int64 id = 1;</code>
       */
      public Builder setId(long value) {
        bitField0_ |= 0x00000001;
        id_ = value;
        onChanged();
        return this;
      }
      /**
       * <code>required int64 id = 1;</code>
       */
      public Builder clearId() {
        bitField0_ = (bitField0_ & ~0x00000001);
        id_ = 0L;
        onChanged();
        return this;
      }

      // required int64 item_id = 2;
      private long itemId_ ;
      /**
       * <code>required int64 item_id = 2;</code>
       */
      public boolean hasItemId() {
        return ((bitField0_ & 0x00000002) == 0x00000002);
      }
      /**
       * <code>required int64 item_id = 2;</code>
       */
      public long getItemId() {
        return itemId_;
      }
      /**
       * <code>required int64 item_id = 2;</code>
       */
      public Builder setItemId(long value) {
        bitField0_ |= 0x00000002;
        itemId_ = value;
        onChanged();
        return this;
      }
      /**
       * <code>required int64 item_id = 2;</code>
       */
      public Builder clearItemId() {
        bitField0_ = (bitField0_ & ~0x00000002);
        itemId_ = 0L;
        onChanged();
        return this;
      }

      // required string qname = 3;
      private java.lang.Object qname_ = "";
      /**
       * <code>required string qname = 3;</code>
       */
      public boolean hasQname() {
        return ((bitField0_ & 0x00000004) == 0x00000004);
      }
      /**
       * <code>required string qname = 3;</code>
       */
      public java.lang.String getQname() {
        java.lang.Object ref = qname_;
        if (!(ref instanceof java.lang.String)) {
          java.lang.String s = ((com.google.protobuf.ByteString) ref)
              .toStringUtf8();
          qname_ = s;
          return s;
        } else {
          return (java.lang.String) ref;
        }
      }
      /**
       * <code>required string qname = 3;</code>
       */
      public com.google.protobuf.ByteString
          getQnameBytes() {
        java.lang.Object ref = qname_;
        if (ref instanceof String) {
          com.google.protobuf.ByteString b = 
              com.google.protobuf.ByteString.copyFromUtf8(
                  (java.lang.String) ref);
          qname_ = b;
          return b;
        } else {
          return (com.google.protobuf.ByteString) ref;
        }
      }
      /**
       * <code>required string qname = 3;</code>
       */
      public Builder setQname(
          java.lang.String value) {
        if (value == null) {
    throw new NullPointerException();
  }
  bitField0_ |= 0x00000004;
        qname_ = value;
        onChanged();
        return this;
      }
      /**
       * <code>required string qname = 3;</code>
       */
      public Builder clearQname() {
        bitField0_ = (bitField0_ & ~0x00000004);
        qname_ = getDefaultInstance().getQname();
        onChanged();
        return this;
      }
      /**
       * <code>required string qname = 3;</code>
       */
      public Builder setQnameBytes(
          com.google.protobuf.ByteString value) {
        if (value == null) {
    throw new NullPointerException();
  }
  bitField0_ |= 0x00000004;
        qname_ = value;
        onChanged();
        return this;
      }

      // required string datetime_to_submit = 4;
      private java.lang.Object datetimeToSubmit_ = "";
      /**
       * <code>required string datetime_to_submit = 4;</code>
       */
      public boolean hasDatetimeToSubmit() {
        return ((bitField0_ & 0x00000008) == 0x00000008);
      }
      /**
       * <code>required string datetime_to_submit = 4;</code>
       */
      public java.lang.String getDatetimeToSubmit() {
        java.lang.Object ref = datetimeToSubmit_;
        if (!(ref instanceof java.lang.String)) {
          java.lang.String s = ((com.google.protobuf.ByteString) ref)
              .toStringUtf8();
          datetimeToSubmit_ = s;
          return s;
        } else {
          return (java.lang.String) ref;
        }
      }
      /**
       * <code>required string datetime_to_submit = 4;</code>
       */
      public com.google.protobuf.ByteString
          getDatetimeToSubmitBytes() {
        java.lang.Object ref = datetimeToSubmit_;
        if (ref instanceof String) {
          com.google.protobuf.ByteString b = 
              com.google.protobuf.ByteString.copyFromUtf8(
                  (java.lang.String) ref);
          datetimeToSubmit_ = b;
          return b;
        } else {
          return (com.google.protobuf.ByteString) ref;
        }
      }
      /**
       * <code>required string datetime_to_submit = 4;</code>
       */
      public Builder setDatetimeToSubmit(
          java.lang.String value) {
        if (value == null) {
    throw new NullPointerException();
  }
  bitField0_ |= 0x00000008;
        datetimeToSubmit_ = value;
        onChanged();
        return this;
      }
      /**
       * <code>required string datetime_to_submit = 4;</code>
       */
      public Builder clearDatetimeToSubmit() {
        bitField0_ = (bitField0_ & ~0x00000008);
        datetimeToSubmit_ = getDefaultInstance().getDatetimeToSubmit();
        onChanged();
        return this;
      }
      /**
       * <code>required string datetime_to_submit = 4;</code>
       */
      public Builder setDatetimeToSubmitBytes(
          com.google.protobuf.ByteString value) {
        if (value == null) {
    throw new NullPointerException();
  }
  bitField0_ |= 0x00000008;
        datetimeToSubmit_ = value;
        onChanged();
        return this;
      }

      // @@protoc_insertion_point(builder_scope:me.yabble.common.proto.DelayedJob)
    }

    static {
      defaultInstance = new DelayedJob(true);
      defaultInstance.initFields();
    }

    // @@protoc_insertion_point(class_scope:me.yabble.common.proto.DelayedJob)
  }

  private static com.google.protobuf.Descriptors.Descriptor
    internal_static_me_yabble_common_proto_DelayedJob_descriptor;
  private static
    com.google.protobuf.GeneratedMessage.FieldAccessorTable
      internal_static_me_yabble_common_proto_DelayedJob_fieldAccessorTable;

  public static com.google.protobuf.Descriptors.FileDescriptor
      getDescriptor() {
    return descriptor;
  }
  private static com.google.protobuf.Descriptors.FileDescriptor
      descriptor;
  static {
    java.lang.String[] descriptorData = {
      "\n\014common.proto\022\026me.yabble.common.proto\"T" +
      "\n\nDelayedJob\022\n\n\002id\030\001 \002(\003\022\017\n\007item_id\030\002 \002(" +
      "\003\022\r\n\005qname\030\003 \002(\t\022\032\n\022datetime_to_submit\030\004" +
      " \002(\tB&\n\026me.yabble.common.protoB\014CommonPr" +
      "otos"
    };
    com.google.protobuf.Descriptors.FileDescriptor.InternalDescriptorAssigner assigner =
      new com.google.protobuf.Descriptors.FileDescriptor.InternalDescriptorAssigner() {
        public com.google.protobuf.ExtensionRegistry assignDescriptors(
            com.google.protobuf.Descriptors.FileDescriptor root) {
          descriptor = root;
          internal_static_me_yabble_common_proto_DelayedJob_descriptor =
            getDescriptor().getMessageTypes().get(0);
          internal_static_me_yabble_common_proto_DelayedJob_fieldAccessorTable = new
            com.google.protobuf.GeneratedMessage.FieldAccessorTable(
              internal_static_me_yabble_common_proto_DelayedJob_descriptor,
              new java.lang.String[] { "Id", "ItemId", "Qname", "DatetimeToSubmit", });
          return null;
        }
      };
    com.google.protobuf.Descriptors.FileDescriptor
      .internalBuildGeneratedFileFrom(descriptorData,
        new com.google.protobuf.Descriptors.FileDescriptor[] {
        }, assigner);
  }

  // @@protoc_insertion_point(outer_class_scope)
}
