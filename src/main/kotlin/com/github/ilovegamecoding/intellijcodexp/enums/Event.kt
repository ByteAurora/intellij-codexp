package com.github.ilovegamecoding.intellijcodexp.enums

/**
 * Event enum for the plugin events.
 */
enum class Event(val xpValue: Long) {
    NONE(0),
    TYPING(2),
    CUT(1),
    COPY(1),
    PASTE(1),
    BACKSPACE(1),
    TAB(2),
    ENTER(2),
    SAVE(10),
    BUILD(5),
    RUN(10),
    DEBUG(20),
    ACTION(5);
}