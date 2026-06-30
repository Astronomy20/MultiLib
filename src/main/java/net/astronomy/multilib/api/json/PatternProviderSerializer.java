package net.astronomy.multilib.api.json;

import com.mojang.serialization.Codec;
import net.astronomy.multilib.api.pattern.PatternProvider;

public interface PatternProviderSerializer {
    String getType();
    Codec<? extends PatternProvider> codec();
}
