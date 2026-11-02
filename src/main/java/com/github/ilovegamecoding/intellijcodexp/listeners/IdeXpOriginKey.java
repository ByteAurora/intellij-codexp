package com.github.ilovegamecoding.intellijcodexp.listeners;

import com.intellij.openapi.actionSystem.DataKey;

/** Java calls the stable static ABI instead of the companion introduced in newer IDEs. */
final class IdeXpOriginKey {
    static final DataKey<IdeXpOrigin> KEY = DataKey.create("CodeXP.xpOrigin");

    private IdeXpOriginKey() {}
}
