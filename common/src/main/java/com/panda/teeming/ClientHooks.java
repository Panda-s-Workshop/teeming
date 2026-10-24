package com.panda.teeming;

/** Client actions that common code triggers. The client init fills them in; on a dedicated server they do nothing. */
public final class ClientHooks {
    /** Opens the Field Guide screen. */
    public static Runnable openFieldGuide = () -> {};

    private ClientHooks() {}
}
