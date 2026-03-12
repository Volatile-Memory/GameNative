package com.winlator.xserver.extensions;

import static com.winlator.xserver.XClientRequestHandler.RESPONSE_CODE_SUCCESS;

import com.winlator.xconnector.XInputStream;
import com.winlator.xconnector.XOutputStream;
import com.winlator.xconnector.XStreamLock;
import com.winlator.xserver.ScreenInfo;
import com.winlator.xserver.XClient;
import com.winlator.xserver.XServer;
import com.winlator.xserver.errors.XRequestError;
import com.winlator.xserver.events.Event;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Minimal RandR 1.0 extension.
 *
 * Supports enough of the RandR protocol for Wine's winex11.drv to:
 *   - detect the extension is present (RRQueryVersion)
 *   - register for screen-change events (RRSelectInput)
 *   - query the current screen info (RRGetScreenInfo)
 *   - receive RRScreenChangeNotify when the Android container is resized
 *
 * Wine subscribes via XRRSelectInput(root, RRScreenChangeNotifyMask) at startup
 * and reacts to RRScreenChangeNotify by updating the Windows display resolution.
 */
public class RandRExtension implements Extension {
    public static final byte MAJOR_OPCODE = -99;
    /** First X11 event code allocated to this extension (must not collide with MITSHM=64). */
    public static final byte FIRST_EVENT_ID = 80;

    private static final int RR_SCREEN_CHANGE_NOTIFY_MASK = 1;

    // RandR 1.0 minor opcodes
    private static final int OP_QUERY_VERSION     = 0;
    private static final int OP_SET_SCREEN_CONFIG = 2;
    private static final int OP_SELECT_INPUT      = 4;
    private static final int OP_GET_SCREEN_INFO   = 5;

    private final XServer xServer;

    /** Clients that called RRSelectInput with RRScreenChangeNotifyMask. */
    private final Map<XClient, Integer> subscribers = new ConcurrentHashMap<>();

    /** Monotonically increasing timestamp for config changes (seconds). */
    private int configTimestamp = (int) (System.currentTimeMillis() / 1000);

    public RandRExtension(XServer xServer) {
        this.xServer = xServer;
    }

    @Override public String getName()       { return "RANDR"; }
    @Override public byte getMajorOpcode()  { return MAJOR_OPCODE; }
    @Override public byte getFirstErrorId() { return 0; }
    @Override public byte getFirstEventId() { return FIRST_EVENT_ID; }

    @Override
    public void handleRequest(XClient client, XInputStream inputStream, XOutputStream outputStream)
            throws IOException, XRequestError {
        switch (client.getRequestData()) {
            case OP_QUERY_VERSION:
                queryVersion(client, inputStream, outputStream);
                break;
            case OP_SET_SCREEN_CONFIG:
                setScreenConfig(client, inputStream, outputStream);
                break;
            case OP_SELECT_INPUT:
                selectInput(client, inputStream);
                break;
            case OP_GET_SCREEN_INFO:
                getScreenInfo(client, inputStream, outputStream);
                break;
            default:
                client.skipRequest();
                break;
        }
    }

    // -------------------------------------------------------------------------
    // Request handlers
    // -------------------------------------------------------------------------

    /** RRQueryVersion: negotiate version. We advertise 1.0 to keep Wine on the safe path. */
    private void queryVersion(XClient client, XInputStream inputStream, XOutputStream outputStream)
            throws IOException {
        inputStream.skip(8); // clientMajorVersion(4) + clientMinorVersion(4)
        try (XStreamLock lock = outputStream.lock()) {
            outputStream.writeByte(RESPONSE_CODE_SUCCESS);
            outputStream.writeByte((byte) 0);
            outputStream.writeShort(client.getSequenceNumber());
            outputStream.writeInt(0);  // length (no extra data)
            outputStream.writeInt(1);  // majorVersion = 1
            outputStream.writeInt(0);  // minorVersion = 0
            outputStream.writePad(16);
        }
    }

    /**
     * RRSetScreenConfig: Wine may call this to request a resolution change.
     * We acknowledge with success but apply no change here; the Android side
     * drives resolution via GLRenderer.onSurfaceChanged.
     */
    private void setScreenConfig(XClient client, XInputStream inputStream, XOutputStream outputStream)
            throws IOException {
        client.skipRequest();
        int now = (int) (System.currentTimeMillis() / 1000);
        try (XStreamLock lock = outputStream.lock()) {
            outputStream.writeByte(RESPONSE_CODE_SUCCESS);
            outputStream.writeByte((byte) 0); // RRSetConfigSuccess
            outputStream.writeShort(client.getSequenceNumber());
            outputStream.writeInt(0);         // length
            outputStream.writeInt(now);       // newTimestamp
            outputStream.writeInt(configTimestamp);
            outputStream.writeInt(xServer.windowManager.rootWindow.id); // root
            outputStream.writeShort((short) 0); // subpixelOrder = SubPixelUnknown
            outputStream.writePad(18);
        }
    }

    /**
     * RRSelectInput: register this client to receive RRScreenChangeNotify events.
     * enable bitmask: bit 0 = RRScreenChangeNotifyMask.
     */
    private void selectInput(XClient client, XInputStream inputStream) throws IOException {
        inputStream.skip(4); // window (always root for screen changes)
        int enable = inputStream.readInt();
        if ((enable & RR_SCREEN_CHANGE_NOTIFY_MASK) != 0) {
            subscribers.put(client, enable);
        } else {
            subscribers.remove(client);
        }
        // No reply for RRSelectInput
    }

    /**
     * RRGetScreenInfo: return the single current screen mode.
     * Reply layout (32-byte header + nSizes * 8 bytes for ScreenSize structs):
     *   1  reply (1)
     *   1  setOfRotations (1 = RR_Rotate_0)
     *   2  sequenceNumber
     *   4  length (= nSizes * 2, in 4-byte units)
     *   4  root window
     *   4  timestamp
     *   4  configTimestamp
     *   2  nSizes
     *   2  sizeID (current = 0)
     *   2  rotation (1 = RR_Rotate_0)
     *   2  rate (Hz)
     *   2  nInfo (0: no per-size rate table in RandR 1.0)
     *   2  pad
     * + 8 bytes per ScreenSize: width(2) height(2) widthMM(2) heightMM(2)
     */
    private void getScreenInfo(XClient client, XInputStream inputStream, XOutputStream outputStream)
            throws IOException {
        inputStream.skip(4); // window
        ScreenInfo si = xServer.screenInfo;
        try (XStreamLock lock = outputStream.lock()) {
            outputStream.writeByte(RESPONSE_CODE_SUCCESS);
            outputStream.writeByte((byte) 1);       // setOfRotations = RR_Rotate_0
            outputStream.writeShort(client.getSequenceNumber());
            outputStream.writeInt(2);               // length: 1 ScreenSize * 8 bytes / 4 = 2
            outputStream.writeInt(xServer.windowManager.rootWindow.id);
            outputStream.writeInt(configTimestamp);
            outputStream.writeInt(configTimestamp);
            outputStream.writeShort((short) 1);     // nSizes = 1
            outputStream.writeShort((short) 0);     // sizeID = 0 (current)
            outputStream.writeShort((short) 1);     // rotation = RR_Rotate_0
            outputStream.writeShort((short) 60);    // rate = 60 Hz
            outputStream.writeShort((short) 0);     // nInfo = 0
            outputStream.writeShort((short) 0);     // pad
            // ScreenSize[0]
            outputStream.writeShort(si.width);
            outputStream.writeShort(si.height);
            outputStream.writeShort(si.getWidthInMillimeters());
            outputStream.writeShort(si.getHeightInMillimeters());
        }
    }

    // -------------------------------------------------------------------------
    // Screen change notification
    // -------------------------------------------------------------------------

    /**
     * Called by XServer.updateScreenSize() after the logical screen dimensions
     * have been updated. Sends RRScreenChangeNotify to all subscribed clients.
     */
    public void notifyScreenChange(short width, short height, short widthMM, short heightMM) {
        configTimestamp = (int) (System.currentTimeMillis() / 1000);
        ScreenChangeEvent event = new ScreenChangeEvent(
                FIRST_EVENT_ID, configTimestamp,
                xServer.windowManager.rootWindow.id,
                width, height, widthMM, heightMM);
        for (Map.Entry<XClient, Integer> entry : subscribers.entrySet()) {
            if ((entry.getValue() & RR_SCREEN_CHANGE_NOTIFY_MASK) != 0) {
                try {
                    entry.getKey().sendEvent(event);
                } catch (Exception ignored) {
                    subscribers.remove(entry.getKey());
                }
            }
        }
    }

    /** Remove a client from the subscriber list (call when client disconnects). */
    public void removeClient(XClient client) {
        subscribers.remove(client);
    }

    // -------------------------------------------------------------------------
    // RRScreenChangeNotify event (32 bytes)
    // -------------------------------------------------------------------------

    private static class ScreenChangeEvent extends Event {
        private final int timestamp;
        private final int rootId;
        private final short width, height, widthMM, heightMM;

        ScreenChangeEvent(int code, int timestamp, int rootId,
                          short width, short height, short widthMM, short heightMM) {
            super(code);
            this.timestamp = timestamp;
            this.rootId = rootId;
            this.width = width;
            this.height = height;
            this.widthMM = widthMM;
            this.heightMM = heightMM;
        }

        @Override
        public void send(short sequenceNumber, XOutputStream outputStream) throws IOException {
            try (XStreamLock lock = outputStream.lock()) {
                outputStream.writeByte(code);           // event type = firstEventId + 0
                outputStream.writeByte((byte) 1);       // rotation = RR_Rotate_0
                outputStream.writeShort(sequenceNumber);
                outputStream.writeInt(timestamp);       // timestamp
                outputStream.writeInt(timestamp);       // configTimestamp
                outputStream.writeInt(rootId);          // root window
                outputStream.writeInt(rootId);          // requestWindow (same as root for screen events)
                outputStream.writeShort((short) 0);     // sizeID = 0
                outputStream.writeShort((short) 0);     // subpixelOrder = SubPixelUnknown
                outputStream.writeShort(width);
                outputStream.writeShort(height);
                outputStream.writeShort(widthMM);
                outputStream.writeShort(heightMM);
            }
        }
    }
}
