/**
 * Copyright 2005-2010 Noelios Technologies.
 * 
 * The contents of this file are subject to the terms of one of the following
 * open source licenses: LGPL 3.0 or LGPL 2.1 or CDDL 1.0 or EPL 1.0 (the
 * "Licenses"). You can select the license that you prefer but you may not use
 * this file except in compliance with one of these Licenses.
 * 
 * You can obtain a copy of the LGPL 3.0 license at
 * http://www.opensource.org/licenses/lgpl-3.0.html
 * 
 * You can obtain a copy of the LGPL 2.1 license at
 * http://www.opensource.org/licenses/lgpl-2.1.php
 * 
 * You can obtain a copy of the CDDL 1.0 license at
 * http://www.opensource.org/licenses/cddl1.php
 * 
 * You can obtain a copy of the EPL 1.0 license at
 * http://www.opensource.org/licenses/eclipse-1.0.php
 * 
 * See the Licenses for the specific language governing permissions and
 * limitations under the Licenses.
 * 
 * Alternatively, you can obtain a royalty free commercial license with less
 * limitations, transferable or non-transferable, directly at
 * http://www.noelios.com/products/restlet-engine
 * 
 * Restlet is a registered trademark of Noelios Technologies.
 */

package org.restlet.engine.nio;

import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.logging.Level;

import org.restlet.Message;
import org.restlet.Request;
import org.restlet.Response;
import org.restlet.data.Form;
import org.restlet.data.Parameter;
import org.restlet.data.Protocol;
import org.restlet.engine.http.header.HeaderConstants;
import org.restlet.engine.http.header.HeaderUtils;
import org.restlet.engine.util.StringUtils;
import org.restlet.representation.FileRepresentation;
import org.restlet.representation.ReadableRepresentation;
import org.restlet.representation.Representation;
import org.restlet.util.Series;

/**
 * A network connection way though which messages are sent. Messages can be
 * either requests or responses.
 * 
 * @author Jerome Louvel
 */
public abstract class OutboundWay extends Way {

    /**
     * Returns the protocol version.
     * 
     * @param request
     * @return
     */
    protected static String getVersion(Request request) {
        Protocol protocol = request.getProtocol();
        String protocolVersion = protocol.getVersion();
        return protocol.getTechnicalName() + '/'
                + ((protocolVersion == null) ? "1.1" : protocolVersion);
    }

    /** The entity as a NIO readable byte channel. */
    private volatile ReadableByteChannel entityChannel;

    /** The entity index. */
    private volatile long entityIndex;

    /**
     * The entity's NIO selection key holding the link between the entity to be
     * written and the way.
     */
    private volatile SelectionKey entityKey;

    /** The entity as a BIO input stream. */
    private volatile InputStream entityStream;

    /** The entity type. */
    private volatile EntityType entityType;

    /** The header index. */
    private volatile int headerIndex;

    /** The message headers. */
    private volatile Series<Parameter> headers;

    /**
     * Constructor.
     * 
     * @param connection
     */
    public OutboundWay(Connection<?> connection) {
        super(connection);
        this.entityChannel = null;
        this.entityStream = null;
        this.entityKey = null;
        this.entityIndex = 0;
        this.headerIndex = 0;
        this.headers = null;
    }

    /**
     * Adds the entity headers for the given response.
     * 
     * @param entity
     *            The entity to inspect.
     */
    protected void addEntityHeaders(Representation entity,
            Series<Parameter> headers) {
        HeaderUtils.addEntityHeaders(entity, headers);
    }

    /**
     * Adds the general headers from the {@link Message} to the {@link Series}.
     * 
     * @param headers
     *            The target headers {@link Series}.
     */
    protected void addGeneralHeaders(Series<Parameter> headers) {
        if (!getConnection().isPersistent()) {
            headers.set(HeaderConstants.HEADER_CONNECTION, "close", true);
        }

        if (shouldBeChunked(getMessage().getEntity())) {
            headers.add(HeaderConstants.HEADER_TRANSFER_ENCODING, "chunked");
        }

        HeaderUtils.addGeneralHeaders(getMessage(), headers);
    }

    /**
     * Add all the headers, including the general, the message specific and the
     * entity headers.
     * 
     * @param headers
     *            The headers to update.
     */
    protected abstract void addHeaders(Series<Parameter> headers);

    /**
     * Returns the entity as a NIO readable byte channel.
     * 
     * @return The entity as a NIO readable byte channel.
     */
    public ReadableByteChannel getEntityChannel() {
        return entityChannel;
    }

    /**
     * Returns the entity as a NIO file channel.
     * 
     * @return The entity as a NIO file channel.
     */
    public FileChannel getEntityFileChannel() {
        return (FileChannel) getEntityChannel();
    }

    /**
     * Returns the entity index.
     * 
     * @return The entity index.
     */
    public long getEntityIndex() {
        return entityIndex;
    }

    /**
     * Registers interest of this way for socket NIO operations.
     * 
     * @return The operations of interest.
     */
    public int getEntityInterestOps() {
        int result = 0;

        if (getIoState() == IoState.INTEREST) {
            result = SelectionKey.OP_READ;
        }

        return result;
    }

    /**
     * Returns the entity's NIO selection key holding the link between the
     * entity to be written and the way.
     * 
     * @return The entity's NIO selection key.
     */
    public SelectionKey getEntityKey() {
        return entityKey;
    }

    /**
     * Returns the entity as a NIO non-blocking selectable channel.
     * 
     * @return The entity as a NIO non-blocking selectable channel.
     */
    public SelectableChannel getEntitySelectableChannel() {
        return (SelectableChannel) getEntityChannel();
    }

    /**
     * Returns the entity as a BIO input stream.
     * 
     * @return The entity as a BIO input stream.
     */
    public InputStream getEntityStream() {
        return entityStream;
    }

    /**
     * Returns the entity type.
     * 
     * @return The entity type.
     */
    public EntityType getEntityType() {
        return entityType;
    }

    /**
     * Returns the header index.
     * 
     * @return The header index.
     */
    protected int getHeaderIndex() {
        return headerIndex;
    }

    /**
     * Returns the response headers.
     * 
     * @return The response headers to be written.
     */
    public Series<Parameter> getHeaders() {
        return headers;
    }

    @Override
    public int getSocketInterestOps() {
        int result = 0;

        if (getIoState() == IoState.INTEREST) {
            result = SelectionKey.OP_WRITE;
        }

        return result;
    }

    @Override
    protected void onCompleted(Response message) {
        super.onCompleted(message);
        setHeaders(null);
        setHeaderIndex(0);
        setEntityIndex(0);

        if (getLogger().isLoggable(Level.FINER)) {
            getLogger().finer("Outbound message sent");
        }
    }

    @Override
    public void onSelected() {
        super.onSelected();

        try {
            Response message = getMessage();

            if (message != null) {
                while (getIoState() == IoState.PROCESSING) {
                    if (isFilling()) {
                        // Before writing the byte buffer, we need to try
                        // to fill it as much as possible

                        if (getMessageState() == MessageState.BODY) {
                            // Writing the body doesn't rely on the line builder
                            switch (getEntityType()) {
                            case ASYNC_CHANNEL:

                                break;

                            case SYNC_CHANNEL:

                                break;

                            case FILE_CHANNEL:

                                break;

                            case STREAM:
                                if (getByteBuffer().hasArray()) {
                                    byte[] byteArray = getByteBuffer().array();
                                    int available = getEntityStream()
                                            .available();

                                    if (available > 0) {
                                        long entitySize = getMessage()
                                                .getEntity().getSize();

                                        // Non-blocking read guaranteed
                                        int result = getEntityStream().read(
                                                byteArray,
                                                getByteBuffer().position(),
                                                available);

                                        if (result > 0) {
                                            getByteBuffer().position(
                                                    getByteBuffer().position()
                                                            + result);
                                            setEntityIndex(getEntityIndex()
                                                    + result);
                                        } else if (result == -1) {
                                            getByteBuffer().position(
                                                    getByteBuffer().position()
                                                            + available);
                                            setEntityIndex(getEntityIndex()
                                                    + available);
                                        }

                                        // Detect end of entity reached
                                        if ((result == -1)
                                                || ((entitySize != -1) && (getEntityIndex() >= entitySize))) {
                                            setMessageState(MessageState.IDLE);
                                        }
                                    } else {
                                        // Blocking read, need to launch a new
                                        // thread...
                                    }
                                } else {
                                    System.out
                                            .println("No underlying byte array for the NIO byte buffer!");
                                }
                                break;
                            }
                        } else {
                            // Write the start line or the headers relies on
                            // the line builder
                            if (getLineBuilder().length() == 0) {
                                // A new line can be written in the builder
                                writeLine();
                            }

                            if (getLineBuilder().length() > 0) {
                                // We can fill the byte buffer with the
                                // remaining line builder
                                int remaining = getByteBuffer().remaining();

                                if (remaining >= getLineBuilder().length()) {
                                    // Put the whole builder line in the
                                    // buffer
                                    getByteBuffer()
                                            .put(StringUtils
                                                    .getLatin1Bytes(getLineBuilder()
                                                            .toString()));
                                    getLineBuilder().delete(0,
                                            getLineBuilder().length());
                                } else {
                                    // Put the maximum number of characters
                                    // into the byte buffer
                                    getByteBuffer()
                                            .put(StringUtils
                                                    .getLatin1Bytes(getLineBuilder()
                                                            .substring(0,
                                                                    remaining)));
                                    getLineBuilder().delete(0, remaining);
                                }
                            }
                        }
                    } else {
                        // After filling the byte buffer, we can now flip it
                        // and start draining it.
                        getByteBuffer().flip();
                        int bytesWritten = getConnection()
                                .getWritableSelectionChannel().write(
                                        getByteBuffer());

                        if (bytesWritten == 0) {
                            // The byte buffer hasn't been written, the socket
                            // channel can't write more. We needs to put the
                            // byte buffer in the filling state again and
                            // wait for a new NIO selection.
                            getByteBuffer().flip();
                            setIoState(IoState.INTEREST);
                        } else if (getByteBuffer().hasRemaining()) {
                            // All the buffer couldn't be written. Compact the
                            // remaining bytes so that filling can happen again.
                            getByteBuffer().compact();
                        } else if (getMessageState() == MessageState.IDLE) {
                            // Message fully sent, ready for a new one
                            onCompleted(getMessage());
                        } else {
                            // The byte buffer has been fully written, but
                            // the socket channel wants more.
                        }
                    }
                }
            }
        } catch (Exception e) {
            getLogger().log(Level.WARNING,
                    "Error while writing an HTTP message: ", e.getMessage());
            getLogger().log(Level.INFO, "Error while writing an HTTP message",
                    e);
        }
    }

    @Override
    public void registerInterest(Selector selector) {
        super.registerInterest(selector);

        // If the entity is available as a non-blocking selectable channel,
        // register it as well
        SelectableChannel entitySelectableChannel = getEntitySelectableChannel();

        if (entitySelectableChannel != null) {
            int entityInterestOps = getEntityInterestOps();

            if ((getEntityKey() == null) && (entityInterestOps > 0)) {
                try {
                    setEntityKey(entitySelectableChannel.register(selector,
                            entityInterestOps, new SelectionListener() {

                                public void onSelected(SelectionKey key) {
                                    // TODO
                                }

                            }));
                } catch (ClosedChannelException cce) {
                    getLogger()
                            .log(Level.WARNING,
                                    "Unable to register NIO interest operations for this entity",
                                    cce);
                    getConnection().onError();
                }
            } else {
                getEntityKey().interestOps(entityInterestOps);
            }
        }
    }

    /**
     * Sets the entity as a NIO readable byte channel.
     * 
     * @param entityChannel
     *            The entity as a NIO readable byte channel.
     */
    public void setEntityChannel(ReadableByteChannel entityChannel) {
        this.entityChannel = entityChannel;
    }

    /**
     * Sets the entity index.
     * 
     * @param entityIndex
     *            The entity index.
     */
    protected void setEntityIndex(long entityIndex) {
        this.entityIndex = entityIndex;
    }

    /**
     * Sets the entity's NIO selection key holding the link between the entity
     * to be written and the way.
     * 
     * @param entityKey
     *            The entity's NIO selection key.
     */
    public void setEntityKey(SelectionKey entityKey) {
        this.entityKey = entityKey;
    }

    /**
     * Sets the entity as a BIO input stream.
     * 
     * @param entityStream
     *            The entity as a BIO input stream.
     */
    public void setEntityStream(InputStream entityStream) {
        this.entityStream = entityStream;
    }

    /**
     * Sets the entity type.
     * 
     * @param entityType
     *            The entity type.
     */
    public void setEntityType(EntityType entityType) {
        this.entityType = entityType;
    }

    /**
     * Sets the header index.
     * 
     * @param headerIndex
     *            The header index.
     */
    protected void setHeaderIndex(int headerIndex) {
        this.headerIndex = headerIndex;
    }

    /**
     * Sets the response headers to be written.
     * 
     * @param headers
     *            The response headers.
     */
    public void setHeaders(Series<Parameter> headers) {
        this.headers = headers;
    }

    /**
     * Indicates if the entity should be chunked because its length is unknown.
     * 
     * @param entity
     *            The entity to analyze.
     * @return True if the entity should be chunked.
     */
    protected boolean shouldBeChunked(Representation entity) {
        return (entity != null)
                && (entity.getSize() == Representation.UNKNOWN_SIZE);
    }

    @Override
    public void updateState() {
        // Update the IO state if necessary
        if ((getIoState() == IoState.IDLE) && !getMessages().isEmpty()) {
            if (getMessage() == null) {
                setIoState(IoState.INTEREST);
                setMessage(getMessages().peek());
            }
        }
    }

    /**
     * Write a new line into the line builder.
     * 
     * @throws IOException
     */
    protected void writeLine() throws IOException {
        switch (getMessageState()) {
        case START_LINE:
            writeStartLine();
            setMessageState(MessageState.HEADERS);
            break;

        case HEADERS:
            if (getHeaders() == null) {
                setHeaders(new Form());
                setHeaderIndex(0);
                addHeaders(getHeaders());
            }

            if (getHeaderIndex() < getHeaders().size()) {
                // Write header
                Parameter header = getHeaders().get(getHeaderIndex());
                getLineBuilder().append(header.getName());
                getLineBuilder().append(": ");
                getLineBuilder().append(header.getValue());
                getLineBuilder().append('\r'); // CR
                getLineBuilder().append('\n'); // LF

                // Move to the next header
                setHeaderIndex(getHeaderIndex() + 1);
            } else {
                // Write the end of the headers section
                getLineBuilder().append('\r'); // CR
                getLineBuilder().append('\n'); // LF

                // Prepare entity writing if available
                if (getMessage().isEntityAvailable()) {
                    setMessageState(MessageState.BODY);

                    if (getMessage().getEntity() instanceof FileRepresentation) {
                        FileRepresentation fr = (FileRepresentation) getMessage()
                                .getEntity();
                        setEntityChannel(fr.getChannel());
                        setEntityType(EntityType.FILE_CHANNEL);
                    } else if (getMessage().getEntity() instanceof ReadableRepresentation) {
                        ReadableRepresentation rr = (ReadableRepresentation) getMessage()
                                .getEntity();
                        setEntityChannel(rr.getChannel());
                        setEntityType(EntityType.SYNC_CHANNEL);

                        if (getEntityChannel() instanceof SelectableChannel) {
                            SelectableChannel sc = (SelectableChannel) getEntityChannel();

                            if (!sc.isBlocking()) {
                                setEntityType(EntityType.ASYNC_CHANNEL);
                            }
                        }
                    } else {
                        setEntityStream(getMessage().getEntity().getStream());
                        setEntityType(EntityType.STREAM);
                    }
                } else {
                    onCompleted(getMessage());
                }
            }

            break;
        }
    }

    /**
     * Writes the start line of the current outbound message.
     * 
     * @throws IOException
     */
    protected abstract void writeStartLine() throws IOException;

}