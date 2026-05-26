package com.aaltay.musicnotifications;

import com.sun.jna.Function;
import com.sun.jna.Library;
import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.PointerByReference;
import com.sun.jna.win32.W32APIOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;

public class MediaManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("MediaManager");

    private static String currentArtist = "";
    private static String currentTitle = "";
    private static long lastChangeTime = 0;
    private static Pointer staticsPointer;

    public interface Combase extends Library {
        Combase INSTANCE = Native.load("combase", Combase.class, W32APIOptions.DEFAULT_OPTIONS);
        int RoInitialize(int initType);
        int WindowsCreateString(char[] sourceString, int length, PointerByReference hstring);
        Pointer WindowsGetStringRawBuffer(Pointer hstring, IntByReference length);
        int WindowsDeleteString(Pointer hstring);
        int RoGetActivationFactory(Pointer activatableClassId, Pointer iid, PointerByReference factory);
    }

    public static volatile boolean isRunning = false;

    private static final int VTABLE_OFFSET_GET_CURRENT_SESSION = 6;
    private static final int VTABLE_OFFSET_TRY_GET_MEDIA_PROPERTIES = 7;
    private static final int VTABLE_OFFSET_GET_RESULTS = 8;
    private static final int VTABLE_OFFSET_GET_TITLE = 6;
    private static final int VTABLE_OFFSET_GET_SUBTITLE = 7;
    private static final int VTABLE_OFFSET_GET_ALBUM_ARTIST = 8;
    private static final int VTABLE_OFFSET_GET_ARTIST = 9;
    private static final int VTABLE_OFFSET_REQUEST_ASYNC = 6;
    private static final int VTABLE_OFFSET_RELEASE = 2;
    private static final int ASYNC_WAIT_ATTEMPTS = 10;
    private static final int ASYNC_WAIT_MILLIS = 20;

    public static void startListening() {
        if (isRunning) return;
        isRunning = true;
        Thread.ofVirtual().name("SMTC-Listener").start(() -> {
            LOGGER.info("SMTC Listener thread started.");
            try {
                int roHr = Combase.INSTANCE.RoInitialize(1);
                LOGGER.info("RoInitialize result: {}", Integer.toHexString(roHr));
            } catch (Throwable t) {
                LOGGER.error("CRITICAL SMTC Init Failure: ", t);
                return;
            }

            byte[] iidBytes = new byte[] {
                (byte)0xEE, (byte)0xC4, (byte)0x50, (byte)0x20,
                (byte)0xA0, (byte)0x11,
                (byte)0xDE, (byte)0x57,
                (byte)0xAE, (byte)0xD7,
                (byte)0xC9, (byte)0x7C, (byte)0x70, (byte)0x33, (byte)0x82, (byte)0x45
            };
            Memory iidMem = new Memory(16);
            iidMem.write(0, iidBytes, 0, 16);

            byte[] factoryIidBytes = new byte[] {
                (byte)0x35, (byte)0x00, (byte)0x00, (byte)0x00,
                (byte)0x00, (byte)0x00,
                (byte)0x00, (byte)0x00,
                (byte)0xC0, (byte)0x00,
                (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x46
            };
            Memory factoryIidMem = new Memory(16);
            factoryIidMem.write(0, factoryIidBytes, 0, 16);

            try {
                while (isRunning) {
                    try {
                        pollMediaData(iidMem, factoryIidMem);
                        Thread.sleep(1000);
                    } catch (Throwable t) {
                        LOGGER.error("SMTC Poll Error: ", t);
                        releaseStatics();
                        try { Thread.sleep(5000); } catch (InterruptedException ignored) {}
                    }
                }
            } finally {
                releaseStatics();
            }
        });
    }

    public static void stopListening() {
        isRunning = false;
    }

    private static boolean activated = false;

    private static void pollMediaData(Memory iidMem, Memory factoryIidMem) {
        Pointer pStatics = getStaticsPointer(iidMem, factoryIidMem);
        if (pStatics == null) return;

        Pointer pOp = null;
        Pointer pManager = null;
        Pointer pSess = null;
        Pointer pOpProps = null;
        Pointer pProps = null;
        Pointer titleHString = null;
        Pointer artistHString = null;
        Pointer albumArtistHString = null;
        Pointer subtitleHString = null;

        try {
            Pointer vtable = pStatics.getPointer(0);
            Function reqAsync = Function.getFunction(vtable.getPointer(VTABLE_OFFSET_REQUEST_ASYNC * Native.POINTER_SIZE), Function.ALT_CONVENTION);
            PointerByReference opRef = new PointerByReference();
            int hr = reqAsync.invokeInt(new Object[] { pStatics, opRef });

            if (hr != 0 || opRef.getValue() == null) return;
            pOp = opRef.getValue();

            PointerByReference managerRef = waitForResults(pOp);
            if (managerRef.getValue() == null) return;
            pManager = managerRef.getValue();

            Pointer vManager = pManager.getPointer(0);
            Function getSess = Function.getFunction(vManager.getPointer(VTABLE_OFFSET_GET_CURRENT_SESSION * Native.POINTER_SIZE), Function.ALT_CONVENTION);
            PointerByReference sessRef = new PointerByReference();
            hr = getSess.invokeInt(new Object[] { pManager, sessRef });

            if (hr != 0 || sessRef.getValue() == null) return;
            pSess = sessRef.getValue();

            Pointer vSess = pSess.getPointer(0);
            Function tryGetProps = Function.getFunction(vSess.getPointer(VTABLE_OFFSET_TRY_GET_MEDIA_PROPERTIES * Native.POINTER_SIZE), Function.ALT_CONVENTION);
            PointerByReference opPropsRef = new PointerByReference();
            hr = tryGetProps.invokeInt(new Object[] { pSess, opPropsRef });

            if (hr != 0 || opPropsRef.getValue() == null) return;
            pOpProps = opPropsRef.getValue();

            PointerByReference propsRef = waitForResults(pOpProps);
            if (propsRef.getValue() == null) return;
            pProps = propsRef.getValue();

            Pointer vProps = pProps.getPointer(0);
            titleHString = getHString(vProps, pProps, VTABLE_OFFSET_GET_TITLE);
            artistHString = getHString(vProps, pProps, VTABLE_OFFSET_GET_ARTIST);
            albumArtistHString = getHString(vProps, pProps, VTABLE_OFFSET_GET_ALBUM_ARTIST);
            subtitleHString = getHString(vProps, pProps, VTABLE_OFFSET_GET_SUBTITLE);

            String newTitle = readHString(titleHString);
            String newArtist = firstNonBlank(
                    readHString(artistHString),
                    readHString(albumArtistHString),
                    readHString(subtitleHString));

            if (newTitle == null) newTitle = "Bilinmiyor";
            if (newArtist == null || newArtist.isEmpty()) newArtist = "Bilinmeyen Sanatçı";

            if (!newTitle.equals(currentTitle) || !newArtist.equals(currentArtist)) {
                LOGGER.info("SMTC Media: {} - {}", newTitle, newArtist);
                currentTitle = newTitle;
                currentArtist = newArtist;
                triggerToast(currentTitle, currentArtist);
                lastChangeTime = System.currentTimeMillis();
            }
        } finally {
            deleteHString(titleHString);
            deleteHString(artistHString);
            deleteHString(albumArtistHString);
            deleteHString(subtitleHString);
            releaseCom(pProps);
            releaseCom(pOpProps);
            releaseCom(pSess);
            releaseCom(pManager);
            releaseCom(pOp);
        }
    }

    private static Pointer getStaticsPointer(Memory iidMem, Memory factoryIidMem) {
        if (staticsPointer != null) {
            return staticsPointer;
        }

        char[] className = "Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager".toCharArray();
        PointerByReference hclassName = new PointerByReference();
        if (Combase.INSTANCE.WindowsCreateString(className, className.length, hclassName) != 0) return null;

        PointerByReference staticsRef = new PointerByReference();
        int hr = Combase.INSTANCE.RoGetActivationFactory(hclassName.getValue(), iidMem, staticsRef);

        if (hr == 0x80004002) {
            hr = Combase.INSTANCE.RoGetActivationFactory(hclassName.getValue(), factoryIidMem, staticsRef);
        }

        Combase.INSTANCE.WindowsDeleteString(hclassName.getValue());

        if (hr != 0 || staticsRef.getValue() == null) {
            if (System.currentTimeMillis() - lastChangeTime > 60000) {
                LOGGER.info("SMTC Activation Failed (HR: {}). No music apps?", Integer.toHexString(hr));
                lastChangeTime = System.currentTimeMillis();
            }
            return null;
        }

        if (!activated) {
            LOGGER.info("SMTC Activated successfully.");
            activated = true;
        }

        staticsPointer = staticsRef.getValue();
        return staticsPointer;
    }

    private static PointerByReference waitForResults(Pointer operation) {
        Pointer vtable = operation.getPointer(0);
        Function getResults = Function.getFunction(vtable.getPointer(VTABLE_OFFSET_GET_RESULTS * Native.POINTER_SIZE), Function.ALT_CONVENTION);
        PointerByReference resultRef = new PointerByReference();

        for (int attempt = 0; attempt < ASYNC_WAIT_ATTEMPTS; attempt++) {
            int hr = getResults.invokeInt(new Object[] { operation, resultRef });
            if (hr == 0 && resultRef.getValue() != null) {
                return resultRef;
            }
            try { Thread.sleep(ASYNC_WAIT_MILLIS); } catch (InterruptedException ignored) {}
        }

        return resultRef;
    }

    private static Pointer getHString(Pointer vtable, Pointer instance, int offset) {
        Function getter = Function.getFunction(vtable.getPointer(offset * Native.POINTER_SIZE), Function.ALT_CONVENTION);
        PointerByReference ref = new PointerByReference();
        getter.invokeInt(new Object[] { instance, ref });
        return ref.getValue();
    }

    private static void deleteHString(Pointer hstring) {
        if (hstring != null) {
            Combase.INSTANCE.WindowsDeleteString(hstring);
        }
    }

    private static void releaseStatics() {
        Pointer pointer = staticsPointer;
        staticsPointer = null;
        activated = false;
        releaseCom(pointer);
    }

    private static void releaseCom(Pointer pointer) {
        if (pointer == null) return;
        Pointer vtable = pointer.getPointer(0);
        Function release = Function.getFunction(vtable.getPointer(VTABLE_OFFSET_RELEASE * Native.POINTER_SIZE), Function.ALT_CONVENTION);
        release.invokeInt(new Object[] { pointer });
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private static String readHString(Pointer hstring) {
        if (hstring == null) return null;
        IntByReference lenRef = new IntByReference();
        Pointer rawBuffer = Combase.INSTANCE.WindowsGetStringRawBuffer(hstring, lenRef);
        if (rawBuffer != null && lenRef.getValue() > 0) {
            return new String(rawBuffer.getCharArray(0, lenRef.getValue()));
        }
        return "";
    }

    private static void triggerToast(String title, String artist) {
        CompletableFuture.runAsync(() -> {
            try {
                MediaToast.show(title, artist);
            } catch (Throwable t) {
                LOGGER.error("Toast failed: ", t);
            }
        });
    }
}
